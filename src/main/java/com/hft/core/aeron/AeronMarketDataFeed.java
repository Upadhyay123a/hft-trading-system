package com.hft.core.aeron;

import com.hft.core.binary.BinaryProtocol;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aeron-based market data feed for ultra-low latency real-time updates
 * Provides WebSocket API integration and high-performance data distribution
 */
public class AeronMarketDataFeed {
    private static final Logger logger = LoggerFactory.getLogger(AeronMarketDataFeed.class);
    
    // Aeron channels
    private static final String MARKET_DATA_CHANNEL = "aeron:udp?endpoint=localhost:40123";
    private static final String WEBSOCKET_CHANNEL = "aeron:udp?endpoint=localhost:40124";
    private static final String ORDER_CHANNEL = "aeron:udp?endpoint=localhost:40125";
    
    // Stream IDs
    private static final int MARKET_DATA_STREAM = 1;
    private static final int WEBSOCKET_STREAM = 2;
    private static final int ORDER_STREAM = 3;
    
    // Aeron components
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final Publication marketDataPublication;
    private final Subscription marketDataSubscription;
    private final Publication websocketPublication;
    private final Subscription orderSubscription;
    
    // Buffers
    private final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(1024);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Performance
    private final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy(1);
    
    // WebSocket handler
    private WebSocketHandler webSocketHandler;
    
    public AeronMarketDataFeed() {
        // Start embedded media driver
        MediaDriver.Context driverContext = new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .threadingMode(ThreadingMode.DEDICATED)
            .sharedIdleStrategy(new SleepingMillisIdleStrategy(1));
            
        this.mediaDriver = MediaDriver.launch(driverContext);
        
        // Create Aeron context
        Aeron.Context aeronContext = new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName())
            .keepAliveIntervalNs(1_000_000_000L); // 1 second
            
        this.aeron = Aeron.connect(aeronContext);
        
        // Create publications and subscriptions
        this.marketDataPublication = aeron.addPublication(MARKET_DATA_CHANNEL, MARKET_DATA_STREAM);
        this.marketDataSubscription = aeron.addSubscription(MARKET_DATA_CHANNEL, MARKET_DATA_STREAM);
        this.websocketPublication = aeron.addPublication(WEBSOCKET_CHANNEL, WEBSOCKET_STREAM);
        this.orderSubscription = aeron.addSubscription(ORDER_CHANNEL, ORDER_STREAM);
        
        logger.info("Aeron Market Data Feed initialized");
        logger.info("Market Data: {} (stream: {})", MARKET_DATA_CHANNEL, MARKET_DATA_STREAM);
        logger.info("WebSocket: {} (stream: {})", WEBSOCKET_CHANNEL, WEBSOCKET_STREAM);
        logger.info("Orders: {} (stream: {})", ORDER_CHANNEL, ORDER_STREAM);
    }
    
    /**
     * Start the Aeron feed
     */
    public void start() {
        logger.info("Starting Aeron Market Data Feed");
        running.set(true);
        
        // Start market data subscriber
        Thread marketDataThread = new Thread(this::marketDataLoop, "Aeron-MarketData");
        marketDataThread.setDaemon(true);
        marketDataThread.setPriority(Thread.MAX_PRIORITY - 1);
        marketDataThread.start();
        
        // Start order subscriber
        Thread orderThread = new Thread(this::orderLoop, "Aeron-Orders");
        orderThread.setDaemon(true);
        orderThread.setPriority(Thread.MAX_PRIORITY - 1);
        orderThread.start();
        
        // Start WebSocket publisher
        Thread websocketThread = new Thread(this::websocketLoop, "Aeron-WebSocket");
        websocketThread.setDaemon(true);
        websocketThread.start();
        
        logger.info("Aeron Market Data Feed started successfully");
    }
    
    /**
     * Publish market data tick
     */
    public void publishTick(long timestamp, int symbolId, long price, long volume, byte side) {
        sendBuffer.clear();
        
        // Encode tick using binary protocol
        BinaryProtocol.encodeTick(sendBuffer, timestamp, symbolId, price, volume, side);
        sendBuffer.flip();
        
        // Publish via Aeron
        DirectBuffer directBuffer = new UnsafeBuffer(sendBuffer);
        long result = marketDataPublication.offer(directBuffer);
        
        if (result < 0) {
            handlePublicationFailure(result, "market data tick");
        } else {
            // Also publish to WebSocket feed
            publishToWebSocket(sendBuffer);
        }
    }
    
    /**
     * Publish order update
     */
    public void publishOrderUpdate(long orderId, int symbolId, long price, int quantity, 
                                   byte side, byte orderType, long timestamp, byte status, 
                                   int filledQuantity) {
        sendBuffer.clear();
        
        // Encode order using binary protocol
        BinaryProtocol.encodeOrder(sendBuffer, orderId, symbolId, price, quantity, 
                                  side, orderType, timestamp, status, filledQuantity);
        sendBuffer.flip();
        
        // Publish via Aeron
        DirectBuffer directBuffer = new UnsafeBuffer(sendBuffer);
        long result = marketDataPublication.offer(directBuffer);
        
        if (result < 0) {
            handlePublicationFailure(result, "order update");
        } else {
            // Also publish to WebSocket feed
            publishToWebSocket(sendBuffer);
        }
    }
    
    /**
     * Publish to WebSocket feed
     */
    private void publishToWebSocket(ByteBuffer data) {
        DirectBuffer buffer = new UnsafeBuffer(data);
        long result = websocketPublication.offer(buffer);
        if (result < 0) {
            handlePublicationFailure(result, "WebSocket feed");
        }
    }
    
    /**
     * Market data subscriber loop
     */
    private void marketDataLoop() {
        while (running.get()) {
            marketDataSubscription.poll(this::handleMarketDataMessage, 10);
            idleStrategy.idle();
        }
    }
    
    /**
     * Order subscriber loop
     */
    private void orderLoop() {
        while (running.get()) {
            orderSubscription.poll(this::handleOrderMessage, 10);
            idleStrategy.idle();
        }
    }
    
    /**
     * WebSocket publisher loop
     */
    private void websocketLoop() {
        while (running.get()) {
            // Handle WebSocket connections and data distribution
            if (webSocketHandler != null) {
                webSocketHandler.processConnections();
            }
            idleStrategy.idle();
        }
    }
    
    /**
     * Handle incoming market data message
     */
    private void handleMarketDataMessage(DirectBuffer buffer, int offset, int length, Header header) {
        try {
            UnsafeBuffer slicedBuffer = new UnsafeBuffer(buffer, offset, length);
            ByteBuffer data = slicedBuffer.byteBuffer();
            byte messageType = data.get(0);
            
            switch (messageType) {
                case BinaryProtocol.TICK_MESSAGE:
                    BinaryProtocol.TickData tick = BinaryProtocol.decodeTick(data);
                    processTick(tick);
                    break;
                case BinaryProtocol.TRADE_MESSAGE:
                    BinaryProtocol.TradeData trade = BinaryProtocol.decodeTrade(data);
                    processTrade(trade);
                    break;
                default:
                    logger.warn("Unknown market data message type: {}", messageType);
            }
        } catch (Exception e) {
            logger.error("Error handling market data message", e);
        }
    }
    
    /**
     * Handle incoming order message
     */
    private void handleOrderMessage(DirectBuffer buffer, int offset, int length, Header header) {
        try {
            UnsafeBuffer slicedBuffer = new UnsafeBuffer(buffer, offset, length);
            ByteBuffer data = slicedBuffer.byteBuffer();
            byte messageType = data.get(0);
            
            if (messageType == BinaryProtocol.ORDER_MESSAGE) {
                BinaryProtocol.OrderData order = BinaryProtocol.decodeOrder(data);
                processOrder(order);
            } else {
                logger.warn("Unknown order message type: {}", messageType);
            }
        } catch (Exception e) {
            logger.error("Error handling order message", e);
        }
    }
    
    /**
     * Process tick data
     */
    private void processTick(BinaryProtocol.TickData tick) {
        // Forward to trading engine or other components
        logger.debug("Received tick: symbol={}, price={}, volume={}", 
                     tick.symbolId, tick.price, tick.volume);
        
        // Update market data cache, analytics, etc.
    }
    
    /**
     * Process trade data
     */
    private void processTrade(BinaryProtocol.TradeData trade) {
        // Forward to trading engine or other components
        logger.debug("Received trade: symbol={}, price={}, quantity={}", 
                     trade.symbolId, trade.price, trade.quantity);
        
        // Update trade cache, analytics, etc.
    }
    
    /**
     * Process order data
     */
    private void processOrder(BinaryProtocol.OrderData order) {
        // Forward to trading engine or other components
        logger.debug("Received order: id={}, symbol={}, price={}, qty={}", 
                     order.orderId, order.symbolId, order.price, order.quantity);
        
        // Update order cache, risk management, etc.
    }
    
    /**
     * Handle publication failures
     */
    private void handlePublicationFailure(long result, String messageType) {
        if (result == Publication.BACK_PRESSURED) {
            logger.warn("Publication back pressured for {}", messageType);
        } else if (result == Publication.NOT_CONNECTED) {
            logger.warn("Publication not connected for {}", messageType);
        } else if (result == Publication.ADMIN_ACTION) {
            logger.warn("Publication admin action for {}", messageType);
        } else if (result == Publication.CLOSED) {
            logger.error("Publication closed for {}", messageType);
        } else {
            logger.error("Unknown publication failure for {}: {}", messageType, result);
        }
    }
    
    /**
     * Set WebSocket handler
     */
    public void setWebSocketHandler(WebSocketHandler handler) {
        this.webSocketHandler = handler;
    }
    
    /**
     * Get connection statistics
     */
    public void printStatistics() {
        logger.info("=== Aeron Market Data Feed Statistics ===");
        logger.info("Market Data Publication: {}", marketDataPublication.isConnected() ? "Connected" : "Disconnected");
        logger.info("WebSocket Publication: {}", websocketPublication.isConnected() ? "Connected" : "Disconnected");
        logger.info("Market Data Subscription: {}", marketDataSubscription.isConnected() ? "Connected" : "Disconnected");
        logger.info("Order Subscription: {}", orderSubscription.isConnected() ? "Connected" : "Disconnected");
        logger.info("=========================================");
    }
    
    /**
     * Stop the Aeron feed
     */
    public void stop() {
        logger.info("Stopping Aeron Market Data Feed");
        running.set(false);
        
        CloseHelper.close(websocketPublication);
        CloseHelper.close(marketDataPublication);
        CloseHelper.close(orderSubscription);
        CloseHelper.close(marketDataSubscription);
        CloseHelper.close(aeron);
        CloseHelper.close(mediaDriver);
        
        logger.info("Aeron Market Data Feed stopped");
    }
    
    /**
     * WebSocket handler interface
     */
    public interface WebSocketHandler {
        void processConnections();
        void broadcastToClients(ByteBuffer data);
    }
}
