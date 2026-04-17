package com.hft.exchange;



import java.net.URI;

import java.util.ArrayList;

import java.util.List;

import java.util.concurrent.BlockingQueue;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.TimeUnit;



import org.java_websocket.client.WebSocketClient;

import org.java_websocket.handshake.ServerHandshake;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



import com.google.gson.JsonObject;

import com.google.gson.JsonParser;

import com.hft.core.SymbolMapper;

import com.hft.core.Tick;



/**

 * Binance WebSocket connector for real-time market data

 */

public class BinanceConnector {

    private static final Logger logger = LoggerFactory.getLogger(BinanceConnector.class);

    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/ws";

    

    private WebSocketClient client;

    private final BlockingQueue<Tick> tickQueue = new LinkedBlockingQueue<>(10000);

    private final List<String> symbols;

    private volatile boolean connected = false;

    

    // Rate limiting for logs

    private long lastWarningTime = 0;

    private static final long WARNING_INTERVAL = 5000; // Log warnings every 5 seconds

    

    public BinanceConnector(List<String> symbols) {

        this.symbols = new ArrayList<>(symbols);

        // Ensure symbols are registered

        for (String symbol : symbols) {

            SymbolMapper.register(symbol.toLowerCase());

        }

    }

    

    /**

     * Connect to Binance WebSocket

     */

    public void connect() {

        try {

            // Build stream URL for multiple symbols

            StringBuilder streamUrl = new StringBuilder(BINANCE_WS_URL + "/");

            for (int i = 0; i < symbols.size(); i++) {

                if (i > 0) streamUrl.append("/");

                streamUrl.append(symbols.get(i).toLowerCase()).append("@trade");

            }

            

            logger.info("Connecting to Binance: {}", streamUrl);

            

            client = new WebSocketClient(new URI(streamUrl.toString())) {

                @Override

                public void onOpen(ServerHandshake handshake) {

                    connected = true;

                    logger.info("Connected to Binance WebSocket");

                }

                

                @Override

                public void onMessage(String message) {

                    try {

                        processMessage(message);

                    } catch (Exception e) {

                        logger.error("Error processing message", e);

                    }

                }

                

                @Override

                public void onClose(int code, String reason, boolean remote) {

                    connected = false;

                    logger.warn("Connection closed: {} - {}", code, reason);

                }

                

                @Override

                public void onError(Exception ex) {

                    logger.error("WebSocket error", ex);

                }

            };

            

            client.connect();

            

        } catch (Exception e) {

            logger.error("Failed to connect", e);

            throw new RuntimeException(e);

        }

    }

    

    /**

     * Process incoming trade message

     */

    private void processMessage(String message) {

        try {

            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            

            // Binance trade stream format

            String symbol = json.get("s").getAsString();

            double price = json.get("p").getAsDouble();

            double quantity = json.get("q").getAsDouble();

            long timestamp = json.get("T").getAsLong() * 1_000_000; // Convert to nanos

            boolean isBuyerMaker = json.get("m").getAsBoolean();

            

            // Create tick

            Tick tick = new Tick();

            tick.timestamp = timestamp;

            tick.symbolId = SymbolMapper.getId(symbol);

            tick.setPrice(price);

            tick.volume = (long)(quantity * 100000); // Scale volume

            tick.side = (byte)(isBuyerMaker ? 1 : 0); // Buyer maker means sell

            

            // Add to queue (non-blocking)

            if (!tickQueue.offer(tick)) {

                long currentTime = System.currentTimeMillis();

                if (currentTime - lastWarningTime > WARNING_INTERVAL) {

                    logger.warn("Tick queue full, dropping tick (queue size: {})", tickQueue.size());

                    lastWarningTime = currentTime;

                }

            }

            // Periodic debug trace for queue depth

            long now = System.currentTimeMillis();

            if (now % 60000 < 100) { // roughly every minute

                logger.debug("Tick queue size: {}", tickQueue.size());

            }

            

        } catch (Exception e) {

            logger.error("Error parsing message: {}", message, e);

        }

    }

    

    /**

     * Get next tick (with timeout to prevent infinite blocking)

     */

    public Tick getNextTick() throws InterruptedException {

        return tickQueue.poll(100, TimeUnit.MILLISECONDS);

    }

    

    /**

     * Poll tick (non-blocking)

     */

    public Tick pollTick() {

        return tickQueue.poll();

    }

    

    /**

     * Check if connected

     */

    public boolean isConnected() {

        return connected && client != null && client.isOpen();

    }

    

    /**

     * Disconnect

     */

    public void disconnect() {

        if (client != null) {

            client.close();

        }

        connected = false;

    }

    

    /**

     * Get queue size

     */

    public int getQueueSize() {

        return tickQueue.size();

    }

}