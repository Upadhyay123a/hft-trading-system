package com.hft.exchange.api;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.monitoring.PerformanceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-Exchange Manager for HFT Trading
 * Manages connections to multiple exchanges with smart routing and failover
 */
public class MultiExchangeManager {
    private static final Logger logger = LoggerFactory.getLogger(MultiExchangeManager.class);
    
    // Exchange instances
    private final Map<String, ExchangeApi> exchanges = new ConcurrentHashMap<>();
    private final Map<String, ExchangeStatus> exchangeStatuses = new ConcurrentHashMap<>();
    
    // Routing and load balancing
    private final Map<Integer, List<String>> symbolToExchanges = new ConcurrentHashMap<>();
    private final AtomicLong roundRobinCounter = new AtomicLong(0);
    
    // Performance monitoring
    private final PerformanceMonitor performanceMonitor;
    
    // Configuration
    private final boolean enableSmartRouting;
    private final boolean enableFailover;
    private final int maxRetries;
    
    public MultiExchangeManager(boolean enableSmartRouting, boolean enableFailover, int maxRetries) {
        this.performanceMonitor = PerformanceMonitor.getInstance();
        this.enableSmartRouting = enableSmartRouting;
        this.enableFailover = enableFailover;
        this.maxRetries = maxRetries;
        
        initializeExchanges();
        setupSymbolRouting();
        
        logger.info("Multi-Exchange Manager initialized with smart routing: {}, failover: {}", 
            enableSmartRouting, enableFailover);
    }
    
    /**
     * Initialize exchange APIs
     */
    private void initializeExchanges() {
        ApiKeyManager apiKeyManager = ApiKeyManager.getInstance();
        
        // Initialize Binance if configured
        if (apiKeyManager.isExchangeConfigured("binance")) {
            BinanceRealApi binance = new BinanceRealApi();
            exchanges.put("binance", binance);
            exchangeStatuses.put("binance", new ExchangeStatus("binance", true));
            logger.info("Initialized Binance API");
        }
        
        // Initialize Coinbase if configured
        if (apiKeyManager.isExchangeConfigured("coinbase")) {
            CoinbaseRealApi coinbase = new CoinbaseRealApi();
            exchanges.put("coinbase", coinbase);
            exchangeStatuses.put("coinbase", new ExchangeStatus("coinbase", true));
            logger.info("Initialized Coinbase API");
        }
        
        if (exchanges.isEmpty()) {
            logger.warn("No exchanges configured - please set up API keys");
        }
    }
    
    /**
     * Setup symbol to exchange routing
     */
    private void setupSymbolRouting() {
        // Configure which symbols are available on which exchanges
        symbolToExchanges.put(1, Arrays.asList("binance", "coinbase")); // BTC/USDT
        symbolToExchanges.put(2, Arrays.asList("binance", "coinbase")); // ETH/USDT
        // Add more symbol mappings as needed
    }
    
    /**
     * Connect to all configured exchanges
     */
    public CompletableFuture<Void> connectAll(List<String> symbols) {
        List<CompletableFuture<Void>> connections = new ArrayList<>();
        
        for (Map.Entry<String, ExchangeApi> entry : exchanges.entrySet()) {
            String exchangeName = entry.getKey();
            ExchangeApi exchange = entry.getValue();
            
            CompletableFuture<Void> connection = exchange.connectMarketData(symbols)
                .thenRun(() -> {
                    exchangeStatuses.get(exchangeName).connected = true;
                    logger.info("Connected to {} market data", exchangeName);
                })
                .exceptionally(throwable -> {
                    exchangeStatuses.get(exchangeName).connected = false;
                    logger.error("Failed to connect to {} market data", exchangeName, throwable);
                    return null;
                });
            
            connections.add(connection);
        }
        
        return CompletableFuture.allOf(connections.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Place order with smart routing
     */
    public CompletableFuture<OrderResult> placeOrder(Order order) {
        try (var measurement = performanceMonitor.startMeasurement("multi_exchange_order")) {
            
            List<String> availableExchanges = getAvailableExchanges(order.symbolId);
            
            if (availableExchanges.isEmpty()) {
                return CompletableFuture.failedFuture(
                    new RuntimeException("No available exchanges for symbol " + order.symbolId));
            }
            
            if (enableSmartRouting) {
                return placeOrderWithSmartRouting(order, availableExchanges);
            } else {
                return placeOrderWithRoundRobin(order, availableExchanges);
            }
        }
    }
    
    /**
     * Place order with smart routing (best price, lowest latency)
     */
    private CompletableFuture<OrderResult> placeOrderWithSmartRouting(Order order, List<String> availableExchanges) {
        // For now, use round-robin, but this could be enhanced with:
        // - Price comparison across exchanges
        // - Latency measurements
        // - Liquidity analysis
        // - Fee optimization
        
        return placeOrderWithRoundRobin(order, availableExchanges);
    }
    
    /**
     * Place order with round-robin load balancing
     */
    private CompletableFuture<OrderResult> placeOrderWithRoundRobin(Order order, List<String> availableExchanges) {
        int index = (int) (roundRobinCounter.getAndIncrement() % availableExchanges.size());
        String selectedExchange = availableExchanges.get(index);
        
        return placeOrderOnExchange(order, selectedExchange, 0);
    }
    
    /**
     * Place order on specific exchange with retry logic
     */
    private CompletableFuture<OrderResult> placeOrderOnExchange(Order order, String exchangeName, int attempt) {
        ExchangeApi exchange = exchanges.get(exchangeName);
        
        if (exchange == null || !exchangeStatuses.get(exchangeName).connected) {
            if (enableFailover && attempt < maxRetries) {
                logger.warn("Exchange {} unavailable, attempting failover (attempt {})", exchangeName, attempt + 1);
                return placeOrderOnFailoverExchange(order, exchangeName, attempt);
            } else {
                return CompletableFuture.failedFuture(
                    new RuntimeException("Exchange " + exchangeName + " unavailable"));
            }
        }
        
        return exchange.placeOrder(order)
            .thenApply(result -> {
                performanceMonitor.recordMetric("order_success_" + exchangeName, 1);
                logger.info("Order placed on {}: {}", exchangeName, result.status);
                return result;
            })
            .exceptionally(throwable -> {
                performanceMonitor.incrementCounter("order_failed_" + exchangeName);
                logger.error("Order placement failed on {}", exchangeName, throwable);
                
                if (enableFailover && attempt < maxRetries) {
                    return placeOrderOnFailoverExchange(order, exchangeName, attempt);
                } else {
                    throw new RuntimeException("Order placement failed after " + (attempt + 1) + " attempts", throwable);
                }
            });
    }
    
    /**
     * Try placing order on alternative exchange (failover)
     */
    private CompletableFuture<OrderResult> placeOrderOnFailoverExchange(Order order, String failedExchange, int attempt) {
        List<String> availableExchanges = getAvailableExchanges(order.symbolId);
        availableExchanges.remove(failedExchange);
        
        if (availableExchanges.isEmpty()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("No failover exchanges available"));
        }
        
        String failoverExchange = availableExchanges.get(0);
        logger.info("Failing over from {} to {} for order {}", failedExchange, failoverExchange, order.orderId);
        
        return placeOrderOnExchange(order, failoverExchange, attempt + 1);
    }
    
    /**
     * Cancel order on all exchanges
     */
    public CompletableFuture<Boolean> cancelOrder(long orderId, String symbol) {
        List<CompletableFuture<Boolean>> cancellations = new ArrayList<>();
        
        for (Map.Entry<String, ExchangeApi> entry : exchanges.entrySet()) {
            String exchangeName = entry.getKey();
            ExchangeApi exchange = entry.getValue();
            
            CompletableFuture<Boolean> cancellation = exchange.cancelOrder(orderId, symbol)
                .thenApply(success -> {
                    if (success) {
                        logger.info("Order {} cancelled on {}", orderId, exchangeName);
                        performanceMonitor.recordMetric("order_cancel_success_" + exchangeName, 1);
                    } else {
                        logger.warn("Order {} cancellation failed on {}", orderId, exchangeName);
                        performanceMonitor.incrementCounter("order_cancel_failed_" + exchangeName);
                    }
                    return success;
                })
                .exceptionally(throwable -> {
                    logger.error("Order {} cancellation error on {}", orderId, exchangeName, throwable);
                    performanceMonitor.incrementCounter("order_cancel_error_" + exchangeName);
                    return false;
                });
            
            cancellations.add(cancellation);
        }
        
        return CompletableFuture.allOf(cancellations.toArray(new CompletableFuture[0]))
            .thenApply(v -> cancellations.stream().anyMatch(future -> future.join()));
    }
    
    /**
     * Get consolidated account information
     */
    public CompletableFuture<Map<String, Object>> getConsolidatedAccountInfo() {
        Map<String, CompletableFuture<Object>> accountInfoFutures = new HashMap<>();
        
        for (Map.Entry<String, ExchangeApi> entry : exchanges.entrySet()) {
            String exchangeName = entry.getKey();
            ExchangeApi exchange = entry.getValue();
            
            CompletableFuture<Object> accountInfo = exchange.getAccountInfo()
                .thenApply(info -> {
                    performanceMonitor.recordMetric("account_info_success_" + exchangeName, 1);
                    return info;
                })
                .exceptionally(throwable -> {
                    performanceMonitor.incrementCounter("account_info_failed_" + exchangeName);
                    return "Error: " + throwable.getMessage();
                });
            
            accountInfoFutures.put(exchangeName, accountInfo);
        }
        
        return CompletableFuture.allOf(accountInfoFutures.values().toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Object> result = new HashMap<>();
                accountInfoFutures.forEach((exchange, future) -> {
                    try {
                        result.put(exchange, future.get());
                    } catch (Exception e) {
                        result.put(exchange, "Error retrieving info");
                    }
                });
                return result;
            });
    }
    
    /**
     * Get available exchanges for a symbol
     */
    private List<String> getAvailableExchanges(int symbolId) {
        List<String> allExchanges = symbolToExchanges.getOrDefault(symbolId, new ArrayList<>());
        List<String> availableExchanges = new ArrayList<>();
        
        for (String exchange : allExchanges) {
            ExchangeStatus status = exchangeStatuses.get(exchange);
            if (status != null && status.connected) {
                availableExchanges.add(exchange);
            }
        }
        
        return availableExchanges;
    }
    
    /**
     * Get exchange status summary
     */
    public Map<String, ExchangeStatus> getExchangeStatuses() {
        return new HashMap<>(exchangeStatuses);
    }
    
    /**
     * Get performance metrics for all exchanges
     */
    public Map<String, Map<String, Double>> getExchangeMetrics() {
        Map<String, Map<String, Double>> metrics = new HashMap<>();
        
        for (String exchange : exchanges.keySet()) {
            Map<String, Double> exchangeMetrics = new HashMap<>();
            
            // Add exchange-specific metrics
            exchangeMetrics.put(1.0, 1.0); // Placeholder for actual metrics
            
            metrics.put(exchange, exchangeMetrics);
        }
        
        return metrics;
    }
    
    /**
     * Disconnect from all exchanges
     */
    public void disconnect() {
        exchanges.values().forEach(ExchangeApi::disconnect);
        exchangeStatuses.values().forEach(status -> status.connected = false);
        logger.info("Disconnected from all exchanges");
    }
    
    /**
     * Health check for all exchanges
     */
    public CompletableFuture<Map<String, Boolean>> healthCheck() {
        Map<String, CompletableFuture<Boolean>> healthChecks = new HashMap<>();
        
        for (Map.Entry<String, ExchangeApi> entry : exchanges.entrySet()) {
            String exchangeName = entry.getKey();
            ExchangeApi exchange = entry.getValue();
            
            CompletableFuture<Boolean> healthCheck = exchange.getAccountInfo()
                .thenApply(info -> {
                    boolean healthy = !"ERROR".equals(info.status);
                    exchangeStatuses.get(exchangeName).healthy = healthy;
                    return healthy;
                })
                .exceptionally(throwable -> {
                    exchangeStatuses.get(exchangeName).healthy = false;
                    return false;
                });
            
            healthChecks.put(exchangeName, healthCheck);
        }
        
        return CompletableFuture.allOf(healthChecks.values().toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Boolean> result = new HashMap<>();
                healthChecks.forEach((exchange, future) -> {
                    try {
                        result.put(exchange, future.get());
                    } catch (Exception e) {
                        result.put(exchange, false);
                    }
                });
                return result;
            });
    }
    
    /**
     * Exchange status tracking
     */
    public static class ExchangeStatus {
        public final String name;
        public volatile boolean connected;
        public volatile boolean healthy;
        public volatile long lastUpdateTime;
        public volatile double latency;
        
        public ExchangeStatus(String name, boolean connected) {
            this.name = name;
            this.connected = connected;
            this.healthy = true;
            this.lastUpdateTime = System.currentTimeMillis();
            this.latency = 0.0;
        }
        
        public void updateLatency(double latency) {
            this.latency = latency;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Exchange API interface
     */
    public interface ExchangeApi {
        CompletableFuture<Void> connectMarketData(List<String> symbols);
        CompletableFuture<OrderResult> placeOrder(Order order);
        CompletableFuture<Boolean> cancelOrder(long orderId, String symbol);
        CompletableFuture<Object> getAccountInfo();
        void disconnect();
    }
    
    // Data classes (shared across exchanges)
    public static class OrderResult {
        public final String orderId;
        public final String status;
        public final String clientOrderId;
        public final double executedQty;
        public final double executedValue;
        public final double price;
        public final String exchange;
        
        public OrderResult(String orderId, String status, String clientOrderId, 
                         double executedQty, double executedValue, double price) {
            this.orderId = orderId;
            this.status = status;
            this.clientOrderId = clientOrderId;
            this.executedQty = executedQty;
            this.executedValue = executedValue;
            this.price = price;
            this.exchange = "unknown";
        }
        
        public OrderResult(String orderId, String status, String clientOrderId, 
                         double executedQty, double executedValue, double price, String exchange) {
            this.orderId = orderId;
            this.status = status;
            this.clientOrderId = clientOrderId;
            this.executedQty = executedQty;
            this.executedValue = executedValue;
            this.price = price;
            this.exchange = exchange;
        }
    }
}
