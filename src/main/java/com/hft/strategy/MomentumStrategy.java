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
 * Simple Momentum Strategy
 * Buys when price is rising, sells when falling
 */
public class MomentumStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(MomentumStrategy.class);
    
    private final int symbolId;
    private final int lookbackPeriod;    // Number of ticks to look back
    private final double threshold;      // Price change threshold to trigger
    private final int orderSize;
    private final long maxPosition;
    
    private final AtomicLong orderIdGenerator = new AtomicLong(1000);
    private final List<Long> recentPrices = new ArrayList<>();
    
    private long currentPosition = 0;
    private double totalPnL = 0.0;
    private long lastTradeTime = 0;
    private final long minTimeBetweenTrades = 1_000_000_000; // 1 second
    
    public MomentumStrategy(int symbolId, int lookbackPeriod, double threshold, 
                           int orderSize, long maxPosition) {
        this.symbolId = symbolId;
        this.lookbackPeriod = lookbackPeriod;
        this.threshold = threshold;
        this.orderSize = orderSize;
        this.maxPosition = maxPosition;
    }
    
    @Override
    public void initialize() {
        logger.info("Initialized Momentum Strategy for symbol {}", symbolId);
        logger.info("Lookback: {}, Threshold: {}%, Size: {}, Max Pos: {}",
            lookbackPeriod, threshold, orderSize, maxPosition);
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (tick.symbolId != symbolId) {
            return orders;
        }
        
        // Add price to history
        recentPrices.add(tick.price);
        if (recentPrices.size() > lookbackPeriod) {
            recentPrices.remove(0);
        }
        
        // Need enough data
        if (recentPrices.size() < lookbackPeriod) {
            return orders;
        }
        
        // Calculate momentum
        long oldPrice = recentPrices.get(0);
        long newPrice = recentPrices.get(recentPrices.size() - 1);
        double priceChange = ((newPrice - oldPrice) / (double)oldPrice) * 100.0;
        
        // Rate limit trades
        long now = System.nanoTime();
        if (now - lastTradeTime < minTimeBetweenTrades) {
            return orders;
        }
        
        // Generate signals
        if (priceChange > threshold && currentPosition < maxPosition) {
            // Bullish momentum - buy
            Order buyOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price, // Market price
                orderSize,
                (byte)0, // Buy
                (byte)1  // Market order
            );
            orders.add(buyOrder);
            lastTradeTime = now;
            logger.debug("BUY signal: momentum={}%", priceChange);
        } 
        else if (priceChange < -threshold && currentPosition > -maxPosition) {
            // Bearish momentum - sell
            Order sellOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price,
                orderSize,
                (byte)1, // Sell
                (byte)1  // Market order
            );
            orders.add(sellOrder);
            lastTradeTime = now;
            logger.debug("SELL signal: momentum={}%", priceChange);
        }
        
        return orders;
    }
    
    @Override
    public void onTrade(Trade trade) {
        // Update position based on which side we were on
        boolean wasBuyer = trade.buyOrderId >= 1000; // Our orders start at 1000
        
        if (wasBuyer) {
            currentPosition += trade.quantity;
            totalPnL -= trade.getPriceAsDouble() * trade.quantity;
        } else {
            currentPosition -= trade.quantity;
            totalPnL += trade.getPriceAsDouble() * trade.quantity;
        }
        
        logger.debug("Trade executed: price={}, qty={}, pos={}, pnl={}",
            trade.getPriceAsDouble(), trade.quantity, currentPosition, totalPnL);
    }
    
    @Override
    public String getName() {
        return "Momentum";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    @Override
    public void reset() {
        recentPrices.clear();
        currentPosition = 0;
        totalPnL = 0.0;
    }
    
    public long getCurrentPosition() {
        return currentPosition;
    }
}