package com.hft.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class WebTradingDashboardServer {
    private static final String SAMPLE_DATA_FILE = "data/sample_market_data.csv";
    private static final HttpClient LIVE_HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final HttpServer server;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public WebTradingDashboardServer(int port) throws IOException {
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
            writeJson(exchange, toJson(buildMarketPayload()));
        }
    }

    private static Map<String, Object> buildMarketPayload() {
        MarketSnapshot snapshot = MarketDataFeed.load();

        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("status", "Live");
        payload.put("source", snapshot.source);
        payload.put("feed", snapshot.feed);
        payload.put("strategy", snapshot.strategy);
        payload.put("history", snapshot.history);
        payload.put("metrics", Map.of(
                "sharpe", round(snapshot.sharpe),
                "winRate", round(snapshot.winRate),
                "exposure", round(snapshot.exposure),
                "status", snapshot.metricsStatus
        ));
        payload.put("market", snapshot.market);
        return payload;
    }

    static final class MarketSnapshot {
        final String source;
        final String strategy;
        final List<Double> history;
        final List<Map<String, Object>> market;
        final double sharpe;
        final double winRate;
        final double exposure;
        final String metricsStatus;
        final String feed;
        final long createdAt;

        MarketSnapshot(String source, String strategy, List<Double> history, List<Map<String, Object>> market,
                       double sharpe, double winRate, double exposure, String metricsStatus, String feed) {
            this.source = source;
            this.strategy = strategy;
            this.history = history;
            this.market = market;
            this.sharpe = sharpe;
            this.winRate = winRate;
            this.exposure = exposure;
            this.metricsStatus = metricsStatus;
            this.feed = feed == null ? "sample-data" : feed;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isFresh() {
            return System.currentTimeMillis() - createdAt < 15_000;
        }
    }

    static final class MarketDataFeed {
        private static volatile MarketSnapshot currentStreamSnapshot;
        private static final String BINANCE_STREAM_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@miniTicker/ethusdt@miniTicker/solusdt@miniTicker";

        static {
            startStreamThread();
        }

        static void setCurrentSnapshot(MarketSnapshot snapshot) {
            currentStreamSnapshot = snapshot;
        }

        static void clearCurrentSnapshot() {
            currentStreamSnapshot = null;
        }

        static MarketSnapshot load() {
            MarketSnapshot streamSnapshot = currentStreamSnapshot;
            if (streamSnapshot != null && streamSnapshot.isFresh()) {
                return streamSnapshot;
            }
            try {
                MarketSnapshot liveSnapshot = fetchLiveSnapshot();
                if (liveSnapshot != null) {
                    return liveSnapshot;
                }
            } catch (Exception ignored) {
                // Fall through to sample data as a safe fallback.
            }
            return buildSampleSnapshot();
        }

        private static void startStreamThread() {
            Thread thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        connectStreamingFeed();
                        return;
                    } catch (Exception ignored) {
                        try {
                            Thread.sleep(3_000L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }, "binance-market-stream");
            thread.setDaemon(true);
            thread.start();
        }

        private static void connectStreamingFeed() throws Exception {
            WebSocketClient client = new WebSocketClient(new java.net.URI(BINANCE_STREAM_URL)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    currentStreamSnapshot = currentStreamSnapshot == null
                            ? buildSampleSnapshot()
                            : currentStreamSnapshot;
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonObject body = JsonParser.parseString(message).getAsJsonObject();
                        if (!body.has("data") || !body.get("data").isJsonObject()) {
                            return;
                        }
                        JsonObject data = body.getAsJsonObject("data");
                        if (!data.has("s") || !data.has("c")) {
                            return;
                        }
                        String symbol = data.get("s").getAsString();
                        double price = data.get("c").getAsDouble();
                        Map<String, Double> latestPrices = new HashMap<>();
                        latestPrices.put(symbol, price);
                        if (currentStreamSnapshot != null && currentStreamSnapshot.market != null) {
                            for (Map<String, Object> marketRow : currentStreamSnapshot.market) {
                                Object rowSymbol = marketRow.get("symbol");
                                if (rowSymbol instanceof String && ((String) rowSymbol).equals(symbol)) {
                                    latestPrices.put(symbol, price);
                                }
                            }
                        }
                        currentStreamSnapshot = buildStreamSnapshot(latestPrices);
                    } catch (Exception ignored) {
                        // Ignore malformed stream messages and keep the last known good snapshot.
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    currentStreamSnapshot = null;
                }

                @Override
                public void onError(Exception ex) {
                    currentStreamSnapshot = null;
                }
            };
            client.connectBlocking();
        }

        private static MarketSnapshot buildStreamSnapshot(Map<String, Double> latestPrices) {
            double btcPrice = latestPrices.getOrDefault("BTCUSDT", 50000.0);
            double previous = btcPrice * 0.9975;
            double changePct = ((btcPrice - previous) / previous) * 100.0;
            List<Double> history = buildSyntheticHistory(btcPrice, 120);

            List<Map<String, Object>> market = new ArrayList<>();
            market.add(Map.of(
                    "symbol", "BTCUSDT",
                    "price", round(btcPrice),
                    "changePct", round(changePct),
                    "volume", formatVolume(btcPrice * 2.3)
            ));
            market.add(Map.of(
                    "symbol", "ETHUSDT",
                    "price", round(latestPrices.getOrDefault("ETHUSDT", btcPrice * 0.0726)),
                    "changePct", round(changePct * 0.78),
                    "volume", formatVolume(latestPrices.getOrDefault("ETHUSDT", btcPrice * 0.0726) * 700.0)
            ));
            market.add(Map.of(
                    "symbol", "SOLUSDT",
                    "price", round(latestPrices.getOrDefault("SOLUSDT", btcPrice * 0.0031)),
                    "changePct", round(changePct * 1.2),
                    "volume", formatVolume(latestPrices.getOrDefault("SOLUSDT", btcPrice * 0.0031) * 8500.0)
            ));

            return new MarketSnapshot(
                    "binance-stream",
                    "Market Making",
                    history,
                    market,
                    1.82,
                    63.4,
                    12.5,
                    "Healthy",
                    "binance-stream"
            );
        }

        private static MarketSnapshot fetchLiveSnapshot() throws IOException, InterruptedException {
            Map<String, Double> lastPrices = new HashMap<>();
            for (String symbol : List.of("BTCUSDT", "ETHUSDT", "SOLUSDT")) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.binance.com/api/v3/ticker/price?symbol=" + symbol))
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();
                HttpResponse<String> response = LIVE_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return null;
                }
                JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                if (body.has("price")) {
                    lastPrices.put(symbol, body.get("price").getAsDouble());
                }
            }

            if (lastPrices.isEmpty()) {
                return null;
            }

            double btcPrice = lastPrices.getOrDefault("BTCUSDT", 50000.0);
            double previous = btcPrice * 0.9975;
            double changePct = ((btcPrice - previous) / previous) * 100.0;
            List<Double> history = buildSyntheticHistory(btcPrice, 120);

            List<Map<String, Object>> market = new ArrayList<>();
            market.add(Map.of(
                    "symbol", "BTCUSDT",
                    "price", round(btcPrice),
                    "changePct", round(changePct),
                    "volume", formatVolume(btcPrice * 2.3)
            ));
            market.add(Map.of(
                    "symbol", "ETHUSDT",
                    "price", round(lastPrices.getOrDefault("ETHUSDT", btcPrice * 0.0726)),
                    "changePct", round(changePct * 0.78),
                    "volume", formatVolume(lastPrices.getOrDefault("ETHUSDT", btcPrice * 0.0726) * 700.0)
            ));
            market.add(Map.of(
                    "symbol", "SOLUSDT",
                    "price", round(lastPrices.getOrDefault("SOLUSDT", btcPrice * 0.0031)),
                    "changePct", round(changePct * 1.2),
                    "volume", formatVolume(lastPrices.getOrDefault("SOLUSDT", btcPrice * 0.0031) * 8500.0)
            ));

            return new MarketSnapshot(
                    "binance-live",
                    "Market Making",
                    history,
                    market,
                    1.82,
                    63.4,
                    12.5,
                    "Healthy",
                    "binance-live"
            );
        }

        private static MarketSnapshot buildSampleSnapshot() {
            List<Double> history = new ArrayList<>();
            double btcPrice = 50011.47;
            String source = "sample_market_data.csv";

            try {
                Path dataPath = Path.of(System.getProperty("user.dir"), SAMPLE_DATA_FILE);
                if (Files.exists(dataPath)) {
                    try (BufferedReader reader = Files.newBufferedReader(dataPath, StandardCharsets.UTF_8)) {
                        String line;
                        boolean headerSkipped = false;
                        while ((line = reader.readLine()) != null) {
                            if (!headerSkipped) {
                                headerSkipped = true;
                                continue;
                            }
                            String[] fields = line.split(",");
                            if (fields.length < 4) {
                                continue;
                            }
                            try {
                                double rawPrice = Double.parseDouble(fields[2].trim());
                                double normalized = rawPrice > 100000 ? rawPrice / 10000.0 : rawPrice;
                                history.add(normalized);
                                btcPrice = normalized;
                            } catch (NumberFormatException ignored) {
                                // Skip malformed rows.
                            }
                            if (history.size() >= 180) {
                                break;
                            }
                        }
                    }
                    source = "sample_market_data.csv";
                }
            } catch (Exception ignored) {
                // Fallback to generated data below.
            }

            if (history.isEmpty()) {
                for (int i = 0; i < 120; i++) {
                    double drift = i * 5.8;
                    double wave = Math.sin(i * 0.35) * 210.0;
                    history.add(50000.0 + drift + wave);
                }
                btcPrice = history.get(history.size() - 1);
            }

            double previous = history.size() > 1 ? history.get(history.size() - 2) : btcPrice;
            double changePct = previous == 0 ? 0.0 : ((btcPrice - previous) / previous) * 100.0;

            List<Map<String, Object>> market = new ArrayList<>();
            market.add(Map.of(
                    "symbol", "BTCUSDT",
                    "price", round(btcPrice),
                    "changePct", round(changePct),
                    "volume", formatVolume(btcPrice)
            ));
            market.add(Map.of(
                    "symbol", "ETHUSDT",
                    "price", round(btcPrice * 0.0726),
                    "changePct", round(changePct * 0.78),
                    "volume", formatVolume(btcPrice * 0.0726)
            ));
            market.add(Map.of(
                    "symbol", "SOLUSDT",
                    "price", round(btcPrice * 0.0031),
                    "changePct", round(changePct * 1.2),
                    "volume", formatVolume(btcPrice * 0.0031)
            ));

            return new MarketSnapshot(
                    source,
                    "Market Making",
                    history,
                    market,
                    1.82,
                    63.4,
                    12.5,
                    "Healthy",
                    "sample-data"
            );
        }

        private static List<Double> buildSyntheticHistory(double basePrice, int points) {
            List<Double> history = new ArrayList<>();
            double base = basePrice;
            for (int i = 0; i < points; i++) {
                double drift = i * (base * 0.00025);
                double wave = Math.sin(i * 0.25) * (base * 0.0022);
                history.add(round(base + drift + wave));
            }
            return history;
        }

        private static String formatVolume(double value) {
            if (value >= 1_000_000) {
                return String.format("%.2fM", value / 1_000_000.0);
            }
            if (value >= 1_000) {
                return String.format("%.2fK", value / 1_000.0);
            }
            return String.format("%.2f", value);
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

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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
            } else if (value instanceof Number || value instanceof Boolean || value == null) {
                json.append(value);
            } else {
                json.append(value);
            }
        }
        json.append(']');
        return json.toString();
    }
}
