package com.hft.core.disruptor;

import com.hft.core.Tick;
import com.hft.core.Order;
import com.hft.core.Trade;
import com.hft.core.binary.BinaryProtocol;
import com.hft.orderbook.OrderBook;
import com.ft.risk.RiskManager;
import com.hft.strategy.Strategy;
import com.hft.monitoring.PerformanceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
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
    
    // Core components
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final PerformanceMonitor performanceMonitor;
    
    // Disruptor infrastructure
    private final Disruptor<byte[]> disruptor;
    private final RingBuffer<byte[]> ringBuffer;
    
    // Data structures
    private final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ByteBuffer decodeBuffer = ByteBuffer.allocate(256);
    
    // Performance tracking
    private final AtomicLong ticksProcessed = new AtomicLong(0);
    private final AtomicLong tradesExecuted = new AtomicLong(0);
    private final AtomicLong ordersProcessed = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Event handlers
    private final TickEventHandler[] tickHandlers;
    private final OrderEventHandler[] orderHandlers;
    
    public DisruptorEngine(Strategy strategy, RiskManager riskManager) {
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.performanceMonitor = PerformanceMonitor.getInstance();
        
        // Initialize thread factory
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicLong threadNumber = new AtomicLong(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Disruptor-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                t.setPriority(Thread.MAX_PRIORITY); // MAX PRIORITY for ultra-low latency
                return t;
            }
        };
        
        // Create disruptor
        this.disruptor = new Disruptor<byte[]>(
            () -> new byte[256], // EventFactory that creates byte arrays
            BUFFER_SIZE,
            threadFactory,
            ProducerType.MULTI, // Allow multiple producers
            new BusySpinWaitStrategy() // ULTRA-LOW LATENCY - CPU intensive but fastest
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
     */
    private void setupEventProcessors() {
        // Create event processor group for tick handlers
        EventHandler<byte[]>[] tickEventHandlers = new EventHandler[NUM_PROCESSORS];
        for (int i = 0; i < NUM_PROCESSORS; i++) {
            tickEventHandlers[i] = tickHandlers[i];
        }
        
        // Create event processor group for order handlers  
        EventHandler<byte[]>[] orderEventHandlers = new EventHandler[NUM_PROCESSORS];
        for (int i = 0; i < NUM_PROCESSORS; i++) {
            orderEventHandlers[i] = orderHandlers[i];
        }
        
        // Set up worker pool for parallel processing
        WorkHandler<byte[]>[] workHandlers = new WorkHandler[NUM_PROCESSORS];
        for (int i = 0; i < NUM_PROCESSORS; i++) {
            workHandlers[i] = new MarketDataWorker(i);
        }
        
        // Configure event processing pipeline
        disruptor.handleEventsWith(tickEventHandlers)
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
                    id -> new OrderBook(id)
                );
                
                // Convert to Tick object for strategy
                Tick tick = new Tick(tickData.timestamp, tickData.symbolId, 
                                   tickData.price, tickData.volume, tickData.side);
                
                // Generate orders from strategy
                List<Order> orders = strategy.onTick(tick, orderBook);
                
                // Publish orders back to disruptor
                for (Order order : orders) {
                    publishOrder(order);
                }
                
                performanceMonitor.recordThroughput("ticks_processed_disruptor", 1);
            }
        }
    }
    
    /**
     * Order event handler - processes orders with risk management
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
                
                // Risk validation
                RiskManager.RiskCheckResult riskResult = riskManager.validateOrder(order);
                if (!riskResult.approved) {
                    performanceMonitor.incrementCounter("orders_rejected_risk_disruptor");
                    return;
                }
                
                // Execute order
                OrderBook orderBook = orderBooks.get(orderData.symbolId);
                if (orderBook != null) {
                    List<Trade> trades = orderBook.addOrder(order);
                    
                    // Process trades
                    for (Trade trade : trades) {
                        tradesExecuted.incrementAndGet();
                        strategy.onTrade(trade);
                        riskManager.onTrade(trade);
                        performanceMonitor.recordThroughput("trades_disruptor", 1);
                    }
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
        disruptor.shutdown();
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
