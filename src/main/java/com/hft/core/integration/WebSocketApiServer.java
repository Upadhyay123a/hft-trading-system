package com.hft.core.integration;

import com.hft.core.aeron.AeronMarketDataFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance WebSocket API server for real-time updates
 * Integrates with Aeron for ultra-low latency data distribution
 */
public class WebSocketApiServer implements AeronMarketDataFeed.WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketApiServer.class);
    
    // WebSocket configuration
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_CONNECTIONS = 1000;
    
    // Connection management
    private final ConcurrentHashMap<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Message handlers
    private MessageHandler fixMessageHandler;
    private MessageHandler binaryMessageHandler;
    
    // Performance
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    
    public WebSocketApiServer() {
        logger.info("WebSocket API Server initialized");
    }
    
    /**
     * Start the WebSocket server
     */
    public void start() {
        logger.info("Starting WebSocket API Server on port {}", DEFAULT_PORT);
        running.set(true);
        
        // In a real implementation, this would start a Netty or similar WebSocket server
        // For now, we'll simulate the server with a mock implementation
        
        Thread serverThread = new Thread(this::serverLoop, "WebSocket-Server");
        serverThread.setDaemon(true);
        serverThread.start();
        
        logger.info("WebSocket API Server started successfully");
    }
    
    /**
     * Stop the WebSocket server
     */
    public void stop() {
        logger.info("Stopping WebSocket API Server");
        running.set(false);
        
        // Close all connections
        connections.values().forEach(WebSocketConnection::close);
        connections.clear();
        
        logger.info("WebSocket API Server stopped");
    }
    
    /**
     * Set FIX message handler
     */
    public void setFixMessageHandler(MessageHandler handler) {
        this.fixMessageHandler = handler;
    }
    
    /**
     * Set binary message handler
     */
    public void setBinaryMessageHandler(MessageHandler handler) {
        this.binaryMessageHandler = handler;
    }
    
    /**
     * Send FIX response to client
     */
    public void sendFixResponse(byte[] fixData) {
        broadcastToClients(ByteBuffer.wrap(fixData));
        messagesSent.incrementAndGet();
    }
    
    /**
     * Send binary response to client
     */
    public void sendBinaryResponse(byte[] binaryData) {
        broadcastToClients(ByteBuffer.wrap(binaryData));
        messagesSent.incrementAndGet();
    }
    
    /**
     * Process WebSocket connections (mock implementation)
     */
    @Override
    public void processConnections() {
        // In a real implementation, this would handle:
        // - New connection establishment
        // - Connection authentication
        // - Message routing
        // - Connection health monitoring
        
        // For now, we'll simulate connection processing
        if (Math.random() < 0.01) { // 1% chance of new connection
            simulateNewConnection();
        }
        
        if (Math.random() < 0.005) { // 0.5% chance of connection loss
            simulateConnectionLoss();
        }
    }
    
    /**
     * Broadcast data to all connected clients
     */
    @Override
    public void broadcastToClients(ByteBuffer data) {
        connections.values().forEach(connection -> {
            try {
                connection.send(data);
            } catch (Exception e) {
                logger.error("Error sending data to connection: {}", connection.getId(), e);
                connections.remove(connection.getId());
                connectionCount.decrementAndGet();
            }
        });
    }
    
    /**
     * Get connection count
     */
    public int getConnectionCount() {
        return connectionCount.get();
    }
    
    /**
     * Server loop (SAFE implementation with timeouts)
     */
    private void serverLoop() {
        int errorCount = 0;
        final int MAX_ERRORS = 10;
        
        while (running.get() && errorCount < MAX_ERRORS) {
            try {
                // Process connections with timeout
                processConnections();
                
                // Simulate message processing (limited)
                if (Math.random() < 0.05) { // Reduced to 5% chance
                    simulateIncomingMessage();
                }
                
                // Longer sleep to reduce CPU usage
                Thread.sleep(500); // 500ms processing interval
                
                // Reset error count on success
                errorCount = 0;
                
            } catch (InterruptedException e) {
                logger.info("WebSocket server loop interrupted");
                break;
            } catch (Exception e) {
                errorCount++;
                logger.error("Error in WebSocket server loop ({}): {}", errorCount, e.getMessage());
                
                // Exit if too many errors
                if (errorCount >= MAX_ERRORS) {
                    logger.error("Too many errors in WebSocket server, stopping");
                    break;
                }
                
                // Wait before retry
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        
        logger.info("WebSocket server loop terminated");
    }
    
    /**
     * Simulate new connection (SAFE with memory limits)
     */
    private void simulateNewConnection() {
        // Limit connection creation rate
        if (connectionCount.get() < MAX_CONNECTIONS && connections.size() < MAX_CONNECTIONS) {
            String connectionId = "conn_" + System.currentTimeMillis();
            WebSocketConnection connection = new WebSocketConnection(connectionId);
            
            // Prevent memory leaks by cleaning old connections
            if (connections.size() >= MAX_CONNECTIONS - 1) {
                cleanupOldConnections();
            }
            
            connections.put(connectionId, connection);
            connectionCount.incrementAndGet();
            
            logger.debug("New WebSocket connection: {} (total: {})", 
                        connectionId, connectionCount.get());
        }
    }
    
    /**
     * Clean up old connections to prevent memory leaks
     */
    private void cleanupOldConnections() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 60000; // 1 minute max age
        
        connections.keys().asIterator().forEachRemaining(connectionId -> {
            WebSocketConnection connection = connections.get(connectionId);
            if (connection != null && (currentTime - connection.getConnectTime()) > maxAge) {
                connection.close();
                connections.remove(connectionId);
                connectionCount.decrementAndGet();
            }
        });
    }
    
    /**
     * Simulate connection loss
     */
    private void simulateConnectionLoss() {
        if (!connections.isEmpty()) {
            String connectionId = connections.keys().nextElement();
            WebSocketConnection connection = connections.remove(connectionId);
            if (connection != null) {
                connection.close();
                connectionCount.decrementAndGet();
                logger.debug("WebSocket connection lost: {} (total: {})", 
                            connectionId, connectionCount.get());
            }
        }
    }
    
    /**
     * Simulate incoming message (SAFE with limits)
     */
    private void simulateIncomingMessage() {
        if (!connections.isEmpty() && messagesReceived.get() < 1000) { // Limit messages
            try {
                String connectionId = connections.keys().nextElement();
                WebSocketConnection connection = connections.get(connectionId);
                
                if (connection != null && !connection.isClosed()) {
                    // Simulate different message types (smaller messages)
                    byte[] message = simulateMessage();
                    
                    if (message != null && message.length < 1024) { // Limit message size
                        handleMessage(message);
                        messagesReceived.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                logger.error("Error simulating incoming message: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Simulate message data
     */
    private byte[] simulateMessage() {
        // Simulate different message types
        double rand = Math.random();
        
        if (rand < 0.3) {
            // 30% chance of FIX message
            return "35=D|11=12345|55=BTCUSDT|54=1|38=1.0|44=50000.0|40=2|".getBytes();
        } else if (rand < 0.6) {
            // 30% chance of binary message
            ByteBuffer buffer = ByteBuffer.allocate(33);
            buffer.put((byte)1); // Tick message type
            buffer.putLong(System.nanoTime());
            buffer.putInt(1); // BTC/USDT
            buffer.putLong(500000000L); // $50000.00
            buffer.putLong(1000000L); // Volume
            buffer.put((byte)0); // Buy side
            return buffer.array();
        } else {
            // 40% chance of other message
            return null;
        }
    }
    
    /**
     * Handle incoming message
     */
    private void handleMessage(byte[] message) {
        try {
            // Determine message type and route to appropriate handler
            if (isFixMessage(message)) {
                if (fixMessageHandler != null) {
                    fixMessageHandler.handle(message);
                }
            } else if (isBinaryMessage(message)) {
                if (binaryMessageHandler != null) {
                    binaryMessageHandler.handle(message);
                }
            } else {
                logger.warn("Unknown message type received");
            }
        } catch (Exception e) {
            logger.error("Error handling message", e);
        }
    }
    
    /**
     * Check if message is FIX format
     */
    private boolean isFixMessage(byte[] message) {
        // Simple check for FIX message format (contains "=" and "|")
        String messageStr = new String(message);
        return messageStr.contains("=") && messageStr.contains("|");
    }
    
    /**
     * Check if message is binary format
     */
    private boolean isBinaryMessage(byte[] message) {
        // Simple check for binary message (first byte is known message type)
        return message.length > 0 && 
               (message[0] == 1 || message[0] == 2 || message[0] == 3); // Known message types
    }
    
    /**
     * Print statistics
     */
    public void printStatistics() {
        logger.info("=== WebSocket API Server Statistics ===");
        logger.info("Active Connections: {}", connectionCount.get());
        logger.info("Messages Sent: {}", messagesSent.get());
        logger.info("Messages Received: {}", messagesReceived.get());
        logger.info("Message Rate: {} msg/sec", 
                   String.format("%.1f", messagesReceived.get() / 5.0));
        logger.info("=====================================");
    }
    
    /**
     * Message handler interface
     */
    @FunctionalInterface
    public interface MessageHandler {
        void handle(byte[] message);
    }
    
    /**
     * WebSocket connection representation
     */
    private static class WebSocketConnection {
        private final String id;
        private final long connectTime;
        private volatile boolean closed = false;
        
        public WebSocketConnection(String id) {
            this.id = id;
            this.connectTime = System.currentTimeMillis();
        }
        
        public String getId() {
            return id;
        }
        
        public void send(ByteBuffer data) {
            if (!closed) {
                // In a real implementation, this would send data over the WebSocket
                logger.debug("Sending {} bytes to connection {}", data.remaining(), id);
            }
        }
        
        public void close() {
            closed = true;
            logger.debug("Connection {} closed", id);
        }
        
        public long getConnectTime() {
            return connectTime;
        }
        
        public boolean isClosed() {
            return closed;
        }
    }
}
