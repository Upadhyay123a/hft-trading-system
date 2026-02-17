package com.hft.core;

import com.hft.exchange.BinanceConnector;
import com.hft.monitoring.PerformanceMonitor;
import com.hft.orderbook.OrderBook;
import com.hft.risk.RiskManager;
import com.hft.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-Performance Multi-threaded Trading Engine
 * Optimized for ultra-high throughput with parallel processing
 */
public class HighThroughputEngine {
    private static final Logger logger = LoggerFactory.getLogger(HighThroughputEngine.class);
    
    // Core components
    private final BinanceConnector connector;
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final PerformanceMonitor performanceMonitor;
    
    // Thread pools
    private final ExecutorService tickProcessingPool;
    private final ExecutorService orderExecutionPool;
    private final ScheduledExecutorService monitoringPool;
    
    // Data structures for concurrent processing
    private final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final BlockingQueue<Tick> tickQueue = new LinkedBlockingQueue<>(10000);
    private final BlockingQueue<OrderRequest> orderQueue = new LinkedBlockingQueue<>(5000);
    
    // Performance tracking
    private final AtomicLong ticksProcessed = new AtomicLong(0);
    private final AtomicLong tradesExecuted = new AtomicLong(0);
    private final AtomicLong ordersSubmitted = new AtomicLong(0);
    private final AtomicLong ordersRejected = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    
    // Configuration
    private final int numTickProcessors;
    private final int numOrderExecutors;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public HighThroughputEngine(BinanceConnector connector, Strategy strategy, 
                              RiskManager riskManager, EngineConfig config) {
        this.connector = connector;
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.performanceMonitor = PerformanceMonitor.getInstance();
        this.numTickProcessors = config.numTickProcessors;
        this.numOrderExecutors = config.numOrderExecutors;
        this.batchSize = config.batchSize;
        
        // Initialize thread pools
        this.tickProcessingPool = Executors.newFixedThreadPool(numTickProcessors, 
            new ThreadFactory() {
                private final AtomicLong threadNumber = new AtomicLong(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "TickProcessor-" + threadNumber.getAndIncrement());
                    t.setDaemon(false);
                    t.setPriority(Thread.NORM_PRIORITY + 1); // Slightly higher priority
                    return t;
                }
            });
        
        this.orderExecutionPool = Executors.newFixedThreadPool(numOrderExecutors,
            new ThreadFactory() {
                private final AtomicLong threadNumber = new AtomicLong(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "OrderExecutor-" + threadNumber.getAndIncrement());
                    t.setDaemon(false);
                    t.setPriority(Thread.NORM_PRIORITY + 2); // Higher priority for orders
                    return t;
                }
            });
        
        this.monitoringPool = Executors.newScheduledThreadPool(2);
        
        logger.info("High-Throughput Engine initialized:");
        logger.info("Tick processors: {}, Order executors: {}, Batch size: {}", 
            numTickProcessors, numOrderExecutors, batchSize);
    }
    
    /**
     * Start the high-throughput engine
     */
    public void start() {
        logger.info("Starting High-Throughput Engine with strategy: {}", strategy.getName());
        
        running.set(true);
        startTime.set(System.currentTimeMillis());
        strategy.initialize();
        
        // Start tick collection thread
        Thread tickCollector = new Thread(this::tickCollectionLoop, "TickCollector");
        tickCollector.start();
        
        // Start tick processing threads
        for (int i = 0; i < numTickProcessors; i++) {
            tickProcessingPool.submit(new TickProcessor(i));
        }
        
        // Start order execution threads
        for (int i = 0; i < numOrderExecutors; i++) {
            orderExecutionPool.submit(new OrderExecutor(i));
        }
        
        // Start monitoring
        monitoringPool.scheduleAtFixedRate(this::printStatistics, 5, 5, TimeUnit.SECONDS);
        monitoringPool.scheduleAtFixedRate(this::performanceReport, 30, 30, TimeUnit.SECONDS);
        
        logger.info("High-Throughput Engine started successfully");
    }
    
    /**
     * Tick collection loop - feeds the processing pipeline
     */
    private void tickCollectionLoop() {
        while (running.get()) {
            try {
                Tick tick = connector.getNextTick();
                if (tick != null) {
                    if (!tickQueue.offer(tick, 1, TimeUnit.MILLISECONDS)) {
                        // Queue full - drop tick and record
                        performanceMonitor.incrementCounter("ticks_dropped");
                        logger.warn("Tick queue full, dropping tick for symbol {}", tick.symbolId);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error collecting tick", e);
            }
        }
    }
    
    /**
     * Tick processor - processes ticks in parallel
     */
    private class TickProcessor implements Runnable {
        private final int processorId;
        private final List<Tick> batch = new ArrayList<>(batchSize);
        
        public TickProcessor(int processorId) {
            this.processorId = processorId;
        }
        
        @Override
        public void run() {
            while (running.get()) {
                try {
                    // Collect batch of ticks
                    batch.clear();
                    Tick firstTick = tickQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (firstTick != null) {
                        batch.add(firstTick);
                        
                        // Collect more ticks for batch processing
                        tickQueue.drainTo(batch, batchSize - 1);
                        
                        // Process batch
                        processTickBatch(batch);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in tick processor {}", processorId, e);
                }
            }
        }
        
        /**
         * Process a batch of ticks for better cache locality
         */
        private void processTickBatch(List<Tick> ticks) {
            try (PerformanceMonitor.LatencyMeasurement measurement = 
                 performanceMonitor.startMeasurement("tick_batch_processing")) {
                
                for (Tick tick : ticks) {
                    processSingleTick(tick);
                }
                
                performanceMonitor.recordThroughput("tick_batches", 1);
                performanceMonitor.recordThroughput("ticks_processed", ticks.size());
            }
        }
        
        /**
         * Process individual tick
         */
        private void processSingleTick(Tick tick) {
            ticksProcessed.incrementAndGet();
            
            // Get or create order book
            OrderBook orderBook = orderBooks.computeIfAbsent(
                tick.symbolId, 
                id -> new OrderBook(id)
            );
            
            // Generate orders
            List<Order> orders = strategy.onTick(tick, orderBook);
            
            // Submit orders for execution
            for (Order order : orders) {
                OrderRequest request = new OrderRequest(order, orderBook, System.nanoTime());
                if (!orderQueue.offer(request)) {
                    performanceMonitor.incrementCounter("orders_dropped");
                    logger.warn("Order queue full, dropping order");
                } else {
                    ordersSubmitted.incrementAndGet();
                }
            }
            
            performanceMonitor.recordMetric("orders_generated_per_tick", orders.size());
        }
    }
    
    /**
     * Order executor - handles order execution with risk checks
     */
    private class OrderExecutor implements Runnable {
        private final int executorId;
        
        public OrderExecutor(int executorId) {
            this.executorId = executorId;
        }
        
        @Override
        public void run() {
            while (running.get()) {
                try {
                    OrderRequest request = orderQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (request != null) {
                        executeOrder(request);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in order executor {}", executorId, e);
                }
            }
        }
        
        /**
         * Execute order with risk management
         */
        private void executeOrder(OrderRequest request) {
            try (PerformanceMonitor.LatencyMeasurement measurement = 
                 performanceMonitor.startMeasurement("order_execution")) {
                
                // Risk validation
                RiskManager.RiskCheckResult riskResult = riskManager.validateOrder(request.order);
                if (!riskResult.approved) {
                    ordersRejected.incrementAndGet();
                    performanceMonitor.incrementCounter("orders_rejected_risk");
                    return;
                }
                
                // Execute order
                List<Trade> trades = request.orderBook.addOrder(request.order);
                
                // Process trades
                for (Trade trade : trades) {
                    tradesExecuted.incrementAndGet();
                    strategy.onTrade(trade);
                    riskManager.onTrade(trade);
                    performanceMonitor.recordThroughput("trades", 1);
                }
                
                // Record order execution latency
                long executionLatency = System.nanoTime() - request.submitTimeNanos;
                performanceMonitor.recordLatency("order_execution_latency", executionLatency);
            }
        }
    }
    
    /**
     * Print engine statistics
     */
    private void printStatistics() {
        long uptime = (System.currentTimeMillis() - startTime.get()) / 1000;
        long ticks = ticksProcessed.get();
        long trades = tradesExecuted.get();
        long orders = ordersSubmitted.get();
        long rejected = ordersRejected.get();
        
        double ticksPerSecond = uptime > 0 ? (double)ticks / uptime : 0;
        double tradesPerSecond = uptime > 0 ? (double)trades / uptime : 0;
        double orderAcceptanceRate = orders > 0 ? (double)(orders - rejected) / orders * 100 : 0;
        
        logger.info("=== High-Throughput Engine Statistics ===");
        logger.info("Uptime: {}s", uptime);
        logger.info("Ticks processed: {} ({} tps)", ticks, String.format("%.0f", ticksPerSecond));
        logger.info("Orders submitted: {}", orders);
        logger.info("Orders rejected: {} ({}% acceptance)", rejected, 
            String.format("%.1f", orderAcceptanceRate));
        logger.info("Trades executed: {} ({} tps)", trades, String.format("%.2f", tradesPerSecond));
        logger.info("Strategy P&L: ${}", String.format("%.2f", strategy.getPnL()));
        logger.info("Queue sizes - Ticks: {}, Orders: {}", tickQueue.size(), orderQueue.size());
        logger.info("========================================");
    }
    
    /**
     * Detailed performance report
     */
    private void performanceReport() {
        logger.info("=== Performance Report ===");
        performanceMonitor.printReport();
        logger.info("========================");
    }
    
    /**
     * Stop the engine
     */
    public void stop() {
        logger.info("Stopping High-Throughput Engine");
        running.set(false);
        
        // Shutdown thread pools
        tickProcessingPool.shutdown();
        orderExecutionPool.shutdown();
        monitoringPool.shutdown();
        
        try {
            if (!tickProcessingPool.awaitTermination(5, TimeUnit.SECONDS)) {
                tickProcessingPool.shutdownNow();
            }
            if (!orderExecutionPool.awaitTermination(5, TimeUnit.SECONDS)) {
                orderExecutionPool.shutdownNow();
            }
            if (!monitoringPool.awaitTermination(2, TimeUnit.SECONDS)) {
                monitoringPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tickProcessingPool.shutdownNow();
            orderExecutionPool.shutdownNow();
            monitoringPool.shutdownNow();
        }
        
        connector.disconnect();
        printStatistics();
        
        logger.info("High-Throughput Engine stopped");
    }
    
    // Getters
    public long getTicksProcessed() { return ticksProcessed.get(); }
    public long getTradesExecuted() { return tradesExecuted.get(); }
    public long getOrdersSubmitted() { return ordersSubmitted.get(); }
    public long getOrdersRejected() { return ordersRejected.get(); }
    
    // Configuration class
    public static class EngineConfig {
        final int numTickProcessors;
        final int numOrderExecutors;
        final int batchSize;
        
        public EngineConfig(int numTickProcessors, int numOrderExecutors, int batchSize) {
            this.numTickProcessors = numTickProcessors;
            this.numOrderExecutors = numOrderExecutors;
            this.batchSize = batchSize;
        }
        
        public static EngineConfig balanced() {
            return new EngineConfig(
                Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                50
            );
        }
        
        public static EngineConfig highThroughput() {
            return new EngineConfig(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors(),
                100
            );
        }
        
        public static EngineConfig lowLatency() {
            return new EngineConfig(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                Math.max(2, Runtime.getRuntime().availableProcessors() / 4),
                10
            );
        }
    }
    
    // Supporting classes
    private static class OrderRequest {
        final Order order;
        final OrderBook orderBook;
        final long submitTimeNanos;
        
        OrderRequest(Order order, OrderBook orderBook, long submitTimeNanos) {
            this.order = order;
            this.orderBook = orderBook;
            this.submitTimeNanos = submitTimeNanos;
        }
    }
}
