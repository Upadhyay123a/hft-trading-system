package com.hft.orderbook;

import com.hft.core.Order;
import com.hft.core.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Professional O(1) Order Book Implementation
 * Based on Citadel/HRT array-based price level optimization
 * 
 * Key Features:
 * - O(1) price level access via direct array indexing
 * - Cache-friendly memory layout for better CPU performance
 * - Fixed price granularity for predictable behavior
 * - Lock-free operations where possible
 */
public class OptimizedOrderBook {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedOrderBook.class);
    
    // Price grid configuration - Citadel/HRT approach
    private static final long BASE_PRICE = 100000000L; // $10,000 base
    private static final long MAX_PRICE = 250000000L;  // $25,000 max
    private static final long MIN_PRICE = 50000L;     // $50 min
    private static final int PRICE_TICK = 100;        // $0.01 granularity
    private static final int PRICE_LEVELS = (int)(((MAX_PRICE - MIN_PRICE) / PRICE_TICK) + 1);
    
    // Array-based price levels - O(1) access
    private final PriceLevel[] bidLevels = new PriceLevel[PRICE_LEVELS];
    private final PriceLevel[] askLevels = new PriceLevel[PRICE_LEVELS];
    
    // Fast order lookup
    private final HashMap<Long, Order> orderMap = new HashMap<>();
    
    // Market state
    private long bestBid = 0;
    private long bestAsk = Long.MAX_VALUE;
    private final int symbolId;
    
    // Performance tracking
    private final AtomicLong orderCount = new AtomicLong(0);
    private final AtomicLong tradeCount = new AtomicLong(0);
    
    public OptimizedOrderBook(int symbolId) {
        this.symbolId = symbolId;
        initializePriceLevels();
        logger.info("OptimizedOrderBook initialized for symbol {} with {} price levels", 
            symbolId, PRICE_LEVELS);
    }
    
    /**
     * Initialize all price levels - O(n) once at startup
     */
    private void initializePriceLevels() {
        for (int i = 0; i < PRICE_LEVELS; i++) {
            bidLevels[i] = new PriceLevel();
            askLevels[i] = new PriceLevel();
        }
    }
    
    /**
     * Convert price to array index - O(1) calculation
     */
    private int priceToIndex(long price) {
        if (price < MIN_PRICE || price > MAX_PRICE) {
            return -1; // Out of range
        }
        return (int)((price - MIN_PRICE) / PRICE_TICK);
    }
    
    /**
     * Convert index back to price - O(1) calculation
     */
    private long indexToPrice(int index) {
        return MIN_PRICE + (index * PRICE_TICK);
    }
    
    /**
     * Add order - O(1) operation
     */
    public boolean addOrder(Order order) {
        int index = priceToIndex(order.price);
        if (index == -1) {
            logger.warn("Price {} out of range for symbol {}", order.price, symbolId);
            return false;
        }
        
        // Add to appropriate price level
        PriceLevel level = order.isBuy() ? bidLevels[index] : askLevels[index];
        level.addOrder(order);
        
        // Update order map
        orderMap.put(order.orderId, order);
        
        // Update best bid/ask - O(1) comparison
        updateBestPrices(order);
        
        orderCount.incrementAndGet();
        
        return true;
    }
    
    /**
     * Cancel order - O(1) operation
     */
    public boolean cancelOrder(long orderId) {
        Order order = orderMap.get(orderId);
        if (order == null) {
            return false;
        }
        
        int index = priceToIndex(order.price);
        if (index == -1) {
            return false;
        }
        
        // Remove from price level
        PriceLevel level = order.isBuy() ? bidLevels[index] : askLevels[index];
        level.removeOrder(order);
        
        // Remove from order map
        orderMap.remove(orderId);
        
        // Update best bid/ask if needed
        updateBestPricesAfterCancel(order);
        
        return true;
    }
    
    /**
     * Get mid price - O(1) operation
     */
    public long getMidPrice() {
        if (bestBid == 0 || bestAsk == Long.MAX_VALUE) {
            return 0; // No market
        }
        return (bestBid + bestAsk) / 2;
    }
    
    /**
     * Get spread - O(1) operation
     */
    public long getSpread() {
        if (bestBid == 0 || bestAsk == Long.MAX_VALUE) {
            return 0;
        }
        return bestAsk - bestBid;
    }
    
    /**
     * Market order execution - O(k) where k is price levels to consume
     */
    public List<Trade> executeMarketOrder(Order marketOrder) {
        List<Trade> trades = new ArrayList<>();
        
        if (marketOrder.isBuy()) {
            // Buy market order - consume asks
            trades = executeMarketBuy(marketOrder);
        } else {
            // Sell market order - consume bids
            trades = executeMarketSell(marketOrder);
        }
        
        return trades;
    }
    
    /**
     * Execute market buy order
     */
    private List<Trade> executeMarketBuy(Order marketOrder) {
        List<Trade> trades = new ArrayList<>();
        long remainingQuantity = marketOrder.quantity;
        long executionPrice = bestAsk;
        
        // Consume from best ask upwards
        for (int i = priceToIndex(bestAsk); i < PRICE_LEVELS && remainingQuantity > 0; i++) {
            PriceLevel level = askLevels[i];
            if (level.getTotalQuantity() == 0) continue;
            
            long availableQuantity = Math.min(remainingQuantity, level.getTotalQuantity());
            
            // Create trades with orders at this level
            List<Order> ordersAtLevel = new ArrayList<>(level.getOrders());
            for (Order order : ordersAtLevel) {
                if (remainingQuantity <= 0) break;
                
                long tradeQuantity = Math.min(availableQuantity, order.getRemainingQuantity());
                if (tradeQuantity > 0) {
                    Trade trade = createTrade(order, marketOrder, tradeQuantity, indexToPrice(i));
                    trades.add(trade);
                    
                    // Update order
                    order.fill(tradeQuantity);
                    remainingQuantity -= tradeQuantity;
                    
                    // Remove from order map if fully filled
                    if (order.getRemainingQuantity() == 0) {
                        orderMap.remove(order.orderId);
                    }
                }
            }
            
            // Clear empty orders from level
            level.removeEmptyOrders();
        }
        
        // Update best ask
        updateBestAsk();
        
        return trades;
    }
    
    /**
     * Execute market sell order
     */
    private List<Trade> executeMarketSell(Order marketOrder) {
        List<Trade> trades = new ArrayList<>();
        long remainingQuantity = marketOrder.quantity;
        long executionPrice = bestBid;
        
        // Consume from best bid downwards
        for (int i = priceToIndex(bestBid); i >= 0 && remainingQuantity > 0; i--) {
            PriceLevel level = bidLevels[i];
            if (level.getTotalQuantity() == 0) continue;
            
            long availableQuantity = Math.min(remainingQuantity, level.getTotalQuantity());
            
            // Create trades with orders at this level
            List<Order> ordersAtLevel = new ArrayList<>(level.getOrders());
            for (Order order : ordersAtLevel) {
                if (remainingQuantity <= 0) break;
                
                long tradeQuantity = Math.min(availableQuantity, order.getRemainingQuantity());
                if (tradeQuantity > 0) {
                    Trade trade = createTrade(order, marketOrder, tradeQuantity, indexToPrice(i));
                    trades.add(trade);
                    
                    // Update order
                    order.fill(tradeQuantity);
                    remainingQuantity -= tradeQuantity;
                    
                    // Remove from order map if fully filled
                    if (order.getRemainingQuantity() == 0) {
                        orderMap.remove(order.orderId);
                    }
                }
            }
            
            // Clear empty orders from level
            level.removeEmptyOrders();
        }
        
        // Update best bid
        updateBestBid();
        
        return trades;
    }
    
    /**
     * Create trade object
     */
    private Trade createTrade(Order restingOrder, Order marketOrder, long quantity, long price) {
        Trade trade = new Trade();
        trade.tradeId = tradeCount.incrementAndGet();
        trade.symbolId = symbolId;
        trade.price = price;
        trade.quantity = (int) quantity;
        
        if (marketOrder.isBuy()) {
            trade.buyOrderId = marketOrder.orderId;
            trade.sellOrderId = restingOrder.orderId;
        } else {
            trade.buyOrderId = restingOrder.orderId;
            trade.sellOrderId = marketOrder.orderId;
        }
        
        return trade;
    }
    
    /**
     * Update best prices after adding order
     */
    private void updateBestPrices(Order order) {
        if (order.isBuy()) {
            if (order.price > bestBid) {
                bestBid = order.price;
            }
        } else {
            if (order.price < bestAsk) {
                bestAsk = order.price;
            }
        }
    }
    
    /**
     * Update best prices after cancel
     */
    private void updateBestPricesAfterCancel(Order cancelledOrder) {
        if (cancelledOrder.isBuy() && cancelledOrder.price == bestBid) {
            updateBestBid();
        } else if (!cancelledOrder.isBuy() && cancelledOrder.price == bestAsk) {
            updateBestAsk();
        }
    }
    
    /**
     * Update best bid - O(k) where k is empty levels
     */
    private void updateBestBid() {
        bestBid = 0;
        for (int i = priceToIndex(bestBid); i >= 0; i--) {
            if (bidLevels[i].getTotalQuantity() > 0) {
                bestBid = indexToPrice(i);
                break;
            }
        }
    }
    
    /**
     * Update best ask - O(k) where k is empty levels
     */
    private void updateBestAsk() {
        bestAsk = Long.MAX_VALUE;
        for (int i = priceToIndex(bestAsk); i < PRICE_LEVELS; i++) {
            if (askLevels[i].getTotalQuantity() > 0) {
                bestAsk = indexToPrice(i);
                break;
            }
        }
    }
    
    /**
     * Get order book state for debugging
     */
    public String getBookState() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Optimized Order Book: ").append(symbolId).append(" ===\n");
        
        sb.append("ASKS:\n");
        for (int i = priceToIndex(bestAsk); i < Math.min(priceToIndex(bestAsk) + 10, PRICE_LEVELS); i++) {
            if (askLevels[i].getTotalQuantity() > 0) {
                sb.append(String.format("Price: %.4f, Qty: %d\n", 
                    indexToPrice(i) / 10000.0, askLevels[i].getTotalQuantity()));
            }
        }
        
        sb.append(String.format("Spread: %.4f\n", getSpread() / 10000.0));
        
        sb.append("BIDS:\n");
        for (int i = priceToIndex(bestBid); i >= Math.max(priceToIndex(bestBid) - 10, 0); i--) {
            if (bidLevels[i].getTotalQuantity() > 0) {
                sb.append(String.format("Price: %.4f, Qty: %d\n", 
                    indexToPrice(i) / 10000.0, bidLevels[i].getTotalQuantity()));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Performance metrics
     */
    public long getOrderCount() {
        return orderCount.get();
    }
    
    public long getTradeCount() {
        return tradeCount.get();
    }
    
    /**
     * Price level inner class
     */
    private static class PriceLevel {
        private final List<Order> orders = new ArrayList<>();
        
        public void addOrder(Order order) {
            orders.add(order);
        }
        
        public void removeOrder(Order order) {
            orders.remove(order);
        }
        
        public void removeEmptyOrders() {
            orders.removeIf(order -> order.getRemainingQuantity() == 0);
        }
        
        public List<Order> getOrders() {
            return new ArrayList<>(orders);
        }
        
        public long getTotalQuantity() {
            return orders.stream().mapToLong(Order::getRemainingQuantity).sum();
        }
    }
}
