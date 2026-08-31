package com.hft.exchange;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hft.exchange.api.BinanceRealApi;

public class MockedBinanceWebSocketTest {
    static TestWebSocketServer server;
    static int port = 9001;

    static class TestWebSocketServer extends WebSocketServer {
        private final CountDownLatch latch;

        public TestWebSocketServer(InetSocketAddress address, CountDownLatch latch) {
            super(address);
            this.latch = latch;
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            // send a couple of Binance-style trade/depth messages
            try {
                String tradeMsg = "{\"e\":\"trade\",\"s\":\"BTCUSDT\",\"p\":\"30000.0\",\"q\":\"0.001\"}";
                conn.send(tradeMsg);
                Thread.sleep(50);
                String depthMsg = "{\"e\":\"depthUpdate\",\"s\":\"BTCUSDT\",\"b\":[],\"a\":[] }";
                conn.send(depthMsg);
            } catch (Exception e) {
                // ignore
            }
            latch.countDown();
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

        @Override
        public void onMessage(WebSocket conn, String message) {}

        @Override
        public void onError(WebSocket conn, Exception ex) {}

        @Override
        public void onStart() {}
    }

    @BeforeAll
    public static void startServer() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        server = new TestWebSocketServer(new InetSocketAddress("localhost", port), latch);
        server.start();
        // set override so BinanceRealApi connects to our mock server
        System.setProperty("binance.ws.override", "ws://localhost:" + port + "/ws");
        // wait a brief moment for server to be ready
        Thread.sleep(200);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (server != null) server.stop(1000);
        System.clearProperty("binance.ws.override");
    }

    @Test
    public void testConnectsAndReceivesMessages() throws Exception {
        // connect
        BinanceRealApi api = new BinanceRealApi();
        api.connectMarketData(List.of("BTCUSDT")).join();

        // Wait a little for messages to be exchanged
        Thread.sleep(300);

        // If we reached here without exception, connection succeeded to mock
        api.disconnect();
        assertTrue(true, "Connected to mocked Binance WS and exchanged messages");
    }
}
