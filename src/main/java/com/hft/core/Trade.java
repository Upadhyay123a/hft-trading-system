package com.hft.core;

/**
 * Represents an executed trade
 */
public class Trade {
    public long tradeId;
    public long buyOrderId;
    public long sellOrderId;
    public int symbolId;
    public long price;
    public int quantity;
    public long timestamp;
    
    public Trade() {}
    
    public Trade(long tradeId, long buyOrderId, long sellOrderId, 
                 int symbolId, long price, int quantity) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbolId = symbolId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = System.nanoTime();
    }
    
    public double getPriceAsDouble() {
        return price / 10000.0;
    }
    
    @Override
    public String toString() {
        return String.format("Trade[id=%d, symbol=%d, price=%.4f, qty=%d, ts=%d]",
            tradeId, symbolId, getPriceAsDouble(), quantity, timestamp);
    }
}