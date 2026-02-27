package com.hft.core.binary;

import java.nio.ByteBuffer;

/**
 * High-performance binary encoding protocol for HFT entities
 * Uses Simple Binary Encoding (SBE) principles for zero-copy serialization
 */
public class BinaryProtocol {
    
    // Message types
    public static final byte TICK_MESSAGE = 1;
    public static final byte ORDER_MESSAGE = 2;
    public static final byte TRADE_MESSAGE = 3;
    public static final byte MARKET_DATA_MESSAGE = 4;
    
    // Fixed sizes for each message type (header + payload)
    public static final int TICK_SIZE = 33; // 1 + 8 + 4 + 8 + 8 + 1 + 3 (padding)
    public static final int ORDER_SIZE = 43; // 1 + 8 + 4 + 8 + 4 + 1 + 1 + 8 + 1 + 4 + 4 + 3 (padding)
    public static final int TRADE_SIZE = 49; // 1 + 8 + 4 + 8 + 8 + 8 + 4 + 4 + 8 + 1 + 3 (padding)
    
    /**
     * Encode Tick to binary format (33 bytes)
     * Layout: [type(1)][timestamp(8)][symbolId(4)][price(8)][volume(8)][side(1)][padding(3)]
     */
    public static void encodeTick(ByteBuffer buffer, long timestamp, int symbolId, 
                                  long price, long volume, byte side) {
        buffer.put(TICK_MESSAGE);
        buffer.putLong(timestamp);
        buffer.putInt(symbolId);
        buffer.putLong(price);
        buffer.putLong(volume);
        buffer.put(side);
        buffer.position(buffer.position() + 3); // padding for alignment
    }
    
    /**
     * Decode Tick from binary format
     */
    public static TickData decodeTick(ByteBuffer buffer) {
        byte type = buffer.get();
        if (type != TICK_MESSAGE) {
            throw new IllegalArgumentException("Not a tick message: " + type);
        }
        
        long timestamp = buffer.getLong();
        int symbolId = buffer.getInt();
        long price = buffer.getLong();
        long volume = buffer.getLong();
        byte side = buffer.get();
        buffer.position(buffer.position() + 3); // skip padding
        
        return new TickData(timestamp, symbolId, price, volume, side);
    }
    
    /**
     * Encode Order to binary format (43 bytes)
     * Layout: [type(1)][orderId(8)][symbolId(4)][price(8)][quantity(4)][side(1)]
     *         [type(1)][timestamp(8)][status(1)][filledQty(4)][remainingQty(4)][padding(3)]
     */
    public static void encodeOrder(ByteBuffer buffer, long orderId, int symbolId, 
                                  long price, int quantity, byte side, byte orderType,
                                  long timestamp, byte status, int filledQuantity) {
        buffer.put(ORDER_MESSAGE);
        buffer.putLong(orderId);
        buffer.putInt(symbolId);
        buffer.putLong(price);
        buffer.putInt(quantity);
        buffer.put(side);
        buffer.put(orderType);
        buffer.putLong(timestamp);
        buffer.put(status);
        buffer.putInt(filledQuantity);
        buffer.putInt(quantity - filledQuantity); // remaining quantity
        buffer.position(buffer.position() + 3); // padding
    }
    
    /**
     * Decode Order from binary format
     */
    public static OrderData decodeOrder(ByteBuffer buffer) {
        byte type = buffer.get();
        if (type != ORDER_MESSAGE) {
            throw new IllegalArgumentException("Not an order message: " + type);
        }
        
        long orderId = buffer.getLong();
        int symbolId = buffer.getInt();
        long price = buffer.getLong();
        int quantity = buffer.getInt();
        byte side = buffer.get();
        byte orderType = buffer.get();
        long timestamp = buffer.getLong();
        byte status = buffer.get();
        int filledQuantity = buffer.getInt();
        int remainingQuantity = buffer.getInt();
        buffer.position(buffer.position() + 3); // skip padding
        
        return new OrderData(orderId, symbolId, price, quantity, side, orderType,
                           timestamp, status, filledQuantity, remainingQuantity);
    }
    
    /**
     * Encode Trade to binary format (49 bytes)
     * Layout: [type(1)][tradeId(8)][symbolId(4)][price(8)][quantity(8)]
     *         [buyOrderId(8)][sellOrderId(8)][buyQty(4)][sellQty(4)]
     *         [timestamp(8)][side(1)][padding(3)]
     */
    public static void encodeTrade(ByteBuffer buffer, long tradeId, int symbolId,
                                  long price, long quantity, long buyOrderId, 
                                  long sellOrderId, int buyQuantity, int sellQuantity,
                                  long timestamp, byte side) {
        buffer.put(TRADE_MESSAGE);
        buffer.putLong(tradeId);
        buffer.putInt(symbolId);
        buffer.putLong(price);
        buffer.putLong(quantity);
        buffer.putLong(buyOrderId);
        buffer.putLong(sellOrderId);
        buffer.putInt(buyQuantity);
        buffer.putInt(sellQuantity);
        buffer.putLong(timestamp);
        buffer.put(side);
        buffer.position(buffer.position() + 3); // padding
    }
    
    /**
     * Decode Trade from binary format
     */
    public static TradeData decodeTrade(ByteBuffer buffer) {
        byte type = buffer.get();
        if (type != TRADE_MESSAGE) {
            throw new IllegalArgumentException("Not a trade message: " + type);
        }
        
        long tradeId = buffer.getLong();
        int symbolId = buffer.getInt();
        long price = buffer.getLong();
        long quantity = buffer.getLong();
        long buyOrderId = buffer.getLong();
        long sellOrderId = buffer.getLong();
        int buyQuantity = buffer.getInt();
        int sellQuantity = buffer.getInt();
        long timestamp = buffer.getLong();
        byte side = buffer.get();
        buffer.position(buffer.position() + 3); // skip padding
        
        return new TradeData(tradeId, symbolId, price, quantity, buyOrderId, 
                           sellOrderId, buyQuantity, sellQuantity, timestamp, side);
    }
    
    /**
     * Calculate message size from type
     */
    public static int getMessageSize(byte messageType) {
        switch (messageType) {
            case TICK_MESSAGE: return TICK_SIZE;
            case ORDER_MESSAGE: return ORDER_SIZE;
            case TRADE_MESSAGE: return TRADE_SIZE;
            default: throw new IllegalArgumentException("Unknown message type: " + messageType);
        }
    }
    
    // Data holder classes for zero-copy operations
    public static class TickData {
        public final long timestamp;
        public final int symbolId;
        public final long price;
        public final long volume;
        public final byte side;
        
        public TickData(long timestamp, int symbolId, long price, long volume, byte side) {
            this.timestamp = timestamp;
            this.symbolId = symbolId;
            this.price = price;
            this.volume = volume;
            this.side = side;
        }
    }
    
    public static class OrderData {
        public final long orderId;
        public final int symbolId;
        public final long price;
        public final int quantity;
        public final byte side;
        public final byte orderType;
        public final long timestamp;
        public final byte status;
        public final int filledQuantity;
        public final int remainingQuantity;
        
        public OrderData(long orderId, int symbolId, long price, int quantity, byte side, 
                        byte orderType, long timestamp, byte status, int filledQuantity,
                        int remainingQuantity) {
            this.orderId = orderId;
            this.symbolId = symbolId;
            this.price = price;
            this.quantity = quantity;
            this.side = side;
            this.orderType = orderType;
            this.timestamp = timestamp;
            this.status = status;
            this.filledQuantity = filledQuantity;
            this.remainingQuantity = remainingQuantity;
        }
    }
    
    public static class TradeData {
        public final long tradeId;
        public final int symbolId;
        public final long price;
        public final long quantity;
        public final long buyOrderId;
        public final long sellOrderId;
        public final int buyQuantity;
        public final int sellQuantity;
        public final long timestamp;
        public final byte side;
        
        public TradeData(long tradeId, int symbolId, long price, long quantity,
                        long buyOrderId, long sellOrderId, int buyQuantity, 
                        int sellQuantity, long timestamp, byte side) {
            this.tradeId = tradeId;
            this.symbolId = symbolId;
            this.price = price;
            this.quantity = quantity;
            this.buyOrderId = buyOrderId;
            this.sellOrderId = sellOrderId;
            this.buyQuantity = buyQuantity;
            this.sellQuantity = sellQuantity;
            this.timestamp = timestamp;
            this.side = side;
        }
    }
}
