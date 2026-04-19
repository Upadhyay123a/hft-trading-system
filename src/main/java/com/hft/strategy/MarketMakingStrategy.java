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
 * Market Making Strategy
 * Places buy and sell orders around mid-price to capture spread
 */
public class MarketMakingStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(MarketMakingStrategy.class);
    
    private final int symbolId;
    private final long spreadTicks;     // Spread in price ticks (e.g., 10 = 0.001)
    private final int orderSize;        // Order quantity
    private final long maxPosition;     // Maximum position size
    
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private long currentPosition = 0;   // Positive = long, negative = short
    private double totalPnL = 0.0;
    private long lastQuoteTime = 0;
    private final long quoteIntervalNanos = 100_000_000; // 100ms between quotes
    
    // Active orders
    private Long activeBuyOrderId = null;
    private Long activeSellOrderId = null;
    
    public MarketMakingStrategy(int symbolId, double spreadPercent, int orderSize, long maxPosition) {
        this.symbolId = symbolId;
        this.spreadTicks = (long)(spreadPercent * 10000); // Convert percent to ticks
        this.orderSize = orderSize;
        this.maxPosition = maxPosition;
    }
    
    @Override
    public void initialize() {
        logger.info("Initialized Market Making Strategy for symbol {}", symbolId);
        logger.info("Spread: {}%, Order Size: {}, Max Position: {}", 
            spreadTicks / 100.0, orderSize, maxPosition);
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Only process ticks for our symbol
        if (tick.symbolId != symbolId) {
            return orders;
        }
        
        // Rate limit quoting
        long now = System.nanoTime();
        if (now - lastQuoteTime < quoteIntervalNanos) {
            return orders;
        }
        lastQuoteTime = now;
        
        // Get current market state
        long midPrice = 0;
        if (orderBook != null && orderBook.getMidPrice() > 0) {
            // Use order book mid price if available
            midPrice = orderBook.getMidPrice();
        } else if (tick.price > 0) {
            // Fall back to tick price if order book is empty
            midPrice = tick.price;
            logger.debug("Using tick price as mid price: {}", tick.price);
        }
        
        if (midPrice == 0) {
            return orders; // No market data yet
        }
        
        // Calculate quote prices
        long bidPrice = midPrice - spreadTicks / 2;
        long askPrice = midPrice + spreadTicks / 2;
        
        // Check position limits
        boolean canBuy = currentPosition < maxPosition;
        boolean canSell = currentPosition > -maxPosition;
        
        // Debug logging
        logger.debug("MarketMaking: midPrice={}, bidPrice={}, askPrice={}, canBuy={}, canSell={}, activeBuy={}, activeSell={}", 
                    midPrice, bidPrice, askPrice, canBuy, canSell, activeBuyOrderId, activeSellOrderId);
        
        // Place buy order
        if (canBuy && activeBuyOrderId == null) {
            Order buyOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                bidPrice,
                orderSize,
                (byte)0, // Buy
                (byte)0  // Limit
            );
            orders.add(buyOrder);
            activeBuyOrderId = buyOrder.orderId;
            logger.info("MarketMaking: Placed BUY order - ID={}, price={}, qty={}", 
                       buyOrder.orderId, bidPrice, orderSize);
        } else {
            logger.debug("MarketMaking: Not placing BUY order - canBuy={}, activeBuyId={}", 
                        canBuy, activeBuyOrderId);
        }
        
        // Place sell order
        if (canSell && activeSellOrderId == null) {
            Order sellOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                askPrice,
                orderSize,
                (byte)1, // Sell
                (byte)0  // Limit
            );
            orders.add(sellOrder);
            activeSellOrderId = sellOrder.orderId;
            logger.info("MarketMaking: Placed SELL order - ID={}, price={}, qty={}", 
                       sellOrder.orderId, askPrice, orderSize);
        } else {
            logger.debug("MarketMaking: Not placing SELL order - canSell={}, activeSellId={}", 
                        canSell, activeSellOrderId);
        }
        
        logger.debug("MarketMaking: Returning {} orders", orders.size());
        return orders;
    }
    
    @Override
    public void onTrade(Trade trade) {
        // Update position
        if (trade.buyOrderId == activeBuyOrderId) {
            currentPosition += trade.quantity;
            activeBuyOrderId = null; // Can place new order
            logger.debug("Buy filled: qty={}, pos={}", trade.quantity, currentPosition);
        }
        
        if (trade.sellOrderId == activeSellOrderId) {
            currentPosition -= trade.quantity;
            activeSellOrderId = null; // Can place new order
            logger.debug("Sell filled: qty={}, pos={}", trade.quantity, currentPosition);
        }
        
        // Calculate realized P&L (simplified)
        double tradeValue = trade.getPriceAsDouble() * trade.quantity;
        
        // Check if this was our buy order that got filled
        boolean wasOurBuy = (activeBuyOrderId != null && trade.buyOrderId == activeBuyOrderId);
        boolean wasOurSell = (activeSellOrderId != null && trade.sellOrderId == activeSellOrderId);
        
        if (wasOurBuy) {
            // We bought - reduce P&L (we paid money)
            totalPnL -= tradeValue;
            logger.debug("Buy trade: price={}, qty={}, value={}, pnl={}", 
                        trade.getPriceAsDouble(), trade.quantity, tradeValue, totalPnL);
        } else if (wasOurSell) {
            // We sold - increase P&L (we received money)
            totalPnL += tradeValue;
            logger.debug("Sell trade: price={}, qty={}, value={}, pnl={}", 
                        trade.getPriceAsDouble(), trade.quantity, tradeValue, totalPnL);
        }
    }
    
    @Override
    public String getName() {
        return "MarketMaking";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    @Override
    public void reset() {
        currentPosition = 0;
        totalPnL = 0.0;
        activeBuyOrderId = null;
        activeSellOrderId = null;
    }
    
    public long getCurrentPosition() {
        return currentPosition;
    }
}