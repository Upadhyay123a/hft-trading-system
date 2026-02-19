package com.hft.strategy;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Professional O(1) Momentum Strategy Implementation
 * Based on Two Sigma/Virtu circular buffer optimization
 * 
 * Key Features:
 * - O(1) momentum calculation using circular buffer
 * - Incremental statistics for real-time updates
 * - Cache-friendly memory layout
 * - Lock-free operations where possible
 */
public class OptimizedMomentumStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedMomentumStrategy.class);
    
    // Strategy parameters
    private final int symbolId;
    private final int lookbackPeriod;
    private final double threshold;
    private final int orderSize;
    private final long maxPosition;
    
    // Circular buffer for O(1) momentum calculation - Two Sigma approach
    private final double[] priceBuffer;
    private int writeIndex = 0;
    private int bufferCount = 0;
    
    // Incremental statistics for O(1) calculations
    private double sum = 0.0;
    private double sumSquared = 0.0;
    private double oldestPrice = 0.0;
    private double currentPrice = 0.0;
    
    // Strategy state
    private final AtomicLong orderIdGenerator = new AtomicLong(2000);
    private long currentPosition = 0;
    private double totalPnL = 0.0;
    private long lastTradeTime = 0;
    private final long minTimeBetweenTrades = 1_000_000_000; // 1 second
    
    // Performance tracking
    private long signalsGenerated = 0;
    private long tradesExecuted = 0;
    
    public OptimizedMomentumStrategy(int symbolId, int lookbackPeriod, double threshold, 
                                   int orderSize, long maxPosition) {
        this.symbolId = symbolId;
        this.lookbackPeriod = lookbackPeriod;
        this.threshold = threshold;
        this.orderSize = orderSize;
        this.maxPosition = maxPosition;
        
        // Initialize circular buffer
        this.priceBuffer = new double[lookbackPeriod];
        
        logger.info("OptimizedMomentumStrategy initialized - Symbol: {}, Lookback: {}, Threshold: {}%", 
            symbolId, lookbackPeriod, threshold);
    }
    
    @Override
    public void initialize() {
        logger.info("OptimizedMomentumStrategy ready for symbol {}", symbolId);
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (tick.symbolId != symbolId) {
            return orders;
        }
        
        // O(1) update circular buffer and statistics
        updatePriceBuffer(tick.getPriceAsDouble());
        
        // Need enough data for momentum calculation
        if (bufferCount < lookbackPeriod) {
            return orders;
        }
        
        // O(1) momentum calculation
        double momentum = calculateMomentum();
        
        // Rate limiting
        long now = System.nanoTime();
        if (now - lastTradeTime < minTimeBetweenTrades) {
            return orders;
        }
        
        // Generate trading signals
        if (momentum > threshold && currentPosition < maxPosition) {
            // Bullish momentum - buy
            Order buyOrder = createBuyOrder(tick.price);
            orders.add(buyOrder);
            lastTradeTime = now;
            signalsGenerated++;
            
            logger.debug("BUY signal - Momentum: {:.4f}%, Price: ${:.2f}", 
                momentum, tick.getPriceAsDouble());
            
        } else if (momentum < -threshold && currentPosition > -maxPosition) {
            // Bearish momentum - sell
            Order sellOrder = createSellOrder(tick.price);
            orders.add(sellOrder);
            lastTradeTime = now;
            signalsGenerated++;
            
            logger.debug("SELL signal - Momentum: {:.4f}%, Price: ${:.2f}", 
                momentum, tick.getPriceAsDouble());
        }
        
        return orders;
    }
    
    /**
     * O(1) circular buffer update - Two Sigma approach
     */
    private void updatePriceBuffer(double newPrice) {
        // Remove old price from statistics
        if (bufferCount >= lookbackPeriod) {
            double oldPrice = priceBuffer[writeIndex];
            sum -= oldPrice;
            sumSquared -= oldPrice * oldPrice;
            
            // Track oldest price for momentum calculation
            oldestPrice = oldPrice;
        }
        
        // Add new price to statistics
        sum += newPrice;
        sumSquared += newPrice * newPrice;
        currentPrice = newPrice;
        
        // Update circular buffer
        priceBuffer[writeIndex] = newPrice;
        writeIndex = (writeIndex + 1) % lookbackPeriod;
        
        if (bufferCount < lookbackPeriod) {
            bufferCount++;
        }
    }
    
    /**
     * O(1) momentum calculation
     */
    private double calculateMomentum() {
        if (bufferCount < lookbackPeriod) {
            return 0.0;
        }
        
        // Calculate momentum as percentage change
        double priceChange = (currentPrice - oldestPrice) / oldestPrice * 100.0;
        return priceChange;
    }
    
    /**
     * O(1) volatility calculation for risk management
     */
    private double calculateVolatility() {
        if (bufferCount < 2) {
            return 0.0;
        }
        
        double mean = sum / bufferCount;
        double variance = (sumSquared / bufferCount) - (mean * mean);
        return Math.sqrt(variance);
    }
    
    /**
     * O(1) trend strength calculation
     */
    private double calculateTrendStrength() {
        if (bufferCount < 2) {
            return 0.0;
        }
        
        double mean = sum / bufferCount;
        double volatility = calculateVolatility();
        
        if (volatility == 0) {
            return 0.0;
        }
        
        // Trend strength as signal-to-noise ratio
        double momentum = calculateMomentum();
        return Math.abs(momentum) / volatility;
    }
    
    /**
     * Create buy order
     */
    private Order createBuyOrder(long price) {
        Order order = new Order(
            orderIdGenerator.getAndIncrement(),
            symbolId,
            price,
            orderSize,
            (byte)0, // Buy
            (byte)1  // Market order
        );
        return order;
    }
    
    /**
     * Create sell order
     */
    private Order createSellOrder(long price) {
        Order order = new Order(
            orderIdGenerator.getAndIncrement(),
            symbolId,
            price,
            orderSize,
            (byte)1, // Sell
            (byte)1  // Market order
        );
        return order;
    }
    
    @Override
    public void onTrade(Trade trade) {
        // Update position based on trade execution
        boolean wasBuyer = trade.buyOrderId >= 2000; // Our orders start at 2000
        
        if (wasBuyer) {
            currentPosition += trade.quantity;
            totalPnL -= trade.getPriceAsDouble() * trade.quantity;
        } else {
            currentPosition -= trade.quantity;
            totalPnL += trade.getPriceAsDouble() * trade.quantity;
        }
        
        tradesExecuted++;
        
        logger.debug("Trade executed - Price: ${:.2f}, Qty: {}, Pos: {}, P&L: ${:.2f}", 
            trade.getPriceAsDouble(), trade.quantity, currentPosition, totalPnL);
    }
    
    @Override
    public String getName() {
        return "OptimizedMomentum";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    @Override
    public void reset() {
        // Reset circular buffer
        writeIndex = 0;
        bufferCount = 0;
        
        // Reset statistics
        sum = 0.0;
        sumSquared = 0.0;
        oldestPrice = 0.0;
        currentPrice = 0.0;
        
        // Reset strategy state
        currentPosition = 0;
        totalPnL = 0.0;
        lastTradeTime = 0;
        signalsGenerated = 0;
        tradesExecuted = 0;
        
        // Clear price buffer
        for (int i = 0; i < lookbackPeriod; i++) {
            priceBuffer[i] = 0.0;
        }
        
        logger.info("OptimizedMomentumStrategy reset");
    }
    
    /**
     * Get performance metrics
     */
    public MomentumMetrics getMetrics() {
        return new MomentumMetrics(
            calculateMomentum(),
            calculateVolatility(),
            calculateTrendStrength(),
            signalsGenerated,
            tradesExecuted,
            currentPosition,
            totalPnL
        );
    }
    
    public long getCurrentPosition() {
        return currentPosition;
    }
    
    /**
     * Performance metrics inner class
     */
    public static class MomentumMetrics {
        public final double momentum;
        public final double volatility;
        public final double trendStrength;
        public final long signalsGenerated;
        public final long tradesExecuted;
        public final long currentPosition;
        public final double totalPnL;
        
        public MomentumMetrics(double momentum, double volatility, double trendStrength,
                             long signalsGenerated, long tradesExecuted, long currentPosition, 
                             double totalPnL) {
            this.momentum = momentum;
            this.volatility = volatility;
            this.trendStrength = trendStrength;
            this.signalsGenerated = signalsGenerated;
            this.tradesExecuted = tradesExecuted;
            this.currentPosition = currentPosition;
            this.totalPnL = totalPnL;
        }
        
        @Override
        public String toString() {
            return String.format(
                "MomentumMetrics{momentum=%.4f%%, volatility=%.4f, trendStrength=%.4f, " +
                "signals=%d, trades=%d, position=%d, pnl=%.2f}",
                momentum, volatility, trendStrength, signalsGenerated, tradesExecuted, 
                currentPosition, totalPnL
            );
        }
    }
}
