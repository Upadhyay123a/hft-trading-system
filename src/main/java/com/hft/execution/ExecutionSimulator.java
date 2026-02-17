package com.hft.execution;

import com.hft.core.Order;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Realistic Order Execution Simulator for HFT
 * Simulates slippage, latency, market impact, and partial fills
 */
public class ExecutionSimulator {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionSimulator.class);
    
    // Simulation parameters
    private final double baseLatencyMs;           // Base network latency
    private final double latencyVariance;         // Latency variance
    private final double slippageFactor;          // Market slippage factor
    private final double marketImpactFactor;      // Large order market impact
    private final double partialFillProbability;  // Probability of partial fills
    private final int maxPartialFills;            // Maximum number of partial fills
    
    // Execution state
    private final Map<Long, PendingOrder> pendingOrders = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final AtomicLong tradeIdGenerator = new AtomicLong(1);
    private final Random random = new Random();
    
    // Market data for realistic simulation
    private volatile double marketVolatility = 0.01; // 1% default volatility
    private volatile double marketSpread = 0.0001;   // 0.01% default spread
    
    public ExecutionSimulator(ExecutionConfig config) {
        this.baseLatencyMs = config.baseLatencyMs;
        this.latencyVariance = config.latencyVariance;
        this.slippageFactor = config.slippageFactor;
        this.marketImpactFactor = config.marketImpactFactor;
        this.partialFillProbability = config.partialFillProbability;
        this.maxPartialFills = config.maxPartialFills;
        
        logger.info("Execution Simulator initialized:");
        logger.info("Base latency: {}ms ±{}ms, Slippage: {}, Impact: {}", 
            baseLatencyMs, latencyVariance, slippageFactor, marketImpactFactor);
    }
    
    /**
     * Submit order for execution simulation
     */
    public CompletableFuture<List<Trade>> submitOrder(Order order, OrderBook orderBook, 
                                                   TradeCallback callback) {
        CompletableFuture<List<Trade>> future = new CompletableFuture<>();
        
        // Calculate execution latency
        double latency = calculateLatency();
        
        // Schedule execution after latency
        executor.schedule(() -> {
            try {
                List<Trade> trades = executeOrder(order, orderBook, callback);
                future.complete(trades);
            } catch (Exception e) {
                logger.error("Order execution failed", e);
                future.completeExceptionally(e);
            }
        }, (long)latency, TimeUnit.MILLISECONDS);
        
        return future;
    }
    
    /**
     * Execute order with realistic market simulation
     */
    private List<Trade> executeOrder(Order order, OrderBook orderBook, TradeCallback callback) {
        List<Trade> trades = new ArrayList<>();
        
        // Calculate market impact and slippage
        MarketImpact impact = calculateMarketImpact(order, orderBook);
        
        // Determine execution strategy
        if (order.type == 1) { // Market order
            trades = executeMarketOrder(order, orderBook, impact, callback);
        } else if (order.type == 0) { // Limit order
            trades = executeLimitOrder(order, orderBook, impact, callback);
        }
        
        return trades;
    }
    
    /**
     * Execute market order with immediate simulation
     */
    private List<Trade> executeMarketOrder(Order order, OrderBook orderBook, 
                                          MarketImpact impact, TradeCallback callback) {
        List<Trade> trades = new ArrayList<>();
        
        // Calculate execution price with slippage
        long executionPrice = calculateExecutionPrice(order, orderBook, impact);
        
        // Determine if partial fill occurs
        int totalQuantity = order.quantity;
        int remainingQuantity = totalQuantity;
        
        if (shouldPartialFill(order)) {
            // Simulate partial fills
            int numFills = Math.min(random.nextInt(maxPartialFills) + 1, 
                                  (int)Math.ceil((double)totalQuantity / 100));
            int avgFillSize = totalQuantity / numFills;
            
            for (int i = 0; i < numFills && remainingQuantity > 0; i++) {
                int fillQuantity = Math.min(avgFillSize + random.nextInt(50) - 25, 
                                         remainingQuantity);
                
                // Add small price variation for each fill
                long fillPrice = applyPriceVariation(executionPrice);
                
                Trade trade = createTrade(order, fillQuantity, fillPrice);
                trades.add(trade);
                
                // Callback for each fill
                if (callback != null) {
                    callback.onTrade(trade);
                }
                
                remainingQuantity -= fillQuantity;
                
                // Small delay between partial fills
                if (i < numFills - 1 && remainingQuantity > 0) {
                    try {
                        Thread.sleep(random.nextInt(10) + 1); // 1-10ms between fills
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } else {
            // Single fill
            Trade trade = createTrade(order, totalQuantity, executionPrice);
            trades.add(trade);
            
            if (callback != null) {
                callback.onTrade(trade);
            }
        }
        
        logger.debug("Market order executed: {} qty @ {}, {} fills, slippage: {}", 
            order.quantity, executionPrice / 10000.0, trades.size(), 
            impact.slippageBps);
        
        return trades;
    }
    
    /**
     * Execute limit order with book simulation
     */
    private List<Trade> executeLimitOrder(Order order, OrderBook orderBook, 
                                        MarketImpact impact, TradeCallback callback) {
        List<Trade> trades = new ArrayList<>();
        
        // Check if order can be filled immediately
        boolean canFill = canFillImmediately(order, orderBook);
        
        if (canFill) {
            // Immediate fill at limit price or better
            long executionPrice = calculateLimitExecutionPrice(order, orderBook);
            Trade trade = createTrade(order, order.quantity, executionPrice);
            trades.add(trade);
            
            if (callback != null) {
                callback.onTrade(trade);
            }
        } else {
            // Order rests in book - simulate fill after some time
            scheduleLimitOrderFill(order, orderBook, callback);
        }
        
        return trades;
    }
    
    /**
     * Calculate execution latency with variance
     */
    private double calculateLatency() {
        // Normal distribution around base latency
        double gaussian = random.nextGaussian();
        return Math.max(1, baseLatencyMs + gaussian * latencyVariance);
    }
    
    /**
     * Calculate market impact for large orders
     */
    private MarketImpact calculateMarketImpact(Order order, OrderBook orderBook) {
        double orderValue = order.quantity * order.getPriceAsDouble();
        double avgMarketValue = getAverageMarketValue(orderBook);
        
        // Market impact increases with order size relative to market
        double impactRatio = orderValue / (avgMarketValue + 1);
        double marketImpact = Math.min(impactRatio * marketImpactFactor, 0.01); // Cap at 1%
        
        // Slippage based on volatility and order size
        double slippage = (marketVolatility * slippageFactor + marketImpact) * 10000; // Convert to basis points
        
        return new MarketImpact(marketImpact, slippage);
    }
    
    /**
     * Calculate execution price with slippage
     */
    private long calculateExecutionPrice(Order order, OrderBook orderBook, MarketImpact impact) {
        long basePrice;
        
        if (order.isBuy()) {
            basePrice = orderBook.getBestAsk();
            // Buy orders experience positive slippage (worse price)
            basePrice += (long)(impact.slippageBps * basePrice / 10000);
        } else {
            basePrice = orderBook.getBestBid();
            // Sell orders experience negative slippage (worse price)
            basePrice -= (long)(impact.slippageBps * basePrice / 10000);
        }
        
        return basePrice;
    }
    
    /**
     * Calculate limit order execution price
     */
    private long calculateLimitExecutionPrice(Order order, OrderBook orderBook) {
        if (order.isBuy()) {
            // Buy orders can fill at or below their limit price
            long bestAsk = orderBook.getBestAsk();
            return Math.min(order.price, bestAsk);
        } else {
            // Sell orders can fill at or above their limit price
            long bestBid = orderBook.getBestBid();
            return Math.max(order.price, bestBid);
        }
    }
    
    /**
     * Check if limit order can fill immediately
     */
    private boolean canFillImmediately(Order order, OrderBook orderBook) {
        if (order.isBuy()) {
            return order.price >= orderBook.getBestAsk();
        } else {
            return order.price <= orderBook.getBestBid();
        }
    }
    
    /**
     * Schedule limit order fill simulation
     */
    private void scheduleLimitOrderFill(Order order, OrderBook orderBook, TradeCallback callback) {
        // Simulate order resting in book and potentially filling later
        double fillProbability = 0.1 + marketVolatility * 5; // Higher volatility = higher fill chance
        
        if (random.nextDouble() < fillProbability) {
            // Schedule fill after random delay
            long delayMs = (long)(random.nextExponential() * 5000); // Exponential distribution, avg 5s
            
            executor.schedule(() -> {
                if (canFillImmediately(order, orderBook)) {
                    long executionPrice = calculateLimitExecutionPrice(order, orderBook);
                    Trade trade = createTrade(order, order.quantity, executionPrice);
                    
                    if (callback != null) {
                        callback.onTrade(trade);
                    }
                    
                    logger.debug("Limit order filled after {}ms delay", delayMs);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Determine if order should have partial fills
     */
    private boolean shouldPartialFill(Order order) {
        // Larger orders more likely to have partial fills
        double sizeFactor = Math.min(order.quantity / 1000.0, 1.0);
        return random.nextDouble() < (partialFillProbability * sizeFactor);
    }
    
    /**
     * Apply small price variation to simulate market movement
     */
    private long applyPriceVariation(long basePrice) {
        double variation = (random.nextGaussian() * marketVolatility * 0.5) * basePrice;
        return basePrice + (long)variation;
    }
    
    /**
     * Create trade object
     */
    private Trade createTrade(Order order, int quantity, long price) {
        return new Trade(
            tradeIdGenerator.getAndIncrement(),
            order.isBuy() ? order.orderId : -1,
            order.isSell() ? order.orderId : -1,
            order.symbolId,
            price,
            quantity
        );
    }
    
    /**
     * Get average market value from order book
     */
    private double getAverageMarketValue(OrderBook orderBook) {
        // Simplified calculation - in practice would use full book depth
        long midPrice = orderBook.getMidPrice();
        return midPrice * 100; // Assume average size of 100 units
    }
    
    /**
     * Update market conditions
     */
    public void updateMarketConditions(double volatility, double spread) {
        this.marketVolatility = volatility;
        this.marketSpread = spread;
    }
    
    /**
     * Shutdown simulator
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Configuration class
    public static class ExecutionConfig {
        final double baseLatencyMs;
        final double latencyVariance;
        final double slippageFactor;
        final double marketImpactFactor;
        final double partialFillProbability;
        final int maxPartialFills;
        
        public ExecutionConfig(double baseLatencyMs, double latencyVariance, double slippageFactor,
                             double marketImpactFactor, double partialFillProbability, int maxPartialFills) {
            this.baseLatencyMs = baseLatencyMs;
            this.latencyVariance = latencyVariance;
            this.slippageFactor = slippageFactor;
            this.marketImpactFactor = marketImpactFactor;
            this.partialFillProbability = partialFillProbability;
            this.maxPartialFills = maxPartialFills;
        }
        
        public static ExecutionConfig realistic() {
            return new ExecutionConfig(5.0, 2.0, 0.2, 0.001, 0.3, 5);
        }
        
        public static ExecutionConfig fast() {
            return new ExecutionConfig(1.0, 0.5, 0.1, 0.0005, 0.1, 3);
        }
        
        public static ExecutionConfig slow() {
            return new ExecutionConfig(20.0, 10.0, 0.5, 0.002, 0.5, 8);
        }
    }
    
    // Supporting classes
    public static class MarketImpact {
        public final double impactPercent;
        public final double slippageBps;
        
        public MarketImpact(double impactPercent, double slippageBps) {
            this.impactPercent = impactPercent;
            this.slippageBps = slippageBps;
        }
    }
    
    public interface TradeCallback {
        void onTrade(Trade trade);
    }
    
    private static class PendingOrder {
        final Order order;
        final long submitTime;
        final TradeCallback callback;
        
        PendingOrder(Order order, TradeCallback callback) {
            this.order = order;
            this.submitTime = System.nanoTime();
            this.callback = callback;
        }
    }
}
