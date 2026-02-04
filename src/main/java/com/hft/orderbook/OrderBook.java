package com.hft.orderbook;

import com.hft.core.Order;
import com.hft.core.Trade;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance order book implementation
 * Uses TreeMap for price levels and maintains fast lookup
 */
public class OrderBook {
    private final int symbolId;
    
    // Price levels: price -> total quantity at that level
    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();
    
    // Fast order lookup
    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();
    
    // Statistics
    private volatile long lastTradePrice = 0;
    private volatile int lastTradeQuantity = 0;
    private final AtomicLong tradeIdGenerator = new AtomicLong(1);
    
    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }
    
    /**
     * Add order to book
     */
    public synchronized List<Trade> addOrder(Order order) {
        List<Trade> trades = new ArrayList<>();
        
        if (order.type == 1) { // Market order
            trades = executeMarketOrder(order);
        } else if (order.type == 2) { // IOC
            trades = executeIOCOrder(order);
        } else if (order.type == 3) { // FOK
            trades = executeFOKOrder(order);
        } else { // Limit order
            trades = executeLimitOrder(order);
        }
        
        return trades;
    }
    
    /**
     * Execute limit order - match or add to book
     */
    private List<Trade> executeLimitOrder(Order order) {
        List<Trade> trades = new ArrayList<>();
        
        // Try to match against existing orders
        TreeMap<Long, PriceLevel> oppositeSide = order.isBuy() ? asks : bids;
        
        while (order.getRemainingQuantity() > 0 && !oppositeSide.isEmpty()) {
            Map.Entry<Long, PriceLevel> bestLevel = oppositeSide.firstEntry();
            
            // Check if price crosses
            if (order.isBuy() && order.price < bestLevel.getKey()) break;
            if (order.isSell() && order.price > bestLevel.getKey()) break;
            
            // Match orders
            PriceLevel level = bestLevel.getValue();
            Order oppositeOrder = level.orders.peek();
            
            if (oppositeOrder == null) {
                oppositeSide.remove(bestLevel.getKey());
                continue;
            }
            
            int matchQty = Math.min(order.getRemainingQuantity(), oppositeOrder.getRemainingQuantity());
            
            // Create trade
            Trade trade = new Trade(
                tradeIdGenerator.getAndIncrement(),
                order.isBuy() ? order.orderId : oppositeOrder.orderId,
                order.isSell() ? order.orderId : oppositeOrder.orderId,
                symbolId,
                oppositeOrder.price,
                matchQty
            );
            trades.add(trade);
            
            // Update orders
            order.filledQuantity += matchQty;
            oppositeOrder.filledQuantity += matchQty;
            
            lastTradePrice = oppositeOrder.price;
            lastTradeQuantity = matchQty;
            
            // Remove fully filled order
            if (oppositeOrder.getRemainingQuantity() == 0) {
                level.orders.poll();
                level.totalQuantity -= oppositeOrder.quantity;
                oppositeOrder.status = 2; // Filled
                
                if (level.orders.isEmpty()) {
                    oppositeSide.remove(bestLevel.getKey());
                }
            }
        }
        
        // Add remaining to book
        if (order.getRemainingQuantity() > 0) {
            orders.put(order.orderId, order);
            TreeMap<Long, PriceLevel> side = order.isBuy() ? bids : asks;
            
            PriceLevel level = side.computeIfAbsent(order.price, k -> new PriceLevel(order.price));
            level.orders.add(order);
            level.totalQuantity += order.getRemainingQuantity();
            
            if (order.filledQuantity > 0) {
                order.status = 1; // Partial fill
            }
        } else {
            order.status = 2; // Filled
        }
        
        return trades;
    }
    
    /**
     * Execute market order - match at any price
     */
    private List<Trade> executeMarketOrder(Order order) {
        List<Trade> trades = new ArrayList<>();
        TreeMap<Long, PriceLevel> oppositeSide = order.isBuy() ? asks : bids;
        
        while (order.getRemainingQuantity() > 0 && !oppositeSide.isEmpty()) {
            Map.Entry<Long, PriceLevel> bestLevel = oppositeSide.firstEntry();
            PriceLevel level = bestLevel.getValue();
            Order oppositeOrder = level.orders.peek();
            
            if (oppositeOrder == null) {
                oppositeSide.remove(bestLevel.getKey());
                continue;
            }
            
            int matchQty = Math.min(order.getRemainingQuantity(), oppositeOrder.getRemainingQuantity());
            
            Trade trade = new Trade(
                tradeIdGenerator.getAndIncrement(),
                order.isBuy() ? order.orderId : oppositeOrder.orderId,
                order.isSell() ? order.orderId : oppositeOrder.orderId,
                symbolId,
                oppositeOrder.price,
                matchQty
            );
            trades.add(trade);
            
            order.filledQuantity += matchQty;
            oppositeOrder.filledQuantity += matchQty;
            
            if (oppositeOrder.getRemainingQuantity() == 0) {
                level.orders.poll();
                level.totalQuantity -= oppositeOrder.quantity;
                oppositeOrder.status = 2;
                
                if (level.orders.isEmpty()) {
                    oppositeSide.remove(bestLevel.getKey());
                }
            }
        }
        
        order.status = order.getRemainingQuantity() == 0 ? 2 : 1;
        return trades;
    }
    
    /**
     * Immediate or Cancel - fill what you can, cancel rest
     */
    private List<Trade> executeIOCOrder(Order order) {
        List<Trade> trades = executeLimitOrder(order);
        if (order.getRemainingQuantity() > 0) {
            order.status = 3; // Cancelled
        }
        return trades;
    }
    
    /**
     * Fill or Kill - all or nothing
     */
    private List<Trade> executeFOKOrder(Order order) {
        // Check if full quantity available
        TreeMap<Long, PriceLevel> oppositeSide = order.isBuy() ? asks : bids;
        int availableQty = 0;
        
        for (Map.Entry<Long, PriceLevel> entry : oppositeSide.entrySet()) {
            if (order.isBuy() && entry.getKey() > order.price) break;
            if (order.isSell() && entry.getKey() < order.price) break;
            
            availableQty += entry.getValue().totalQuantity;
            if (availableQty >= order.quantity) break;
        }
        
        if (availableQty >= order.quantity) {
            return executeLimitOrder(order);
        } else {
            order.status = 3; // Cancelled
            return new ArrayList<>();
        }
    }
    
    /**
     * Cancel order
     */
    public synchronized boolean cancelOrder(long orderId) {
        Order order = orders.remove(orderId);
        if (order == null) return false;
        
        TreeMap<Long, PriceLevel> side = order.isBuy() ? bids : asks;
        PriceLevel level = side.get(order.price);
        
        if (level != null) {
            level.orders.remove(order);
            level.totalQuantity -= order.getRemainingQuantity();
            
            if (level.orders.isEmpty()) {
                side.remove(order.price);
            }
        }
        
        order.status = 3; // Cancelled
        return true;
    }
    
    /**
     * Get best bid price
     */
    public long getBestBid() {
        return bids.isEmpty() ? 0 : bids.firstKey();
    }
    
    /**
     * Get best ask price
     */
    public long getBestAsk() {
        return asks.isEmpty() ? 0 : asks.firstKey();
    }
    
    /**
     * Get spread
     */
    public long getSpread() {
        if (bids.isEmpty() || asks.isEmpty()) return 0;
        return getBestAsk() - getBestBid();
    }
    
    /**
     * Get mid price
     */
    public long getMidPrice() {
        if (bids.isEmpty() || asks.isEmpty()) return lastTradePrice;
        return (getBestBid() + getBestAsk()) / 2;
    }
    
    /**
     * Get total bid quantity at top N levels
     */
    public int getBidDepth(int levels) {
        int total = 0;
        int count = 0;
        for (PriceLevel level : bids.values()) {
            if (count++ >= levels) break;
            total += level.totalQuantity;
        }
        return total;
    }
    
    /**
     * Get total ask quantity at top N levels
     */
    public int getAskDepth(int levels) {
        int total = 0;
        int count = 0;
        for (PriceLevel level : asks.values()) {
            if (count++ >= levels) break;
            total += level.totalQuantity;
        }
        return total;
    }
    
    public void printBook(int levels) {
        System.out.println("\n=== Order Book: " + symbolId + " ===");
        
        // Print asks (reversed)
        List<Map.Entry<Long, PriceLevel>> askList = new ArrayList<>(asks.entrySet());
        Collections.reverse(askList);
        
        System.out.println("ASKS:");
        int count = 0;
        for (Map.Entry<Long, PriceLevel> entry : askList) {
            if (count++ >= levels) break;
            System.out.printf("  %.4f | %d\n", 
                entry.getKey() / 10000.0, entry.getValue().totalQuantity);
        }
        
        System.out.println("--------");
        System.out.printf("Spread: %.4f\n", getSpread() / 10000.0);
        System.out.println("--------");
        
        System.out.println("BIDS:");
        count = 0;
        for (Map.Entry<Long, PriceLevel> entry : bids.entrySet()) {
            if (count++ >= levels) break;
            System.out.printf("  %.4f | %d\n", 
                entry.getKey() / 10000.0, entry.getValue().totalQuantity);
        }
        System.out.println();
    }
    
    /**
     * Price level - holds all orders at a specific price
     */
    private static class PriceLevel {
        final long price;
        final Queue<Order> orders = new LinkedList<>();
        int totalQuantity = 0;
        
        PriceLevel(long price) {
            this.price = price;
        }
    }
}