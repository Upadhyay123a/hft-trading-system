package com.hft.core;

/**
 * Represents a single market tick (price update)
 * Using primitives for zero GC overhead
 */
public class Tick {
    public long timestamp;      // Nanoseconds
    public int symbolId;        // Integer ID for fast comparison
    public long price;          // Price * 10000 (4 decimal places)
    public long volume;         // Volume
    public byte side;           // 0 = Buy, 1 = Sell
    
    public Tick() {}
    
    public Tick(long timestamp, int symbolId, long price, long volume, byte side) {
        this.timestamp = timestamp;
        this.symbolId = symbolId;
        this.price = price;
        this.volume = volume;
        this.side = side;
    }
    
    public void reset() {
        this.timestamp = 0;
        this.symbolId = 0;
        this.price = 0;
        this.volume = 0;
        this.side = 0;
    }
    
    public double getPriceAsDouble() {
        return price / 10000.0;
    }
    
    public void setPrice(double priceDouble) {
        this.price = (long)(priceDouble * 10000);
    }
    
    @Override
    public String toString() {
        return String.format("Tick[symbol=%d, price=%.4f, volume=%d, side=%s, ts=%d]",
            symbolId, getPriceAsDouble(), volume, side == 0 ? "BUY" : "SELL", timestamp);
    }
}