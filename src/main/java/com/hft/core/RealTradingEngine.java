package com.hft.core;

import com.hft.exchange.api.MultiExchangeManager;
import com.hft.exchange.api.ApiKeyManager;
import com.hft.exchange.api.BinanceRealApi;
import com.hft.exchange.api.CoinbaseRealApi;
import com.hft.monitoring.PerformanceMonitor;
import com.hft.orderbook.OrderBook;
import com.ft.risk.RiskManager;
import com.hft.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real Trading Engine with Exchange API Integration
 * Executes actual trades on multiple exchanges with smart routing
 */
public class RealTradingEngine {
    private static final Logger logger = LoggerFactory.getLogger(RealTradingEngine.class);
    
    // Core components
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final PerformanceMonitor performanceMonitor;
    private final MultiExchangeManager exchangeManager;
    
    // Trading state
    private final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<Long, OrderStatus> activeOrders = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Performance tracking
    private final AtomicLong ticksProcessed = new AtomicLong(0);
    private final AtomicLong ordersPlaced = new AtomicLong(0);
    private final AtomicLong ordersFilled = new AtomicLong(0);
    private final AtomicLong totalPnL = new AtomicLong(0);
    private final long startTime;
    
    // Configuration
    private final boolean enableSmartRouting;
    private final boolean enableFailover;
    private final List<String> symbols;
    
    public RealTradingEngine(Strategy strategy, RiskManager riskManager, 
                           EngineConfig config, List<String> symbols) {
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.performanceMonitor = PerformanceMonitor.getInstance();
        this.symbols = new ArrayList<>(symbols);
        this.enableSmartRouting = config.enableSmartRouting;
        this.enableFailover = config.enableFailover;
        this.startTime = System.currentTimeMillis();
        
        // Initialize exchange manager
        this.exchangeManager = new MultiExchangeManager(
            config.enableSmartRouting, config.enableFailover, config.maxRetries);
        
        logger.info("Real Trading Engine initialized with {} symbols", symbols.size());
    }
    
    /**
     * Start the real trading engine
     */
    public CompletableFuture<Void> start() {
        logger.info("Starting Real Trading Engine with strategy: {}", strategy.getName());
        
        running.set(true);
        strategy.initialize();
        
        // Check if exchanges are configured
        ApiKeyManager apiKeyManager = ApiKeyManager.getInstance();
        String[] configuredExchanges = apiKeyManager.getConfiguredExchanges();
        
        if (configuredExchanges.length == 0) {
            logger.error("No exchanges configured - please set up API keys in exchange-api-keys.properties");
            return CompletableFuture.failedFuture(
                new RuntimeException("No exchanges configured"));
        }
        
        logger.info("Configured exchanges: {}", Arrays.toString(configuredExchanges));
        
        // Connect to exchanges
        return exchangeManager.connectAll(symbols)
            .thenRun(() -> {
                logger.info("Connected to all exchanges successfully");
                startTradingLoop();
                startMonitoringLoop();
            })
            .exceptionally(throwable -> {
                logger.error("Failed to connect to exchanges", throwable);
                running.set(false);
                return null;
            });
    }
    
    /**
     * Main trading loop
     */
    private void startTradingLoop() {
        Thread tradingThread = new Thread(() -> {
            while (running.get()) {
                try {
                    // Simulate tick processing (in real system, this would come from exchange websockets)
                    processTick();
                    
                    Thread.sleep(100); // Process every 100ms for HFT
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in trading loop", e);
                }
            }
        }, "RealTradingEngine-Main");
        
        tradingThread.start();
    }
    
    /**
     * Process tick and generate orders
     */
    private void processTick() {
        try (var measurement = performanceMonitor.startMeasurement("real_tick_processing")) {
            
            ticksProcessed.incrementAndGet();
            
            // Get order book (simplified - would use real market data)
            OrderBook orderBook = orderBooks.computeIfAbsent(1, id -> new OrderBook(id));
            
            // Generate trading signals from strategy
            List<Order> orders = strategy.onTick(null, orderBook); // Simplified tick
            
            // Process each order
            for (Order order : orders) {
                processOrder(order);
            }
            
            performanceMonitor.recordThroughput("ticks_processed", 1);
            performanceMonitor.recordMetric("orders_generated_per_tick", orders.size());
        }
    }
    
    /**
     * Process individual order with risk management and execution
     */
    private void processOrder(Order order) {
        try (var measurement = performanceMonitor.startMeasurement("real_order_processing")) {
            
            // Risk validation
            RiskManager.RiskCheckResult riskResult = riskManager.validateOrder(order);
            if (!riskResult.approved) {
                logger.warn("Order rejected by risk manager: {}", riskResult.reason);
                performanceMonitor.incrementCounter("orders_rejected_risk");
                return;
            }
            
            // Place order on exchange
            placeOrderOnExchange(order);
        }
    }
    
    /**
     * Place order on exchange with tracking
     */
    private void placeOrderOnExchange(Order order) {
        exchangeManager.placeOrder(order)
            .thenAccept(result -> {
                ordersPlaced.incrementAndGet();
                
                // Track order status
                OrderStatus status = new OrderStatus(order.orderId, result);
                activeOrders.put(order.orderId, status);
                
                logger.info("Order placed: {} - Status: {}", order.orderId, result.status);
                
                // Update risk manager with order placement
                riskManager.onTrade(null); // Simplified
                
                // Check if order was filled immediately
                if ("FILLED".equals(result.status)) {
                    handleOrderFilled(order, result);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Order placement failed: {}", order.orderId, throwable);
                performanceMonitor.incrementCounter("order_placement_failed");
                return null;
            });
    }
    
    /**
     * Handle order fill
     */
    private void handleOrderFilled(Order order, MultiExchangeManager.OrderResult result) {
        ordersFilled.incrementAndGet();
        
        // Calculate P&L (simplified)
        double pnl = calculatePnL(order, result);
        totalPnL.addAndGet((long)(pnl * 10000)); // Convert to ticks
        
        // Update strategy
        strategy.onTrade(null); // Simplified trade object
        
        // Update risk manager
        riskManager.onTrade(null); // Simplified
        
        logger.info("Order filled: {} - P&L: ${}", order.orderId, String.format("%.2f", pnl));
        
        performanceMonitor.recordThroughput("orders_filled", 1);
        performanceMonitor.recordMetric("realized_pnl", pnl);
    }
    
    /**
     * Calculate P&L for filled order
     */
    private double calculatePnL(Order order, MultiExchangeManager.OrderResult result) {
        // Simplified P&L calculation
        // In practice, this would consider position, fees, etc.
        return result.executedValue * 0.001; // Simplified
    }
    
    /**
     * Start monitoring and statistics loop
     */
    private void startMonitoringLoop() {
        Thread monitoringThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(5000); // Every 5 seconds
                    printStatistics();
                    checkExchangeHealth();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in monitoring loop", e);
                }
            }
        }, "RealTradingEngine-Monitor");
        
        monitoringThread.start();
    }
    
    /**
     * Check exchange health
     */
    private void checkExchangeHealth() {
        exchangeManager.healthCheck()
            .thenAccept(healthResults -> {
                healthResults.forEach((exchange, healthy) -> {
                    if (!healthy) {
                        logger.warn("Exchange {} is unhealthy", exchange);
                    }
                });
            })
            .exceptionally(throwable -> {
                logger.error("Health check failed", throwable);
                return null;
            });
    }
    
    /**
     * Print trading statistics
     */
    private void printStatistics() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        double ticksPerSecond = uptime > 0 ? (double)ticksProcessed.get() / uptime : 0;
        
        // Get exchange statuses
        Map<String, MultiExchangeManager.ExchangeStatus> exchangeStatuses = exchangeManager.getExchangeStatuses();
        
        logger.info("=== Real Trading Engine Statistics ===");
        logger.info("Uptime: {}s", uptime);
        logger.info("Ticks processed: {} ({} tps)", ticksProcessed.get(), String.format("%.2f", ticksPerSecond));
        logger.info("Orders placed: {}", ordersPlaced.get());
        logger.info("Orders filled: {}", ordersFilled.get());
        logger.info("Total P&L: ${}", String.format("%.2f", totalPnL.get() / 10000.0));
        logger.info("Strategy P&L: ${}", String.format("%.2f", strategy.getPnL()));
        
        logger.info("--- Exchange Status ---");
        exchangeStatuses.forEach((exchange, status) -> {
            logger.info("{}: Connected={}, Healthy={}, Latency={}ms", 
                exchange, status.connected, status.healthy, String.format("%.2f", status.latency));
        });
        
        logger.info("=====================================");
    }
    
    /**
     * Get consolidated account information
     */
    public CompletableFuture<Map<String, Object>> getAccountInfo() {
        return exchangeManager.getConsolidatedAccountInfo();
    }
    
    /**
     * Cancel all active orders
     */
    public CompletableFuture<Void> cancelAllOrders() {
        List<CompletableFuture<Boolean>> cancellations = new ArrayList<>();
        
        for (Long orderId : activeOrders.keySet()) {
            cancellations.add(exchangeManager.cancelOrder(orderId, "BTCUSDT"));
        }
        
        return CompletableFuture.allOf(cancellations.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                activeOrders.clear();
                logger.info("All orders cancelled");
            });
    }
    
    /**
     * Emergency stop
     */
    public void emergencyStop() {
        logger.warn("EMERGENCY STOP ACTIVATED");
        running.set(false);
        
        // Cancel all orders
        cancelAllOrders().thenRun(() -> {
            // Disconnect from exchanges
            exchangeManager.disconnect();
            logger.info("Emergency stop completed");
        });
    }
    
    /**
     * Stop the engine gracefully
     */
    public void stop() {
        logger.info("Stopping Real Trading Engine");
        running.set(false);
        
        // Cancel active orders
        cancelAllOrders().thenRun(() -> {
            // Disconnect from exchanges
            exchangeManager.disconnect();
            printStatistics();
            logger.info("Real Trading Engine stopped");
        });
    }
    
    // Getters
    public long getTicksProcessed() { return ticksProcessed.get(); }
    public long getOrdersPlaced() { return ordersPlaced.get(); }
    public long getOrdersFilled() { return ordersFilled.get(); }
    public double getTotalPnL() { return totalPnL.get() / 10000.0; }
    public boolean isRunning() { return running.get(); }
    
    // Configuration class
    public static class EngineConfig {
        final boolean enableSmartRouting;
        final boolean enableFailover;
        final int maxRetries;
        
        public EngineConfig(boolean enableSmartRouting, boolean enableFailover, int maxRetries) {
            this.enableSmartRouting = enableSmartRouting;
            this.enableFailover = enableFailover;
            this.maxRetries = maxRetries;
        }
        
        public static EngineConfig defaultConfig() {
            return new EngineConfig(true, true, 3);
        }
        
        public static EngineConfig highPerformance() {
            return new EngineConfig(true, true, 1);
        }
        
        public static EngineConfig conservative() {
            return new EngineConfig(false, true, 5);
        }
    }
    
    // Order status tracking
    private static class OrderStatus {
        public final long orderId;
        public volatile String status;
        public volatile long updateTime;
        public final MultiExchangeManager.OrderResult result;
        
        public OrderStatus(long orderId, MultiExchangeManager.OrderResult result) {
            this.orderId = orderId;
            this.status = result.status;
            this.updateTime = System.currentTimeMillis();
            this.result = result;
        }
        
        public void updateStatus(String newStatus) {
            this.status = newStatus;
            this.updateTime = System.currentTimeMillis();
        }
    }
}
