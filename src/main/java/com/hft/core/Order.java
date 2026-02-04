package com.hft.core;

/**
 * Represents a trading order
 */
public class Order {
    public long orderId;
    public int symbolId;
    public long price;          // Price * 10000
    public int quantity;
    public byte side;           // 0 = Buy, 1 = Sell
    public byte type;           // 0 = Limit, 1 = Market, 2 = IOC, 3 = FOK
    public long timestamp;
    
    // Order status
    public byte status;         // 0 = New, 1 = PartialFill, 2 = Filled, 3 = Cancelled
    public int filledQuantity;
    
    public Order() {}
    
    public Order(long orderId, int symbolId, long price, int quantity, byte side, byte type) {
        this.orderId = orderId;
        this.symbolId = symbolId;
        this.price = price;
        this.quantity = quantity;
        this.side = side;
        this.type = type;
        this.timestamp = System.nanoTime();
        this.status = 0; // New
        this.filledQuantity = 0;
    }
    
    public double getPriceAsDouble() {
        return price / 10000.0;
    }
    
    public boolean isBuy() {
        return side == 0;
    }
    
    public boolean isSell() {
        return side == 1;
    }
    
    public int getRemainingQuantity() {
        return quantity - filledQuantity;
    }
    
    @Override
    public String toString() {
        return String.format("Order[id=%d, symbol=%d, %s, price=%.4f, qty=%d/%d, status=%d]",
            orderId, symbolId, isBuy() ? "BUY" : "SELL", getPriceAsDouble(), 
            filledQuantity, quantity, status);
    }
}