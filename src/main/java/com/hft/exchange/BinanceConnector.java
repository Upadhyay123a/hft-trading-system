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

    package com.hft.exchange;

    import com.google.gson.JsonObject;
    import com.google.gson.JsonParser;
    import com.hft.core.SymbolMapper;
    import com.hft.core.Tick;
    import org.java_websocket.client.WebSocketClient;
    import org.java_websocket.handshake.ServerHandshake;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;

    import java.net.URI;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.concurrent.BlockingQueue;
    import java.util.concurrent.LinkedBlockingQueue;
    import java.util.concurrent.TimeUnit;

    /**
     * Binance WebSocket connector for real-time market data.
     * Lightweight reconnect with exponential backoff on close/error.
     */
    public class BinanceConnector {
        private static final Logger logger = LoggerFactory.getLogger(BinanceConnector.class);
        private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/stream?streams=";

        private volatile WebSocketClient client;
        private final BlockingQueue<Tick> tickQueue = new LinkedBlockingQueue<>(50_000);
        private final List<String> symbols;
        private volatile boolean connected = false;
        private volatile boolean shouldRun = true;

        public BinanceConnector(List<String> symbols) {
            this.symbols = new ArrayList<>(symbols);
            for (String s : symbols) SymbolMapper.register(s.toLowerCase());
        }

        public synchronized void connect() {
            if (!shouldRun) return;
            try {
                StringBuilder streams = new StringBuilder();
                for (int i = 0; i < symbols.size(); i++) {
                    if (i > 0) streams.append('/');
                    streams.append(symbols.get(i).toLowerCase()).append("@trade");
                }
                String streamUrl = BINANCE_WS_URL + streams.toString();
                logger.info("Connecting to Binance: {}", streamUrl);

                client = new WebSocketClient(new URI(streamUrl)) {
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
                        logger.warn("Binance WebSocket closed: {} - {}", code, reason);
                        if (shouldRun) scheduleReconnect();
                    }

                    @Override
                    public void onError(Exception ex) {
                        logger.error("Binance WebSocket error", ex);
                        // onError may be followed by onClose; attempt reconnect defensively
                        if (shouldRun && !isConnected()) scheduleReconnect();
                    }
                };

                client.connect();

            } catch (Exception e) {
                logger.error("Failed to initiate Binance WebSocket", e);
                scheduleReconnect();
            }
        }

        private void scheduleReconnect() {
            new Thread(() -> {
                int attempt = 0;
                while (shouldRun && !isConnected() && attempt < 10) {
                    attempt++;
                    long backoff = Math.min(30_000, 500L * (1L << Math.min(attempt, 6)));
                    logger.info("Reconnect attempt {} in {}ms", attempt, backoff);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        connect();
                    } catch (Exception e) {
                        logger.warn("Reconnect attempt {} failed", attempt, e);
                    }
                }
            }, "Binance-Reconnect").start();
        }

        private void processMessage(String message) {
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                // If using /stream endpoint, the payload may be { "stream":"...", "data":{...} }
                if (json.has("data")) json = json.getAsJsonObject("data");

                String symbol = json.get("s").getAsString();
                double price = json.get("p").getAsDouble();
                double quantity = json.get("q").getAsDouble();
                long timestamp = json.get("T").getAsLong() * 1_000_000; // nanos
                boolean isBuyerMaker = json.get("m").getAsBoolean();

                Tick tick = new Tick();
                tick.timestamp = timestamp;
                tick.symbolId = SymbolMapper.getId(symbol);
                tick.setPrice(price);
                tick.volume = (long) (quantity * 100_000);
                tick.side = (byte) (isBuyerMaker ? 1 : 0);

                if (!tickQueue.offer(tick)) {
                    logger.warn("Tick queue full, dropping tick (size={})", tickQueue.size());
                }
            } catch (Exception e) {
                logger.debug("Ignoring malformed Binance message: {}", message);
            }
        }

        public Tick getNextTick() throws InterruptedException {
            return tickQueue.poll(100, TimeUnit.MILLISECONDS);
        }

        public Tick pollTick() {
            return tickQueue.poll();
        }

        public boolean isConnected() {
            WebSocketClient c = client;
            return connected && c != null && c.isOpen();
        }

        public synchronized void disconnect() {
            shouldRun = false;
            WebSocketClient c = client;
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }

        public int getQueueSize() {
            return tickQueue.size();
        }
    }

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

                    // Build stream URI for multiple symbols using /stream?streams=
                    StringBuilder streams = new StringBuilder();
                    for (int i = 0; i < symbols.size(); i++) {
                        if (i > 0) streams.append("/");
                        streams.append(symbols.get(i).toLowerCase()).append("@trade");
                    }

                    // Convert base /ws endpoint to /stream?streams= to support multiple streams
                    String streamUrl = BINANCE_WS_URL.replace("/ws", "/stream?streams=") + streams.toString();
                    logger.info("Connecting to Binance: {}", streamUrl);

                    client = new WebSocketClient(new URI(streamUrl)) {

     * Get queue size

     */

    public int getQueueSize() {

        return tickQueue.size();

    }

}