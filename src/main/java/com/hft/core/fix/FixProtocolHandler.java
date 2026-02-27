package com.hft.core.fix;

import com.hft.core.binary.BinaryProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FIX Protocol handler for order entity search and external API integration
 * Implements high-performance FIX over binary encoding for HFT systems
 */
public class FixProtocolHandler {
    private static final Logger logger = LoggerFactory.getLogger(FixProtocolHandler.class);
    
    // FIX message types
    public static final String NEW_ORDER_SINGLE = "D";
    public static final String ORDER_CANCEL_REQUEST = "F";
    public static final String ORDER_STATUS_REQUEST = "H";
    public static final String EXECUTION_REPORT = "8";
    public static final String MARKET_DATA_REQUEST = "V";
    public static final String MARKET_DATA_SNAPSHOT = "W";
    
    // FIX tags
    public static final int MSG_TYPE = 35;
    public static final int CL_ORD_ID = 11;
    public static final int ORDER_ID = 37;
    public static final int SYMBOL = 55;
    public static final int SIDE = 54;
    public static final int ORDER_QTY = 38;
    public static final int PRICE = 44;
    public static final int ORD_TYPE = 40;
    public static final int TIME_IN_FORCE = 59;
    public static final int EXEC_TYPE = 150;
    public static final int ORD_STATUS = 39;
    public static final int LEAVES_QTY = 151;
    public static final int CUM_QTY = 14;
    public static final int AVG_PX = 6;
    
    // FIX session data
    private final String senderCompId;
    private final String targetCompId;
    private final AtomicLong sequenceNumber = new AtomicLong(1);
    public final Map<String, FixOrder> orders = new ConcurrentHashMap<>();
    
    // Performance optimization
    private final ByteBuffer fixBuffer = ByteBuffer.allocate(1024);
    private final Map<Integer, String> tagCache = new HashMap<>();
    
    public FixProtocolHandler(String senderCompId, String targetCompId) {
        this.senderCompId = senderCompId;
        this.targetCompId = targetCompId;
        
        // Initialize tag cache for performance
        initializeTagCache();
        
        logger.info("FIX Protocol Handler initialized: {} -> {}", senderCompId, targetCompId);
    }
    
    /**
     * Initialize FIX tag cache for performance
     */
    private void initializeTagCache() {
        tagCache.put(MSG_TYPE, "35");
        tagCache.put(CL_ORD_ID, "11");
        tagCache.put(ORDER_ID, "37");
        tagCache.put(SYMBOL, "55");
        tagCache.put(SIDE, "54");
        tagCache.put(ORDER_QTY, "38");
        tagCache.put(PRICE, "44");
        tagCache.put(ORD_TYPE, "40");
        tagCache.put(TIME_IN_FORCE, "59");
        tagCache.put(EXEC_TYPE, "150");
        tagCache.put(ORD_STATUS, "39");
        tagCache.put(LEAVES_QTY, "151");
        tagCache.put(CUM_QTY, "14");
        tagCache.put(AVG_PX, "6");
    }
    
    /**
     * Parse FIX message from binary data
     */
    public FixMessage parseFixMessage(byte[] data) {
        String fixString = new String(data, StandardCharsets.US_ASCII);
        return parseFixString(fixString);
    }
    
    /**
     * Parse FIX message from string
     */
    public FixMessage parseFixString(String fixString) {
        FixMessage message = new FixMessage();
        String[] fields = fixString.split("\\|");
        
        for (String field : fields) {
            String[] keyValue = field.split("=");
            if (keyValue.length == 2) {
                try {
                    int tag = Integer.parseInt(keyValue[0]);
                    String value = keyValue[1];
                    message.addField(tag, value);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid FIX field: {}", field);
                }
            }
        }
        
        // Validate checksum
        validateChecksum(message);
        
        return message;
    }
    
    /**
     * Create FIX message for new order
     */
    public byte[] createNewOrderSingle(String clOrdId, String symbol, char side, 
                                       double quantity, double price, char ordType) {
        FixMessage message = new FixMessage();
        
        // Header
        message.addField(49, senderCompId); // SenderCompID
        message.addField(56, targetCompId); // TargetCompID
        message.addField(MSG_TYPE, NEW_ORDER_SINGLE);
        
        // Body
        message.addField(CL_ORD_ID, clOrdId);
        message.addField(SYMBOL, symbol);
        message.addField(SIDE, String.valueOf(side));
        message.addField(ORDER_QTY, String.valueOf(quantity));
        message.addField(PRICE, String.valueOf(price));
        message.addField(ORD_TYPE, String.valueOf(ordType));
        message.addField(TIME_IN_FORCE, "0"); // Day
        
        // Create order record
        FixOrder order = new FixOrder(clOrdId, symbol, side, quantity, price, ordType);
        orders.put(clOrdId, order);
        
        return serializeFixMessage(message);
    }
    
    /**
     * Create FIX execution report
     */
    public byte[] createExecutionReport(String orderId, String clOrdId, String symbol, 
                                       char execType, char ordStatus, double leavesQty, 
                                       double cumQty, double avgPx) {
        FixMessage message = new FixMessage();
        
        // Header
        message.addField(49, senderCompId);
        message.addField(56, targetCompId);
        message.addField(MSG_TYPE, EXECUTION_REPORT);
        
        // Body
        message.addField(ORDER_ID, orderId);
        message.addField(CL_ORD_ID, clOrdId);
        message.addField(SYMBOL, symbol);
        message.addField(EXEC_TYPE, String.valueOf(execType));
        message.addField(ORD_STATUS, String.valueOf(ordStatus));
        message.addField(LEAVES_QTY, String.valueOf(leavesQty));
        message.addField(CUM_QTY, String.valueOf(cumQty));
        message.addField(AVG_PX, String.valueOf(avgPx));
        
        return serializeFixMessage(message);
    }
    
    /**
     * Create FIX market data request
     */
    public byte[] createMarketDataRequest(String reqId, String symbol, int subscriptionType) {
        FixMessage message = new FixMessage();
        
        // Header
        message.addField(49, senderCompId);
        message.addField(56, targetCompId);
        message.addField(MSG_TYPE, MARKET_DATA_REQUEST);
        
        // Body
        message.addField(262, reqId); // MDReqID
        message.addField(263, String.valueOf(subscriptionType)); // SubscriptionRequestType
        message.addField(264, "0"); // MarketDepth
        message.addField(265, "1"); // MDUpdateType
        
        // Symbol
        message.addField(SYMBOL, symbol);
        
        return serializeFixMessage(message);
    }
    
    /**
     * Search orders by criteria
     */
    public FixOrder[] searchOrders(String symbol, char side, char status) {
        return orders.values().stream()
            .filter(order -> (symbol == null || order.symbol.equals(symbol)))
            .filter(order -> (side == '\0' || order.side == side))
            .filter(order -> (status == '\0' || order.status == status))
            .toArray(FixOrder[]::new);
    }
    
    /**
     * Get order by client order ID
     */
    public FixOrder getOrder(String clOrdId) {
        return orders.get(clOrdId);
    }
    
    /**
     * Update order status
     */
    public void updateOrderStatus(String clOrdId, char status, double filledQty, double avgPx) {
        FixOrder order = orders.get(clOrdId);
        if (order != null) {
            order.status = status;
            order.filledQuantity = filledQty;
            order.averagePrice = avgPx;
        }
    }
    
    /**
     * Serialize FIX message to binary
     */
    private byte[] serializeFixMessage(FixMessage message) {
        fixBuffer.clear();
        
        // Add header fields
        addToBuffer(fixBuffer, 8, "FIX.4.4"); // BeginString
        addToBuffer(fixBuffer, 9, String.valueOf(System.currentTimeMillis())); // SendingTime
        addToBuffer(fixBuffer, 49, senderCompId);
        addToBuffer(fixBuffer, 56, targetCompId);
        addToBuffer(fixBuffer, 34, String.valueOf(sequenceNumber.getAndIncrement()));
        
        // Add body fields
        for (Map.Entry<Integer, String> entry : message.fields.entrySet()) {
            addToBuffer(fixBuffer, entry.getKey(), entry.getValue());
        }
        
        // Calculate and add checksum
        String checksum = calculateChecksum(fixBuffer.array(), fixBuffer.position());
        addToBuffer(fixBuffer, 10, checksum);
        
        // Convert to byte array
        byte[] result = new byte[fixBuffer.position()];
        System.arraycopy(fixBuffer.array(), 0, result, 0, result.length);
        
        return result;
    }
    
    /**
     * Add field to buffer
     */
    private void addToBuffer(ByteBuffer buffer, int tag, String value) {
        String field = tag + "=" + value + "|";
        byte[] fieldBytes = field.getBytes(StandardCharsets.US_ASCII);
        buffer.put(fieldBytes);
    }
    
    /**
     * Calculate FIX checksum
     */
    private String calculateChecksum(byte[] data, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += data[i] & 0xFF;
        }
        return String.format("%03d", sum % 256);
    }
    
    /**
     * Validate FIX message checksum
     */
    private void validateChecksum(FixMessage message) {
        // Implementation for checksum validation
        // This would verify the checksum field (tag 10) matches calculated checksum
    }
    
    /**
     * Convert binary protocol data to FIX message
     */
    public byte[] convertBinaryToFix(byte[] binaryData) {
        ByteBuffer buffer = ByteBuffer.wrap(binaryData);
        byte messageType = buffer.get();
        
        switch (messageType) {
            case BinaryProtocol.ORDER_MESSAGE:
                return convertOrderToFix(buffer);
            case BinaryProtocol.TRADE_MESSAGE:
                return convertTradeToFix(buffer);
            case BinaryProtocol.TICK_MESSAGE:
                return convertTickToFix(buffer);
            default:
                logger.warn("Unknown binary message type: {}", messageType);
                return null;
        }
    }
    
    /**
     * Convert binary order to FIX
     */
    private byte[] convertOrderToFix(ByteBuffer buffer) {
        BinaryProtocol.OrderData orderData = BinaryProtocol.decodeOrder(buffer);
        
        return createExecutionReport(
            String.valueOf(orderData.orderId),
            String.valueOf(orderData.orderId),
            String.valueOf(orderData.symbolId),
            getFixExecType(orderData.status),
            getFixOrdStatus(orderData.status),
            orderData.remainingQuantity,
            orderData.filledQuantity,
            orderData.price / 10000.0
        );
    }
    
    /**
     * Convert binary trade to FIX
     */
    private byte[] convertTradeToFix(ByteBuffer buffer) {
        BinaryProtocol.TradeData tradeData = BinaryProtocol.decodeTrade(buffer);
        
        return createExecutionReport(
            String.valueOf(tradeData.tradeId),
            String.valueOf(tradeData.buyOrderId),
            String.valueOf(tradeData.symbolId),
            'F', // Fill
            '2', // Filled
            0,
            tradeData.quantity,
            tradeData.price / 10000.0
        );
    }
    
    /**
     * Convert binary tick to FIX market data
     */
    private byte[] convertTickToFix(ByteBuffer buffer) {
        BinaryProtocol.TickData tickData = BinaryProtocol.decodeTick(buffer);
        
        FixMessage message = new FixMessage();
        message.addField(49, senderCompId);
        message.addField(56, targetCompId);
        message.addField(MSG_TYPE, MARKET_DATA_SNAPSHOT);
        message.addField(SYMBOL, String.valueOf(tickData.symbolId));
        message.addField(269, "0"); // MDEntryType = Bid
        message.addField(270, String.valueOf(tickData.price / 10000.0)); // MDEntryPx
        message.addField(271, String.valueOf(tickData.volume)); // MDEntrySize
        
        return serializeFixMessage(message);
    }
    
    /**
     * Convert binary status to FIX execution type
     */
    public char getFixExecType(byte status) {
        switch (status) {
            case 0: return '0'; // New
            case 1: return '1'; // Partial fill
            case 2: return 'F'; // Fill
            case 3: return 'C'; // Cancelled
            default: return '0';
        }
    }
    
    /**
     * Convert binary status to FIX order status
     */
    public char getFixOrdStatus(byte status) {
        switch (status) {
            case 0: return 'A'; // New
            case 1: return '1'; // Partially filled
            case 2: return '2'; // Filled
            case 3: return '4'; // Cancelled
            default: return 'A';
        }
    }
    
    /**
     * FIX message container
     */
    public static class FixMessage {
        private final Map<Integer, String> fields = new HashMap<>();
        
        public void addField(int tag, String value) {
            fields.put(tag, value);
        }
        
        public String getField(int tag) {
            return fields.get(tag);
        }
        
        public String getMsgType() {
            return getField(MSG_TYPE);
        }
        
        public Map<Integer, String> getFields() {
            return fields;
        }
    }
    
    /**
     * FIX order container
     */
    public static class FixOrder {
        public final String clOrdId;
        public final String symbol;
        public final char side;
        public final double quantity;
        public final double price;
        public final char ordType;
        public char status = 'A'; // New
        public double filledQuantity = 0;
        public double averagePrice = 0;
        public long timestamp = System.currentTimeMillis();
        
        public FixOrder(String clOrdId, String symbol, char side, double quantity, 
                       double price, char ordType) {
            this.clOrdId = clOrdId;
            this.symbol = symbol;
            this.side = side;
            this.quantity = quantity;
            this.price = price;
            this.ordType = ordType;
        }
    }
}
