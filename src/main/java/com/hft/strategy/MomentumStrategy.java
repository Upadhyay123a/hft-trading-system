package com.hft.strategy;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ArrayDeque; // FIX 1: ArrayDeque instead of ArrayList for O(1) remove from front
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
    private final double threshold;      // Price change % threshold to trigger (e.g. 0.05 = 0.05%)
    private final int orderSize;
    private final long maxPosition;
    
    private final AtomicLong orderIdGenerator = new AtomicLong(1000);

    // FIX 1: ArrayDeque gives O(1) addLast / removeFirst vs ArrayList's O(n) remove(0)
    private final ArrayDeque<Long> recentPrices = new ArrayDeque<>();
    
    // FIX 2: volatile so Disruptor handler thread and monitor thread both see latest values
    private volatile long currentPosition = 0;
    private volatile double totalPnL = 0.0;
    private volatile long lastTradeTime = 0;

    // FIX 3: added L suffix — without it the literal is an int (max ~2.1B fits, but
    // explicit L makes intent clear and prevents future refactor bugs)
    private final long minTimeBetweenTrades = 1_000_000_000L; // 1 second in nanoseconds

    // FIX 4: track our own order IDs so onTrade() can correctly identify buyer/seller
    // instead of the fragile magic-number heuristic (trade.buyOrderId >= 1000)
    private final java.util.Set<Long> ourBuyOrderIds  = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final java.util.Set<Long> ourSellOrderIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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
        
        // FIX 1: use ArrayDeque API — addLast / size / peekFirst / pollFirst
        recentPrices.addLast(tick.price);
        if (recentPrices.size() > lookbackPeriod) {
            recentPrices.pollFirst(); // O(1) instead of ArrayList.remove(0) which is O(n)
        }
        
        // Need enough data
        if (recentPrices.size() < lookbackPeriod) {
            return orders;
        }
        
        // Calculate momentum
        long oldPrice = recentPrices.peekFirst();
        long newPrice = recentPrices.peekLast();
        
        // Add null safety for price calculation
        if (oldPrice <= 0 || newPrice <= 0) {
            return orders; // Skip invalid prices
        }
        
        double priceChange = ((newPrice - oldPrice) / (double)oldPrice) * 100.0;
        
        // Rate limit trades
        long now = System.nanoTime();
        if (now - lastTradeTime < minTimeBetweenTrades) {
            return orders;
        }
        
        // Generate signals
        if (priceChange > threshold && currentPosition < maxPosition) {
            // Bullish momentum - buy
            long orderId = orderIdGenerator.getAndIncrement();
            Order buyOrder = new Order(
                orderId,
                symbolId,
                tick.price, // Market price
                orderSize,
                (byte)0, // Buy
                (byte)1  // Market order
            );
            ourBuyOrderIds.add(orderId); // FIX 4: track as our buy order
            orders.add(buyOrder);
            lastTradeTime = now;
            logger.info("BUY signal: momentum={:.4f}%", priceChange);
        } 
        else if (priceChange < -threshold && currentPosition > -maxPosition) {
            // Bearish momentum - sell
            long orderId = orderIdGenerator.getAndIncrement();
            Order sellOrder = new Order(
                orderId,
                symbolId,
                tick.price,
                orderSize,
                (byte)1, // Sell
                (byte)1  // Market order
            );
            ourSellOrderIds.add(orderId); // FIX 4: track as our sell order
            orders.add(sellOrder);
            lastTradeTime = now;
            logger.info("SELL signal: momentum={:.4f}%", priceChange);
        }
        
        return orders;
    }
    
    @Override
    public void onTrade(Trade trade) {
        // FIX 4: use tracked order ID sets instead of fragile magic-number heuristic
        // Old code: boolean wasBuyer = trade.buyOrderId >= 1000; // breaks if IDs reset
        boolean wasBuyer = ourBuyOrderIds.contains(trade.buyOrderId);

        if (wasBuyer) {
            currentPosition += trade.quantity;
            totalPnL -= trade.getPriceAsDouble() * trade.quantity; // cost of buy
            ourBuyOrderIds.remove(trade.buyOrderId); // clean up to avoid memory leak
        } else if (ourSellOrderIds.contains(trade.sellOrderId)) {
            currentPosition -= trade.quantity;
            totalPnL += trade.getPriceAsDouble() * trade.quantity; // revenue from sell
            ourSellOrderIds.remove(trade.sellOrderId); // clean up
        }
        // If trade belongs to someone else, ignore it
        
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
        ourBuyOrderIds.clear();
        ourSellOrderIds.clear();
    }
    
    public long getCurrentPosition() {
        return currentPosition;
    }
}