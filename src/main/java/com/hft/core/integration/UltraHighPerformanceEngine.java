package com.hft.core.integration;

import com.hft.core.Tick;
import com.hft.core.Order;
import com.hft.core.Trade;
import com.hft.core.binary.BinaryProtocol;
import com.hft.core.disruptor.DisruptorEngine;
import com.hft.core.aeron.AeronMarketDataFeed;
import com.hft.core.fix.FixProtocolHandler;
import com.hft.orderbook.OrderBook;
import com.ft.risk.RiskManager;
import com.hft.strategy.Strategy;
import com.hft.monitoring.PerformanceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultra-High Performance Trading Engine integrating Binary Encoding + LMAX Disruptor + Aeron + FIX
 * This is the main entry point for the next-generation HFT system
 */
public class UltraHighPerformanceEngine {
    private static final Logger logger = LoggerFactory.getLogger(UltraHighPerformanceEngine.class);
    
    // Core components
    private final DisruptorEngine disruptorEngine;
    private final AeronMarketDataFeed aeronFeed;
    private final FixProtocolHandler fixHandler;
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final PerformanceMonitor performanceMonitor;
    
    // Performance tracking
    private final AtomicLong ticksProcessed = new AtomicLong(0);
    private final AtomicLong tradesExecuted = new AtomicLong(0);
    private final AtomicLong ordersProcessed = new AtomicLong(0);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong fixMessagesProcessed = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // WebSocket integration
    private final WebSocketApiServer webSocketServer;
    
    public UltraHighPerformanceEngine(Strategy strategy, RiskManager riskManager) {
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.performanceMonitor = PerformanceMonitor.getInstance();
        
        // Initialize core components
        this.disruptorEngine = new DisruptorEngine(strategy, riskManager);
        this.aeronFeed = new AeronMarketDataFeed();
        this.fixHandler = new FixProtocolHandler("HFT_ENGINE", "EXTERNAL_CLIENT");
        this.webSocketServer = new WebSocketApiServer();
        
        // Set up integrations
        setupIntegrations();
        
        logger.info("Ultra-High Performance Engine initialized");
        logger.info("Components: Disruptor={}, Aeron={}, FIX={}, WebSocket={}", 
                   disruptorEngine != null, aeronFeed != null, fixHandler != null, webSocketServer != null);
    }
    
    /**
     * Set up component integrations
     */
    private void setupIntegrations() {
        // Set up WebSocket handler for Aeron
        aeronFeed.setWebSocketHandler(webSocketServer);
        
        // Set up FIX protocol bridge
        setupFixBridge();
        
        // Set up binary protocol bridge
        setupBinaryBridge();
    }
    
    /**
     * Set up FIX protocol bridge
     */
    private void setupFixBridge() {
        // Bridge FIX messages to binary protocol
        webSocketServer.setFixMessageHandler(this::handleFixMessage);
    }
    
    /**
     * Set up binary protocol bridge
     */
    private void setupBinaryBridge() {
        // Bridge binary messages between components
        webSocketServer.setBinaryMessageHandler(this::handleBinaryMessage);
    }
    
    /**
     * Start the ultra-high performance engine
     */
    public void start() {
        logger.info("Starting Ultra-High Performance Engine");
        running.set(true);
        
        // Start core components
        disruptorEngine.start();
        aeronFeed.start();
        webSocketServer.start();
        
        // Start performance monitoring
        Thread monitoringThread = new Thread(this::monitoringLoop, "UHP-Engine-Monitor");
        monitoringThread.setDaemon(true);
        monitoringThread.start();
        
        logger.info("Ultra-High Performance Engine started successfully");
        logger.info("Ready for high-frequency trading with binary encoding + Disruptor + Aeron + FIX");
    }
    
    /**
     * Stop the engine
     */
    public void stop() {
        logger.info("Stopping Ultra-High Performance Engine");
        running.set(false);
        
        // Stop components in reverse order
        webSocketServer.stop();
        aeronFeed.stop();
        disruptorEngine.stop();
        
        printFinalStatistics();
        
        logger.info("Ultra-High Performance Engine stopped");
    }
    
    /**
     * Process incoming tick data
     */
    public void processTick(long timestamp, int symbolId, long price, long volume, byte side) {
        // Publish to Disruptor for ultra-low latency processing
        disruptorEngine.publishTick(timestamp, symbolId, price, volume, side);
        
        // Publish to Aeron for external distribution
        aeronFeed.publishTick(timestamp, symbolId, price, volume, side);
        
        ticksProcessed.incrementAndGet();
        messagesProcessed.incrementAndGet();
    }
    
    /**
     * Process order update
     */
    public void processOrderUpdate(Order order) {
        // Publish to Disruptor
        disruptorEngine.publishOrder(order);
        
        // Publish to Aeron
        aeronFeed.publishOrderUpdate(order.orderId, order.symbolId, order.price,
                                   order.quantity, order.side, order.type, 
                                   order.timestamp, order.status, order.filledQuantity);
        
        ordersProcessed.incrementAndGet();
        messagesProcessed.incrementAndGet();
    }
    
    /**
     * Handle FIX message
     */
    private void handleFixMessage(byte[] fixData) {
        try {
            fixMessagesProcessed.incrementAndGet();
            FixProtocolHandler.FixMessage message = fixHandler.parseFixMessage(fixData);
            String msgType = message.getMsgType();
            
            switch (msgType) {
                case FixProtocolHandler.NEW_ORDER_SINGLE:
                    handleNewOrderSingle(message);
                    break;
                case FixProtocolHandler.ORDER_STATUS_REQUEST:
                    handleOrderStatusRequest(message);
                    break;
                case FixProtocolHandler.ORDER_CANCEL_REQUEST:
                    handleOrderCancelRequest(message);
                    break;
                case FixProtocolHandler.MARKET_DATA_REQUEST:
                    handleMarketDataRequest(message);
                    break;
                default:
                    logger.warn("Unhandled FIX message type: {}", msgType);
            }
        } catch (Exception e) {
            logger.error("Error handling FIX message", e);
        }
    }
    
    /**
     * Handle binary message
     */
    private void handleBinaryMessage(byte[] binaryData) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(binaryData);
            byte messageType = buffer.get();
            
            switch (messageType) {
                case BinaryProtocol.TICK_MESSAGE:
                    BinaryProtocol.TickData tick = BinaryProtocol.decodeTick(buffer);
                    processTick(tick.timestamp, tick.symbolId, tick.price, tick.volume, tick.side);
                    break;
                case BinaryProtocol.ORDER_MESSAGE:
                    BinaryProtocol.OrderData order = BinaryProtocol.decodeOrder(buffer);
                    // Convert to Order object and process
                    Order orderObj = new Order(order.orderId, order.symbolId, order.price,
                                             order.quantity, order.side, order.orderType);
                    orderObj.timestamp = order.timestamp;
                    orderObj.status = order.status;
                    orderObj.filledQuantity = order.filledQuantity;
                    processOrderUpdate(orderObj);
                    break;
                default:
                    logger.warn("Unknown binary message type: {}", messageType);
            }
        } catch (Exception e) {
            logger.error("Error handling binary message", e);
        }
    }
    
    /**
     * Handle new order single (FIX)
     */
    private void handleNewOrderSingle(FixProtocolHandler.FixMessage message) {
        String clOrdId = message.getField(FixProtocolHandler.CL_ORD_ID);
        String symbol = message.getField(FixProtocolHandler.SYMBOL);
        char side = message.getField(FixProtocolHandler.SIDE).charAt(0);
        double quantity = Double.parseDouble(message.getField(FixProtocolHandler.ORDER_QTY));
        double price = Double.parseDouble(message.getField(FixProtocolHandler.PRICE));
        char ordType = message.getField(FixProtocolHandler.ORD_TYPE).charAt(0);
        
        // Create order and submit to engine
        Order order = new Order(System.currentTimeMillis(), 
                              Integer.parseInt(symbol), 
                              (long)(price * 10000), 
                              (int)quantity, 
                              side == '1' ? (byte)1 : (byte)0, 
                              (byte)ordType);
        
        processOrderUpdate(order);
        
        // Send execution report
        byte[] executionReport = fixHandler.createExecutionReport(
            String.valueOf(order.orderId), clOrdId, symbol, '0', 'A', 
            quantity, 0, price);
        
        webSocketServer.sendFixResponse(executionReport);
    }
    
    /**
     * Handle order status request (FIX)
     */
    private void handleOrderStatusRequest(FixProtocolHandler.FixMessage message) {
        String clOrdId = message.getField(FixProtocolHandler.CL_ORD_ID);
        FixProtocolHandler.FixOrder order = fixHandler.getOrder(clOrdId);
        
        if (order != null) {
            byte[] executionReport = fixHandler.createExecutionReport(
                String.valueOf(order.clOrdId), order.clOrdId, order.symbol,
                fixHandler.getFixExecType((byte)order.status),
                fixHandler.getFixOrdStatus((byte)order.status),
                order.quantity - order.filledQuantity,
                order.filledQuantity, order.averagePrice);
            
            webSocketServer.sendFixResponse(executionReport);
        }
    }
    
    /**
     * Handle order cancel request (FIX)
     */
    private void handleOrderCancelRequest(FixProtocolHandler.FixMessage message) {
        String clOrdId = message.getField(FixProtocolHandler.CL_ORD_ID);
        
        // Update order status to cancelled
        fixHandler.updateOrderStatus(clOrdId, '4', 0, 0);
        
        // Send execution report
        FixProtocolHandler.FixOrder order = fixHandler.getOrder(clOrdId);
        if (order != null) {
            byte[] executionReport = fixHandler.createExecutionReport(
                String.valueOf(order.clOrdId), order.clOrdId, order.symbol,
                'C', '4', 0, order.filledQuantity, order.averagePrice);
            
            webSocketServer.sendFixResponse(executionReport);
        }
    }
    
    /**
     * Handle market data request (FIX)
     */
    private void handleMarketDataRequest(FixProtocolHandler.FixMessage message) {
        String reqId = message.getField(262); // MDReqID
        String symbol = message.getField(FixProtocolHandler.SYMBOL);
        
        // Create market data snapshot
        byte[] marketDataSnapshot = fixHandler.createMarketDataRequest(reqId, symbol, 1);
        webSocketServer.sendFixResponse(marketDataSnapshot);
    }
    
    /**
     * Monitoring loop
     */
    private void monitoringLoop() {
        while (running.get()) {
            try {
                Thread.sleep(5000); // Print stats every 5 seconds
                printStatistics();
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * Print statistics
     */
    public void printStatistics() {
        logger.info("=== Ultra-High Performance Engine Statistics ===");
        logger.info("Engine Status: {}", running.get() ? "RUNNING" : "STOPPED");
        logger.info("Ticks Processed: {} ({} tps)", 
                   ticksProcessed.get(), 
                   String.format("%.0f", ticksProcessed.get() / 5.0));
        logger.info("Orders Processed: {}", ordersProcessed.get());
        logger.info("Trades Executed: {}", tradesExecuted.get());
        logger.info("Strategy P&L: ${}", String.format("%.2f", strategy.getPnL()));
        
        // Component statistics
        disruptorEngine.printStatistics();
        aeronFeed.printStatistics();
        
        logger.info("WebSocket Connections: {}", webSocketServer.getConnectionCount());
        logger.info("FIX Orders Cached: {}", fixHandler.orders.size());
        logger.info("================================================");
    }
    
    /**
     * Print final statistics
     */
    private void printFinalStatistics() {
        logger.info("=== Final Performance Summary ===");
        logger.info("Total Ticks Processed: {}", ticksProcessed.get());
        logger.info("Total Orders Processed: {}", ordersProcessed.get());
        logger.info("Total Trades Executed: {}", tradesExecuted.get());
        logger.info("Final Strategy P&L: ${}", String.format("%.2f", strategy.getPnL()));
        logger.info("================================");
    }
    
    // Getters
    public long getTicksProcessed() { return ticksProcessed.get(); }
    public long getOrdersProcessed() { return ordersProcessed.get(); }
    public long getTradesExecuted() { return tradesExecuted.get(); }
    public long getMessagesProcessed() { return messagesProcessed.get(); }
    public long getFixMessagesProcessed() { return fixMessagesProcessed.get(); }
    public boolean isRunning() { return running.get(); }
    
    /**
     * Get engine configuration
     */
    public EngineConfiguration getConfiguration() {
        return new EngineConfiguration(
            disruptorEngine != null,
            aeronFeed != null,
            fixHandler != null,
            webSocketServer != null
        );
    }
    
    /**
     * Engine configuration
     */
    public static class EngineConfiguration {
        public final boolean disruptorEnabled;
        public final boolean aeronEnabled;
        public final boolean fixEnabled;
        public final boolean webSocketEnabled;
        
        public EngineConfiguration(boolean disruptorEnabled, boolean aeronEnabled, 
                                boolean fixEnabled, boolean webSocketEnabled) {
            this.disruptorEnabled = disruptorEnabled;
            this.aeronEnabled = aeronEnabled;
            this.fixEnabled = fixEnabled;
            this.webSocketEnabled = webSocketEnabled;
        }
    }
}
