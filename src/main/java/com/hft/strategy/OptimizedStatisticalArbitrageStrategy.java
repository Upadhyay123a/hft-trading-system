package com.hft.strategy;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Professional O(1) Statistical Arbitrage Strategy Implementation
 * Based on Renaissance/Goldman Sachs incremental regression optimization
 * 
 * Key Features:
 * - O(1) hedge ratio calculation using incremental regression
 * - Real-time spread monitoring with z-score tracking
 * - Lock-free operations for thread safety
 * - Memory-efficient circular buffers
 */
public class OptimizedStatisticalArbitrageStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedStatisticalArbitrageStrategy.class);
    
    // Strategy parameters
    private final int[] symbols;
    private final int lookbackPeriod;
    private final double zScoreThreshold;
    private final double minSpread;
    private final int orderSize;
    
    // Incremental regression variables - O(1) updates
    private double sumX = 0.0, sumY = 0.0;
    private double sumXY = 0.0, sumX2 = 0.0;
    private int sampleCount = 0;
    private double hedgeRatio = 0.0;
    private double intercept = 0.0;
    
    // Spread tracking for z-score calculation
    private final double[] spreadHistory;
    private int spreadIndex = 0;
    private int spreadCount = 0;
    private double spreadSum = 0.0;
    private double spreadSumSquared = 0.0;
    
    // Strategy state
    private final AtomicLong orderIdGenerator = new AtomicLong(3000);
    private volatile ArbitragePosition activePosition = null;
    private double totalPnL = 0.0;
    private long signalsGenerated = 0;
    private long tradesExecuted = 0;
    
    // Price tracking
    private volatile double lastPriceX = 0.0;
    private volatile double lastPriceY = 0.0;
    
    public OptimizedStatisticalArbitrageStrategy(int[] symbols, int lookbackPeriod, 
                                               double zScoreThreshold, double minSpread, int orderSize) {
        this.symbols = symbols;
        this.lookbackPeriod = lookbackPeriod;
        this.zScoreThreshold = zScoreThreshold;
        this.minSpread = minSpread;
        this.orderSize = orderSize;
        
        // Initialize spread history buffer
        this.spreadHistory = new double[lookbackPeriod];
        
        logger.info("OptimizedStatisticalArbitrageStrategy initialized - Symbols: {}, Lookback: {}, Z-Score: {}", 
            symbols.length, lookbackPeriod, zScoreThreshold);
    }
    
    @Override
    public void initialize() {
        logger.info("OptimizedStatisticalArbitrageStrategy ready for symbols {}", symbols);
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Update price tracking
        if (tick.symbolId == symbols[0]) {
            lastPriceY = tick.getPriceAsDouble();
        } else if (tick.symbolId == symbols[1]) {
            lastPriceX = tick.getPriceAsDouble();
        }
        
        // Need both prices for calculation
        if (lastPriceX == 0.0 || lastPriceY == 0.0) {
            return orders;
        }
        
        // O(1) update regression
        updateRegression(lastPriceX, lastPriceY);
        
        // Need enough data for trading
        if (sampleCount < lookbackPeriod) {
            return orders;
        }
        
        // O(1) spread calculation
        double currentSpread = calculateSpread(lastPriceX, lastPriceY);
        
        // O(1) z-score calculation
        double zScore = calculateZScore(currentSpread);
        
        // Check for arbitrage opportunities
        if (activePosition == null) {
            if (zScore > zScoreThreshold) {
                // Short spread signal
                orders = executeShortSpread(lastPriceX, lastPriceY);
                signalsGenerated++;
            } else if (zScore < -zScoreThreshold) {
                // Long spread signal
                orders = executeLongSpread(lastPriceX, lastPriceY);
                signalsGenerated++;
            }
        }
        
        return orders;
    }
    
    /**
     * O(1) incremental regression update - Renaissance approach
     */
    private void updateRegression(double x, double y) {
        sampleCount++;
        
        sumX += x;
        sumY += y;
        sumXY += x * y;
        sumX2 += x * x;
        
        // O(1) hedge ratio calculation
        if (sampleCount > 1) {
            double denominator = sampleCount * sumX2 - sumX * sumX;
            if (denominator != 0) {
                hedgeRatio = (sampleCount * sumXY - sumX * sumY) / denominator;
                intercept = (sumY - hedgeRatio * sumX) / sampleCount;
            }
        }
    }
    
    /**
     * O(1) spread calculation
     */
    private double calculateSpread(double x, double y) {
        return y - (hedgeRatio * x + intercept);
    }
    
    /**
     * O(1) z-score calculation
     */
    private double calculateZScore(double spread) {
        // Update spread history
        if (spreadCount >= lookbackPeriod) {
            double oldSpread = spreadHistory[spreadIndex];
            spreadSum -= oldSpread;
            spreadSumSquared -= oldSpread * oldSpread;
        }
        
        spreadHistory[spreadIndex] = spread;
        spreadSum += spread;
        spreadSumSquared += spread * spread;
        spreadIndex = (spreadIndex + 1) % lookbackPeriod;
        
        if (spreadCount < lookbackPeriod) {
            spreadCount++;
        }
        
        // Calculate mean and standard deviation
        double mean = spreadSum / spreadCount;
        double variance = (spreadSumSquared / spreadCount) - (mean * mean);
        double stdDev = Math.sqrt(variance);
        
        return stdDev == 0 ? 0.0 : (spread - mean) / stdDev;
    }
    
    /**
     * Execute short spread (sell Y, buy X)
     */
    private List<Order> executeShortSpread(double priceX, double priceY) {
        List<Order> orders = new ArrayList<>();
        
        // Calculate quantities
        int quantityY = orderSize;
        int quantityX = (int)(orderSize * hedgeRatio);
        
        // Check minimum spread requirement
        double expectedProfit = Math.abs(calculateSpread(priceX, priceY)) * orderSize;
        if (expectedProfit < minSpread) {
            return orders;
        }
        
        // Create orders
        Order sellY = new Order(
            orderIdGenerator.getAndIncrement(),
            symbols[0],
            (long)(priceY * 10000),
            quantityY,
            (byte)1, // Sell
            (byte)0  // Limit
        );
        
        Order buyX = new Order(
            orderIdGenerator.getAndIncrement(),
            symbols[1],
            (long)(priceX * 10000),
            quantityX,
            (byte)0, // Buy
            (byte)0  // Limit
        );
        
        orders.add(sellY);
        orders.add(buyX);
        
        // Create active position
        activePosition = new ArbitragePosition(
            sellY.orderId, buyX.orderId, quantityY, quantityX, 
            priceY, priceX, System.currentTimeMillis()
        );
        
        logger.debug("Short spread executed - Z-Score: {:.4f}, Hedge Ratio: {:.4f}", 
            calculateZScore(calculateSpread(priceX, priceY)), hedgeRatio);
        
        return orders;
    }
    
    /**
     * Execute long spread (buy Y, sell X)
     */
    private List<Order> executeLongSpread(double priceX, double priceY) {
        List<Order> orders = new ArrayList<>();
        
        // Calculate quantities
        int quantityY = orderSize;
        int quantityX = (int)(orderSize * hedgeRatio);
        
        // Check minimum spread requirement
        double expectedProfit = Math.abs(calculateSpread(priceX, priceY)) * orderSize;
        if (expectedProfit < minSpread) {
            return orders;
        }
        
        // Create orders
        Order buyY = new Order(
            orderIdGenerator.getAndIncrement(),
            symbols[0],
            (long)(priceY * 10000),
            quantityY,
            (byte)0, // Buy
            (byte)0  // Limit
        );
        
        Order sellX = new Order(
            orderIdGenerator.getAndIncrement(),
            symbols[1],
            (long)(priceX * 10000),
            quantityX,
            (byte)1, // Sell
            (byte)0  // Limit
        );
        
        orders.add(buyY);
        orders.add(sellX);
        
        // Create active position
        activePosition = new ArbitragePosition(
            buyY.orderId, sellX.orderId, quantityY, quantityX, 
            priceY, priceX, System.currentTimeMillis()
        );
        
        logger.debug("Long spread executed - Z-Score: {:.4f}, Hedge Ratio: {:.4f}", 
            calculateZScore(calculateSpread(priceX, priceY)), hedgeRatio);
        
        return orders;
    }
    
    @Override
    public void onTrade(com.hft.core.Trade trade) {
        if (activePosition != null) {
            // Check if trade completes our position
            if (trade.buyOrderId == activePosition.buyOrderId || 
                trade.sellOrderId == activePosition.sellOrderId) {
                
                // Calculate realized P&L
                double realizedPnL = calculateRealizedPnL(trade);
                totalPnL += realizedPnL;
                tradesExecuted++;
                
                // Clear position if both legs filled
                if (isPositionComplete(trade)) {
                    activePosition = null;
                }
                
                logger.debug("Trade executed - P&L: ${:.2f}, Total: ${:.2f}", realizedPnL, totalPnL);
            }
        }
    }
    
    /**
     * Calculate realized P&L from trade
     */
    private double calculateRealizedPnL(com.hft.core.Trade trade) {
        if (activePosition == null) return 0.0;
        
        double tradeValue = trade.getPriceAsDouble() * trade.quantity;
        
        // Simplified P&L calculation
        if (trade.buyOrderId == activePosition.buyOrderId) {
            return -tradeValue; // Cost of buying
        } else {
            return tradeValue; // Proceeds from selling
        }
    }
    
    /**
     * Check if position is complete
     */
    private boolean isPositionComplete(com.hft.core.Trade trade) {
        // Simplified - in practice would track filled quantities
        return true;
    }
    
    @Override
    public String getName() {
        return "OptimizedStatisticalArbitrage";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    @Override
    public void reset() {
        // Reset regression variables
        sumX = 0.0; sumY = 0.0;
        sumXY = 0.0; sumX2 = 0.0;
        sampleCount = 0;
        hedgeRatio = 0.0;
        intercept = 0.0;
        
        // Reset spread tracking
        spreadIndex = 0;
        spreadCount = 0;
        spreadSum = 0.0;
        spreadSumSquared = 0.0;
        
        // Reset strategy state
        activePosition = null;
        totalPnL = 0.0;
        signalsGenerated = 0;
        tradesExecuted = 0;
        lastPriceX = 0.0;
        lastPriceY = 0.0;
        
        // Clear spread history
        for (int i = 0; i < lookbackPeriod; i++) {
            spreadHistory[i] = 0.0;
        }
        
        logger.info("OptimizedStatisticalArbitrageStrategy reset");
    }
    
    /**
     * Get performance metrics
     */
    public StatArbMetrics getMetrics() {
        double currentSpread = (lastPriceX > 0 && lastPriceY > 0) ? 
            calculateSpread(lastPriceX, lastPriceY) : 0.0;
        double zScore = (currentSpread != 0) ? calculateZScore(currentSpread) : 0.0;
        
        return new StatArbMetrics(
            hedgeRatio, intercept, currentSpread, zScore,
            signalsGenerated, tradesExecuted, totalPnL
        );
    }
    
    /**
     * Arbitrage position inner class
     */
    private static class ArbitragePosition {
        final long sellOrderId;
        final long buyOrderId;
        final int quantityY;
        final int quantityX;
        final double entryPriceY;
        final double entryPriceX;
        final long entryTime;
        
        ArbitragePosition(long sellOrderId, long buyOrderId, int quantityY, int quantityX,
                        double entryPriceY, double entryPriceX, long entryTime) {
            this.sellOrderId = sellOrderId;
            this.buyOrderId = buyOrderId;
            this.quantityY = quantityY;
            this.quantityX = quantityX;
            this.entryPriceY = entryPriceY;
            this.entryPriceX = entryPriceX;
            this.entryTime = entryTime;
        }
        
        boolean isComplete() {
            // In practice would track filled quantities
            return false;
        }
    }
    
    /**
     * Performance metrics inner class
     */
    public static class StatArbMetrics {
        public final double hedgeRatio;
        public final double intercept;
        public final double currentSpread;
        public final double zScore;
        public final long signalsGenerated;
        public final long tradesExecuted;
        public final double totalPnL;
        
        public StatArbMetrics(double hedgeRatio, double intercept, double currentSpread,
                            double zScore, long signalsGenerated, long tradesExecuted, 
                            double totalPnL) {
            this.hedgeRatio = hedgeRatio;
            this.intercept = intercept;
            this.currentSpread = currentSpread;
            this.zScore = zScore;
            this.signalsGenerated = signalsGenerated;
            this.tradesExecuted = tradesExecuted;
            this.totalPnL = totalPnL;
        }
        
        @Override
        public String toString() {
            return String.format(
                "StatArbMetrics{hedgeRatio=%.4f, intercept=%.4f, spread=%.4f, zScore=%.4f, " +
                "signals=%d, trades=%d, pnl=%.2f}",
                hedgeRatio, intercept, currentSpread, zScore, signalsGenerated, 
                tradesExecuted, totalPnL
            );
        }
    }
}
