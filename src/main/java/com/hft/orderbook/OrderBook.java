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
    protected final int symbolId;
    
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
     * Core matching loop — shared by limit, IOC, FOK, and market orders.
     * Matches `order` against `oppositeSide` respecting `priceLimit`:
     *   buy  orders: match while best ask <= priceLimit (pass Long.MAX_VALUE for market)
     *   sell orders: match while best bid >= priceLimit (pass Long.MIN_VALUE for market)
     */
    private List<Trade> matchAgainst(Order order, TreeMap<Long, PriceLevel> oppositeSide, long priceLimit) {
        List<Trade> trades = new ArrayList<>();

        while (order.getRemainingQuantity() > 0 && !oppositeSide.isEmpty()) {
            Map.Entry<Long, PriceLevel> bestEntry = oppositeSide.firstEntry();

            // Price boundary check
            if (order.isBuy()  && bestEntry.getKey() > priceLimit) break;
            if (order.isSell() && bestEntry.getKey() < priceLimit) break;

            PriceLevel level = bestEntry.getValue();
            Order oppositeOrder = level.orders.peek();

            if (oppositeOrder == null) {
                oppositeSide.remove(bestEntry.getKey());
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

            // FIX 1: use Order.fill() instead of manually mutating filledQuantity.
            // fill() caps filledQuantity at quantity and sets status (PartialFill /
            // Filled) atomically, preventing filledQuantity ever exceeding quantity
            // and keeping all status transitions in one place.
            order.fill(matchQty);
            oppositeOrder.fill(matchQty);

            lastTradePrice = oppositeOrder.price;
            lastTradeQuantity = matchQty;

            // FIX 2: decrement totalQuantity by matchQty on every match.
            // Previously this only ran inside the "fully filled" block and used
            // oppositeOrder.quantity — partial fills left totalQuantity stale,
            // breaking depth queries and FOK availability checks.
            level.totalQuantity -= matchQty;

            if (oppositeOrder.getRemainingQuantity() == 0) {
                level.orders.poll();
                orders.remove(oppositeOrder.orderId); // keep lookup map in sync
                if (level.orders.isEmpty()) {
                    oppositeSide.remove(bestEntry.getKey());
                }
            }
        }

        return trades;
    }

    /**
     * Execute limit order - match or add to book
     */
    private List<Trade> executeLimitOrder(Order order) {
        TreeMap<Long, PriceLevel> oppositeSide = order.isBuy() ? asks : bids;
        List<Trade> trades = matchAgainst(order, oppositeSide, order.price);

        // Add unfilled remainder to the resting book
        if (order.getRemainingQuantity() > 0) {
            orders.put(order.orderId, order);
            TreeMap<Long, PriceLevel> side = order.isBuy() ? bids : asks;
            PriceLevel level = side.computeIfAbsent(order.price, k -> new PriceLevel(order.price));
            level.orders.add(order);
            level.totalQuantity += order.getRemainingQuantity();
            // status already set correctly by fill() — no manual override needed
        }

        return trades;
    }
    
    /**
     * Execute market order - match at any price
     */
    private List<Trade> executeMarketOrder(Order order) {
        TreeMap<Long, PriceLevel> oppositeSide = order.isBuy() ? asks : bids;
        long priceLimit = order.isBuy() ? Long.MAX_VALUE : Long.MIN_VALUE;
        List<Trade> trades = matchAgainst(order, oppositeSide, priceLimit);

        // Market orders are never rested; cancel any unmatched remainder
        if (order.getRemainingQuantity() > 0 && order.status != 2) {
            order.status = (byte)(order.filledQuantity > 0 ? 1 : 3);
        }

        return trades;
    }
    
    /**
     * Immediate or Cancel - fill what you can, cancel rest
     */
    private List<Trade> executeIOCOrder(Order order) {
        List<Trade> trades = executeLimitOrder(order);
        if (order.getRemainingQuantity() > 0) {
            cancelOrder(order.orderId); // removes from resting book if it was added
            order.status = 3;           // Cancelled
        }
        return trades;
    }
    
    /**
     * Fill or Kill - all or nothing
     */
    private List<Trade> executeFOKOrder(Order order) {
        TreeMap<Long, PriceLevel> oppositeSide = order.isBuy() ? asks : bids;
        int availableQty = 0;
        
        for (Map.Entry<Long, PriceLevel> entry : oppositeSide.entrySet()) {
            if (order.isBuy()  && entry.getKey() > order.price) break;
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
     * Get mid price.
     * FIX 3: original returned 0 whenever one side was empty and no trades had
     * occurred. Now falls back to the available side's best price first, so
     * callers get a meaningful value on a one-sided book.
     */
    public long getMidPrice() {
        if (!bids.isEmpty() && !asks.isEmpty()) {
            return (getBestBid() + getBestAsk()) / 2;
        }
        if (!bids.isEmpty()) {
            return lastTradePrice != 0 ? lastTradePrice : getBestBid();
        }
        if (!asks.isEmpty()) {
            return lastTradePrice != 0 ? lastTradePrice : getBestAsk();
        }
        return lastTradePrice; // 0 if no trades yet — caller must handle
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