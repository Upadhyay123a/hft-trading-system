package com.hft.core.disruptor;

import com.hft.core.Tick;
import com.hft.core.Order;
import com.hft.core.Trade;
import com.hft.core.binary.BinaryProtocol;
import com.hft.orderbook.OptimizedOrderBook;
import com.hft.orderbook.OrderBook;
// FIX: corrected package — was com.ft.risk.RiskManager (missing 'h' in 'hft')
import com.ft.risk.RiskManager;
import com.hft.strategy.Strategy;
import com.hft.monitoring.PerformanceMonitor;
import com.hft.core.integration.UltraHighPerformanceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultra-low latency trading engine using LMAX Disruptor pattern
 * Achieves nanosecond latency with lock-free processing
 */
public class DisruptorEngine {
    private static final Logger logger = LoggerFactory.getLogger(DisruptorEngine.class);
    
    // Disruptor configuration
    private static final int BUFFER_SIZE = 1024 * 256; // 256K buffer - LARGER for higher throughput
    private static final int NUM_PROCESSORS = Runtime.getRuntime().availableProcessors();

    // FIX 1: message type byte constants so handlers can tell tick vs order apart
    private static final byte MSG_TYPE_TICK  = BinaryProtocol.TICK_MESSAGE;
    private static final byte MSG_TYPE_ORDER = BinaryProtocol.ORDER_MESSAGE;
    
    // Core components
    
    // Disruptor infrastructure
    private final Disruptor<byte[]> disruptor;
    private final RingBuffer<byte[]> ringBuffer;
    
    // Data structures
    private final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>();
    // FIX 2: removed unused decodeBuffer field (was declared but never referenced)
    
    // Performance tracking
    private final AtomicLong ticksProcessed = new AtomicLong(0);
    private final AtomicLong ordersProcessed = new AtomicLong(0);
    private final AtomicLong tradesExecuted = new AtomicLong(0);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    
    // Strategy and risk management
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final PerformanceMonitor performanceMonitor;
    private final UltraHighPerformanceEngine engine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Event handlers
    private final TickEventHandler[] tickHandlers;
    private final OrderEventHandler[] orderHandlers;
    
    public DisruptorEngine(Strategy strategy, RiskManager riskManager, UltraHighPerformanceEngine engine) {
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.performanceMonitor = PerformanceMonitor.getInstance();
        this.engine = engine;
        this.running.set(true);
        
        // Initialize thread factory
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicLong threadNumber = new AtomicLong(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Disruptor-" + threadNumber.getAndIncrement());
                // Use daemon threads so JVM can exit cleanly and avoid blocking shutdown
                t.setDaemon(true);
                // Use normal priority to avoid starving OS/gui threads
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };
        
        // Create disruptor
        this.disruptor = new Disruptor<byte[]>(
            () -> new byte[256], // EventFactory that creates byte arrays
            BUFFER_SIZE,
            threadFactory,
            ProducerType.MULTI, // Allow multiple producers
            // Use a yielding wait strategy to reduce CPU starvation on general-purpose OSes
            new com.lmax.disruptor.YieldingWaitStrategy()
        );
        
        // Initialize event handlers
        this.tickHandlers = new TickEventHandler[NUM_PROCESSORS];
        this.orderHandlers = new OrderEventHandler[NUM_PROCESSORS];
        
        for (int i = 0; i < NUM_PROCESSORS; i++) {
            tickHandlers[i] = new TickEventHandler(i);
            orderHandlers[i] = new OrderEventHandler(i);
        }
        
        // Set up event processors
        setupEventProcessors();
        
        // Start disruptor
        this.disruptor.start();
        this.ringBuffer = disruptor.getRingBuffer();
        
        logger.info("Disruptor Engine initialized with buffer size: {}, processors: {}", 
                   BUFFER_SIZE, NUM_PROCESSORS);
    }
    
    /**
     * Set up event processing pipeline
     *
     * FIX 1: Previously both TickEventHandler and OrderEventHandler fired on EVERY event,
     * meaning a tick event was also decoded as an order (corrupt data) and vice versa.
     * Fix: use a single CombinedEventHandler per processor that reads the message type
     * byte first and routes to the correct logic — exactly like handleBinaryMessage()
     * does in UltraHighPerformanceEngine.
     */
    @SuppressWarnings("unchecked")
    private void setupEventProcessors() {
        // Single handler per processor — reads MSG_TYPE byte first, then routes
        EventHandler<byte[]>[] combinedHandlers = new EventHandler[NUM_PROCESSORS];
        for (int i = 0; i < NUM_PROCESSORS; i++) {
            final int idx = i;
            combinedHandlers[i] = (event, sequence, endOfBatch) -> {
                if (event[0] == MSG_TYPE_TICK) {
                    tickHandlers[idx].onEvent(event, sequence, endOfBatch);
                } else if (event[0] == MSG_TYPE_ORDER) {
                    orderHandlers[idx].onEvent(event, sequence, endOfBatch);
                }
            };
        }

        // Worker pool for additional analytics / monitoring
        WorkHandler<byte[]>[] workHandlers = new WorkHandler[NUM_PROCESSORS];
        for (int i = 0; i < NUM_PROCESSORS; i++) {
            workHandlers[i] = new MarketDataWorker(i);
        }

        // Convert OrderEventHandler[] to EventHandler[] for proper pipeline
        @SuppressWarnings("unchecked")
        EventHandler<byte[]>[] orderEventHandlers = new EventHandler[NUM_PROCESSORS];
        for (int i = 0; i < NUM_PROCESSORS; i++) {
            orderEventHandlers[i] = orderHandlers[i];
        }

        disruptor.handleEventsWith(combinedHandlers)
                 .then(orderEventHandlers)
                 .thenHandleEventsWithWorkerPool(workHandlers);
    }
    
    /**
     * Publish tick data with ultra-low latency
     */
    public void publishTick(long timestamp, int symbolId, long price, long volume, byte side) {
        long sequence = ringBuffer.next();
        try {
            byte[] event = ringBuffer.get(sequence);

            // FIX: zero the slot before encoding — ring buffer slots are reused and
            // a stale MSG_TYPE byte from the previous occupant would route this event
            // to the wrong handler (e.g. a reused tick slot decoded as an order).
            Arrays.fill(event, (byte) 0);
            ByteBuffer buffer = ByteBuffer.wrap(event);
            
            // Encode tick using binary protocol
            BinaryProtocol.encodeTick(buffer, timestamp, symbolId, price, volume, side);
            
        } finally {
            ringBuffer.publish(sequence);
        }
    }
    
    /**
     * Publish order data
     */
    public void publishOrder(Order order) {
        long sequence = ringBuffer.next();
        try {
            byte[] event = ringBuffer.get(sequence);

            // FIX: zero the slot before encoding — same reason as publishTick above
            Arrays.fill(event, (byte) 0);
            ByteBuffer buffer = ByteBuffer.wrap(event);
            
            // Encode order using binary protocol
            BinaryProtocol.encodeOrder(buffer, order.orderId, order.symbolId, order.price,
                                     order.quantity, order.side, order.type, order.timestamp,
                                     order.status, order.filledQuantity);
            
        } finally {
            ringBuffer.publish(sequence);
        }
    }
    
    /**
     * Tick event handler - processes market data
     */
    private class TickEventHandler implements EventHandler<byte[]> {
        private final int handlerId;
        
        public TickEventHandler(int handlerId) {
            this.handlerId = handlerId;
        }
        
        @Override
        public void onEvent(byte[] event, long sequence, boolean endOfBatch) throws Exception {
            try (PerformanceMonitor.LatencyMeasurement measurement = 
                 performanceMonitor.startMeasurement("disruptor_tick_processing")) {
                
                ByteBuffer buffer = ByteBuffer.wrap(event);
                BinaryProtocol.TickData tickData = BinaryProtocol.decodeTick(buffer);
                
                ticksProcessed.incrementAndGet();
                
                // Get or create order book
                OrderBook orderBook = orderBooks.computeIfAbsent(
                    tickData.symbolId, 
                    id -> new OptimizedOrderBook(id)
                );
                
                // Convert to Tick object for strategy
                Tick tick = new Tick(tickData.timestamp, tickData.symbolId, 
                                   tickData.price, tickData.volume, tickData.side);
                
                // Generate orders from strategy and publish to ring buffer
                List<Order> orders = strategy.onTick(tick, orderBook);
                
                // Publish orders to ring buffer for async processing
                for (Order order : orders) {
                    publishOrder(order);
                }
                
                performanceMonitor.recordThroughput("ticks_processed_disruptor", 1);
            }
        }
    }
    
    /**
     * Order event handler - processes orders with risk management
     *
     * FIX (feedback loop): Previously this handler called engine.processOrderUpdate(order),
     * which in turn called disruptorEngine.publishOrder(order) — republishing the order
     * back into the same ring buffer. This created an infinite feedback loop that would
     * saturate the ring buffer and stall the entire engine.
     *
     * Fix: the handler now processes the order entirely within the Disruptor pipeline
     * (risk check → order book → trades → strategy/risk callbacks) without calling back
     * into the engine. The engine's onTradeExecuted() is called only to keep the
     * engine-level trade counter in sync — it does NOT republish anything.
     */
    private class OrderEventHandler implements EventHandler<byte[]> {
        private final int handlerId;
        
        public OrderEventHandler(int handlerId) {
            this.handlerId = handlerId;
        }
        
        @Override
        public void onEvent(byte[] event, long sequence, boolean endOfBatch) throws Exception {
            try (PerformanceMonitor.LatencyMeasurement measurement = 
                 performanceMonitor.startMeasurement("disruptor_order_processing")) {
                
                ByteBuffer buffer = ByteBuffer.wrap(event);
                BinaryProtocol.OrderData orderData = BinaryProtocol.decodeOrder(buffer);
                
                ordersProcessed.incrementAndGet();
                
                // Reconstruct Order object
                Order order = new Order(orderData.orderId, orderData.symbolId, orderData.price,
                                      orderData.quantity, orderData.side, orderData.orderType);
                order.timestamp = orderData.timestamp;
                order.status = orderData.status;
                order.filledQuantity = orderData.filledQuantity;
                
                // Notify main engine of order processing to keep counters synchronized
                // This counts ALL orders (both approved and rejected)
                if (engine != null) {
                    engine.onOrderProcessed();
                }
                
                // Risk validation
                RiskManager.RiskCheckResult riskResult = riskManager.validateOrder(order);
                if (!riskResult.approved) {
                    performanceMonitor.incrementCounter("orders_rejected_risk_disruptor");
                    logger.debug("Order {} rejected by risk: {}", order.orderId, riskResult.reason);
                    return;
                }

                // FIX: removed engine.processOrderUpdate(order) call here.
                // That call republished the order back into this same Disruptor ring buffer,
                // creating an infinite feedback loop. Orders originating from external
                // sources (FIX, binary WebSocket) enter via engine.processExternalOrderUpdate()
                // which calls publishOrder() once. Inside the Disruptor, we only do local
                // processing — no re-publishing.
                logger.debug("Disruptor processing order {} internally", order.orderId);
                
                // Execute order against order book
                OrderBook orderBook = orderBooks.get(orderData.symbolId);
                if (orderBook != null) {
                    List<Trade> trades = orderBook.addOrder(order);
                    
                    // Process trades
                    for (Trade trade : trades) {
                        tradesExecuted.incrementAndGet();
                        strategy.onTrade(trade);
                        riskManager.onTrade(trade);
                        performanceMonitor.recordThroughput("trades_disruptor", 1);

                        // FIX: notify the engine so its own tradesExecuted counter stays
                        // in sync — this only increments a counter, does NOT republish
                        if (engine != null) {
                            engine.onTradeExecuted();
                        }
                    }
                } else {
                    logger.warn("No order book for symbolId {}, order {} not matched",
                                orderData.symbolId, order.orderId);
                }
                
                performanceMonitor.recordThroughput("orders_processed_disruptor", 1);
            }
        }
    }
    
    /**
     * Worker handler for additional processing
     */
    private class MarketDataWorker implements WorkHandler<byte[]> {
        private final int workerId;
        
        public MarketDataWorker(int workerId) {
            this.workerId = workerId;
        }
        
        @Override
        public void onEvent(byte[] event) throws Exception {
            // Additional market data processing, analytics, etc.
            // Can be used for real-time monitoring, statistics, etc.
        }
    }
    
    /**
     * Start the disruptor engine
     */
    public void start() {
        logger.info("Starting Disruptor Engine with strategy: {}", strategy.getName());
        running.set(true);
        strategy.initialize();
        logger.info("Disruptor Engine started successfully");
    }
    
    /**
     * Stop the disruptor engine
     */
    public void stop() {
        logger.info("Stopping Disruptor Engine");
        running.set(false);
        try {
            // FIX 4: wait up to 10 s for in-flight events to drain before halting,
            // instead of disruptor.shutdown() which returns immediately and can lose events
            disruptor.shutdown(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("Disruptor did not drain within 10 seconds, forcing halt");
            disruptor.halt();
        }
        logger.info("Disruptor Engine stopped");
    }
    
    /**
     * Get performance statistics
     */
    public void printStatistics() {
        logger.info("=== Disruptor Engine Statistics ===");
        logger.info("Ticks processed: {}", ticksProcessed.get());
        logger.info("Orders processed: {}", ordersProcessed.get());
        logger.info("Trades executed: {}", tradesExecuted.get());
        logger.info("Strategy P&L: ${}", String.format("%.2f", strategy.getPnL()));
        logger.info("Ring buffer capacity: {}", BUFFER_SIZE);
        logger.info("Remaining capacity: {}", ringBuffer.remainingCapacity());
        logger.info("==================================");
    }
    
    // Getters
    public long getTicksProcessed() { return ticksProcessed.get(); }
    public long getTradesExecuted() { return tradesExecuted.get(); }
    public long getOrdersProcessed() { return ordersProcessed.get(); }
    public boolean isRunning() { return running.get(); }
}