package com.hft.orderbook;

import com.hft.core.Order;
import com.hft.core.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class OptimizedOrderBook extends OrderBook {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedOrderBook.class);
    
    private static final long BASE_PRICE = 100000000L;
    private static final long MAX_PRICE = 1000000000L;  // $10,000 max
    private static final long MIN_PRICE = 50000L;
    private static final int PRICE_TICK = 100;
    private static final int PRICE_LEVELS = (int)(((MAX_PRICE - MIN_PRICE) / PRICE_TICK) + 1);
    
    private final PriceLevel[] bidLevels = new PriceLevel[PRICE_LEVELS];
    private final PriceLevel[] askLevels = new PriceLevel[PRICE_LEVELS];
    private final HashMap<Long, Order> orderMap = new HashMap<>();
    
    private long bestBid = 0;
    private long bestAsk = Long.MAX_VALUE;
    
    private final AtomicLong orderCount = new AtomicLong(0);
    private final AtomicLong tradeCount = new AtomicLong(0);
    
    public OptimizedOrderBook(int symbolId) {
        super(symbolId);
        initializePriceLevels();
        logger.info("OptimizedOrderBook initialized for symbol {} with {} price levels", symbolId, PRICE_LEVELS);
    }
    
    private void initializePriceLevels() {
        for (int i = 0; i < PRICE_LEVELS; i++) {
            bidLevels[i] = new PriceLevel();
            askLevels[i] = new PriceLevel();
        }
    }
    
    private int priceToIndex(long price) {
        if (price < MIN_PRICE || price > MAX_PRICE) return -1;
        return (int)((price - MIN_PRICE) / PRICE_TICK);
    }
    
    private long indexToPrice(int index) {
        return MIN_PRICE + ((long)index * PRICE_TICK);
    }
    
    public List<Trade> addOrder(Order order) {
        List<Trade> trades = new ArrayList<>();
        
        int index = priceToIndex(order.price);
        if (index == -1) {
            logger.warn("Price {} out of range for symbol {}", order.price, symbolId);
            return trades;
        }
        
        if (order.isBuy()) {
            if (bestAsk != Long.MAX_VALUE && order.price >= bestAsk) {
                trades = executeLimitBuy(order);
                if (order.getRemainingQuantity() == 0) return trades;
            }
            // FIX: Also try to match if there are sell orders at this exact price level
            int askIndex = priceToIndex(order.price);
            if (askIndex >= 0 && askIndex < PRICE_LEVELS && askLevels[askIndex].getTotalQuantity() > 0) {
                List<Trade> additionalTrades = executeLimitBuy(order);
                trades.addAll(additionalTrades);
                if (order.getRemainingQuantity() == 0) return trades;
            }
        } else {
            if (bestBid != 0 && order.price <= bestBid) {
                trades = executeLimitSell(order);
                if (order.getRemainingQuantity() == 0) return trades;
            }
            // FIX: Also try to match if there are buy orders at this exact price level
            int bidIndex = priceToIndex(order.price);
            if (bidIndex >= 0 && bidIndex < PRICE_LEVELS && bidLevels[bidIndex].getTotalQuantity() > 0) {
                List<Trade> additionalTrades = executeLimitSell(order);
                trades.addAll(additionalTrades);
                if (order.getRemainingQuantity() == 0) return trades;
            }
        }
        
        if (order.getRemainingQuantity() > 0) {
            PriceLevel level = order.isBuy() ? bidLevels[index] : askLevels[index];
            level.addOrder(order);
            orderMap.put(order.orderId, order);
            updateBestPrices(order);
            orderCount.incrementAndGet();
        }
        
        return trades;
    }
    
    public boolean cancelOrder(long orderId) {
        Order order = orderMap.get(orderId);
        if (order == null) return false;
        
        int index = priceToIndex(order.price);
        if (index == -1) return false;
        
        PriceLevel level = order.isBuy() ? bidLevels[index] : askLevels[index];
        level.removeOrder(order);
        orderMap.remove(orderId);
        updateBestPricesAfterCancel(order);
        return true;
    }
    
    public long getMidPrice() {
        if (bestBid == 0 || bestAsk == Long.MAX_VALUE) return 0;
        return (bestBid + bestAsk) / 2;
    }
    
    public long getSpread() {
        if (bestBid == 0 || bestAsk == Long.MAX_VALUE) return 0;
        return bestAsk - bestBid;
    }
    
    public long getBestBid() { return bestBid; }
    public long getBestAsk() { return bestAsk == Long.MAX_VALUE ? 0 : bestAsk; }
    
    public List<Trade> executeMarketOrder(Order marketOrder) {
        return marketOrder.isBuy() ? executeMarketBuy(marketOrder) : executeMarketSell(marketOrder);
    }
    
    private List<Trade> executeMarketBuy(Order marketOrder) {
        List<Trade> trades = new ArrayList<>();
        if (bestAsk == Long.MAX_VALUE) return trades;
        
        for (int i = priceToIndex(bestAsk); i < PRICE_LEVELS && marketOrder.getRemainingQuantity() > 0; i++) {
            PriceLevel level = askLevels[i];
            if (level.getTotalQuantity() == 0) continue;
            
            List<Order> ordersAtLevel = new ArrayList<>(level.getOrders());
            for (Order order : ordersAtLevel) {
                if (marketOrder.getRemainingQuantity() <= 0) break;
                long tradeQuantity = Math.min(marketOrder.getRemainingQuantity(), order.getRemainingQuantity());
                if (tradeQuantity > 0) {
                    trades.add(createTrade(order, marketOrder, tradeQuantity, indexToPrice(i)));
                    order.fill(tradeQuantity);
                    marketOrder.fill(tradeQuantity);
                    if (order.getRemainingQuantity() == 0) orderMap.remove(order.orderId);
                }
            }
            level.removeEmptyOrders();
        }
        updateBestAsk();
        return trades;
    }
    
    private List<Trade> executeLimitBuy(Order limitOrder) {
        List<Trade> trades = new ArrayList<>();
        
        for (int i = priceToIndex(bestAsk); i < PRICE_LEVELS && limitOrder.getRemainingQuantity() > 0; i++) {
            long currentPrice = indexToPrice(i);
            if (currentPrice > limitOrder.price) break;
            
            PriceLevel level = askLevels[i];
            if (level.getTotalQuantity() == 0) continue;
            
            List<Order> ordersAtLevel = new ArrayList<>(level.getOrders());
            for (Order order : ordersAtLevel) {
                if (limitOrder.getRemainingQuantity() <= 0) break;
                long tradeQuantity = Math.min(limitOrder.getRemainingQuantity(), order.getRemainingQuantity());
                if (tradeQuantity > 0) {
                    trades.add(createTrade(order, limitOrder, tradeQuantity, currentPrice));
                    order.fill(tradeQuantity);
                    limitOrder.fill(tradeQuantity);
                    if (order.getRemainingQuantity() == 0) orderMap.remove(order.orderId);
                }
            }
            level.removeEmptyOrders();
        }
        updateBestAsk();
        return trades;
    }
    
    private List<Trade> executeLimitSell(Order limitOrder) {
        List<Trade> trades = new ArrayList<>();
        
        for (int i = priceToIndex(bestBid); i >= 0 && limitOrder.getRemainingQuantity() > 0; i--) {
            long currentPrice = indexToPrice(i);
            if (currentPrice < limitOrder.price) break;
            
            PriceLevel level = bidLevels[i];
            if (level.getTotalQuantity() == 0) continue;
            
            List<Order> ordersAtLevel = new ArrayList<>(level.getOrders());
            for (Order order : ordersAtLevel) {
                if (limitOrder.getRemainingQuantity() <= 0) break;
                long tradeQuantity = Math.min(limitOrder.getRemainingQuantity(), order.getRemainingQuantity());
                if (tradeQuantity > 0) {
                    trades.add(createTrade(order, limitOrder, tradeQuantity, currentPrice));
                    order.fill(tradeQuantity);
                    limitOrder.fill(tradeQuantity);
                    if (order.getRemainingQuantity() == 0) orderMap.remove(order.orderId);
                }
            }
            level.removeEmptyOrders();
        }
        updateBestBid();
        return trades;
    }
    
    private List<Trade> executeMarketSell(Order marketOrder) {
        List<Trade> trades = new ArrayList<>();
        if (bestBid == 0) return trades;
        
        for (int i = priceToIndex(bestBid); i >= 0 && marketOrder.getRemainingQuantity() > 0; i--) {
            PriceLevel level = bidLevels[i];
            if (level.getTotalQuantity() == 0) continue;
            
            List<Order> ordersAtLevel = new ArrayList<>(level.getOrders());
            for (Order order : ordersAtLevel) {
                if (marketOrder.getRemainingQuantity() <= 0) break;
                long tradeQuantity = Math.min(marketOrder.getRemainingQuantity(), order.getRemainingQuantity());
                if (tradeQuantity > 0) {
                    trades.add(createTrade(order, marketOrder, tradeQuantity, indexToPrice(i)));
                    order.fill(tradeQuantity);
                    marketOrder.fill(tradeQuantity);
                    if (order.getRemainingQuantity() == 0) orderMap.remove(order.orderId);
                }
            }
            level.removeEmptyOrders();
        }
        updateBestBid();
        return trades;
    }
    
    private Trade createTrade(Order restingOrder, Order aggressorOrder, long quantity, long price) {
        Trade trade = new Trade();
        trade.tradeId = tradeCount.incrementAndGet();
        trade.symbolId = symbolId;
        trade.price = price;
        trade.quantity = (int) quantity;
        if (aggressorOrder.isBuy()) {
            trade.buyOrderId = aggressorOrder.orderId;
            trade.sellOrderId = restingOrder.orderId;
        } else {
            trade.buyOrderId = restingOrder.orderId;
            trade.sellOrderId = aggressorOrder.orderId;
        }
        return trade;
    }
    
    private void updateBestPrices(Order order) {
        if (order.isBuy()) {
            if (order.price > bestBid) bestBid = order.price;
        } else {
            if (order.price < bestAsk) bestAsk = order.price;
        }
    }
    
    private void updateBestPricesAfterCancel(Order cancelledOrder) {
        if (cancelledOrder.isBuy() && cancelledOrder.price == bestBid) updateBestBid();
        else if (!cancelledOrder.isBuy() && cancelledOrder.price == bestAsk) updateBestAsk();
    }
    
    // BUG FIX: capture startIdx BEFORE resetting bestBid to 0.
    // Old code: bestBid=0 first, then priceToIndex(bestBid)==priceToIndex(0)==-1 → loop never ran.
    private void updateBestBid() {
        int startIdx = (bestBid > 0) ? priceToIndex(bestBid) : (PRICE_LEVELS - 1);
        bestBid = 0;
        for (int i = startIdx; i >= 0; i--) {
            if (bidLevels[i].getTotalQuantity() > 0) {
                bestBid = indexToPrice(i);
                break;
            }
        }
    }
    
    // BUG FIX: capture startIdx BEFORE resetting bestAsk to Long.MAX_VALUE.
    // Old code: bestAsk=MAX_VALUE first, then priceToIndex(MAX_VALUE)==-1 → loop never ran.
    private void updateBestAsk() {
        int startIdx = (bestAsk != Long.MAX_VALUE) ? priceToIndex(bestAsk) : 0;
        bestAsk = Long.MAX_VALUE;
        for (int i = startIdx; i < PRICE_LEVELS; i++) {
            if (askLevels[i].getTotalQuantity() > 0) {
                bestAsk = indexToPrice(i);
                break;
            }
        }
    }
    
    public String getBookState() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Optimized Order Book: ").append(symbolId).append(" ===\n");
        
        int askStart = (bestAsk != Long.MAX_VALUE) ? priceToIndex(bestAsk) : 0;
        sb.append("ASKS:\n");
        for (int i = askStart; i < Math.min(askStart + 10, PRICE_LEVELS); i++) {
            if (i >= 0 && askLevels[i].getTotalQuantity() > 0)
                sb.append(String.format("Price: %.4f, Qty: %d\n", indexToPrice(i) / 10000.0, askLevels[i].getTotalQuantity()));
        }
        
        sb.append(String.format("Spread: %.4f\n", getSpread() / 10000.0));
        
        int bidStart = (bestBid > 0) ? priceToIndex(bestBid) : 0;
        sb.append("BIDS:\n");
        for (int i = bidStart; i >= Math.max(bidStart - 10, 0); i--) {
            if (bidLevels[i].getTotalQuantity() > 0)
                sb.append(String.format("Price: %.4f, Qty: %d\n", indexToPrice(i) / 10000.0, bidLevels[i].getTotalQuantity()));
        }
        
        return sb.toString();
    }
    
    public long getOrderCount() { return orderCount.get(); }
    public long getTradeCount() { return tradeCount.get(); }
    
    private static class PriceLevel {
        private final List<Order> orders = new ArrayList<>();
        
        public void addOrder(Order order)    { orders.add(order); }
        public void removeOrder(Order order) { orders.remove(order); }
        public void removeEmptyOrders()      { orders.removeIf(o -> o.getRemainingQuantity() == 0); }
        public List<Order> getOrders()       { return new ArrayList<>(orders); }
        public long getTotalQuantity()       { return orders.stream().mapToLong(Order::getRemainingQuantity).sum(); }
    }
}