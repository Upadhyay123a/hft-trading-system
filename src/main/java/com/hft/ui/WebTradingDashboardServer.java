package com.hft.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebTradingDashboardServer {
    private final HttpServer server;
    private final int port;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public WebTradingDashboardServer(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        configureRoutes();
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            server.start();
        }
    }

    public void stop() {
        if (started.getAndSet(false)) {
            server.stop(0);
        }
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void configureRoutes() {
        server.createContext("/", new StaticResourceHandler("web/index.html"));
        server.createContext("/app.js", new StaticResourceHandler("web/app.js"));
        server.createContext("/styles.css", new StaticResourceHandler("web/styles.css"));
        server.createContext("/api/market", new MarketApiHandler());
        server.createContext("/api/order", new OrderApiHandler());
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = WebTradingDashboardServer.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static class StaticResourceHandler implements HttpHandler {
        private final String resourcePath;

        private StaticResourceHandler(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] content = readResource(resourcePath).getBytes(StandardCharsets.UTF_8);
            String type = resourcePath.endsWith(".js") ? "application/javascript; charset=utf-8" : "text/html; charset=utf-8";
            if (resourcePath.endsWith(".css")) {
                type = "text/css; charset=utf-8";
            }
            exchange.getResponseHeaders().add("Content-Type", type);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(content);
            }
        }
    }

    private static class MarketApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> payload = new HashMap<>();
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("status", "Live");
            payload.put("strategy", "Market Making");
            payload.put("metrics", Map.of(
                    "sharpe", 1.82,
                    "winRate", 63.4,
                    "exposure", 12.5,
                    "status", "Healthy"
            ));

            List<Map<String, Object>> market = new ArrayList<>();
            market.add(Map.of(
                    "symbol", "BTCUSDT",
                    "price", 68342.15,
                    "changePct", 2.35,
                    "volume", "1.24M"
            ));
            market.add(Map.of(
                    "symbol", "ETHUSDT",
                    "price", 3642.10,
                    "changePct", 1.82,
                    "volume", "2.31M"
            ));
            market.add(Map.of(
                    "symbol", "SOLUSDT",
                    "price", 156.84,
                    "changePct", 4.10,
                    "volume", "5.45M"
            ));
            payload.put("market", market);

            String json = toJson(payload);
            writeJson(exchange, json);
        }
    }

    private static class OrderApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> payload = new HashMap<>();

            String symbol = "BTCUSDT";
            String side = "BUY";
            String quantity = "1";

            if (!requestBody.isBlank()) {
                String[] parts = requestBody.replace("{", "").replace("}", "").replace("\"", "").split(",");
                for (String part : parts) {
                    String[] kv = part.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String value = kv[1].trim();
                        if ("symbol".equalsIgnoreCase(key)) symbol = value;
                        if ("side".equalsIgnoreCase(key)) side = value;
                        if ("quantity".equalsIgnoreCase(key)) quantity = value;
                    }
                }
            }

            try {
                double qty = Double.parseDouble(quantity);
                if (qty <= 0) {
                    payload.put("ok", false);
                    payload.put("message", "Quantity must be positive.");
                } else if (!"BUY".equalsIgnoreCase(side) && !"SELL".equalsIgnoreCase(side)) {
                    payload.put("ok", false);
                    payload.put("message", "Side must be BUY or SELL.");
                } else {
                    payload.put("ok", true);
                    payload.put("message", "Order placed: " + side.toUpperCase() + " " + qty + " " + symbol);
                }
            } catch (NumberFormatException e) {
                payload.put("ok", false);
                payload.put("message", "Quantity must be a valid number.");
            }

            writeJson(exchange, toJson(payload));
        }
    }

    private static String toJson(Map<String, Object> payload) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append('"').append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else if (value instanceof Number || value instanceof Boolean || value == null) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(toJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                json.append(toJson((List<?>) value));
            } else {
                json.append('"').append(value).append('"');
            }
        }
        json.append('}');
        return json.toString();
    }

    private static String toJson(List<?> list) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            Object value = list.get(i);
            if (value instanceof Map) {
                json.append(toJson((Map<String, Object>) value));
            } else if (value instanceof String) {
                json.append('"').append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else {
                json.append(value);
            }
        }
        json.append(']');
        return json.toString();
    }
}
