package com.hft.exchange.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hft.core.Order;
import com.hft.monitoring.PerformanceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real Coinbase API Integration with Authentication
 * Supports both market data WebSocket and private REST API endpoints
 */
public class CoinbaseRealApi implements MultiExchangeManager.ExchangeApi {
    private static final Logger logger = LoggerFactory.getLogger(CoinbaseRealApi.class);
    
    // API endpoints
    private static final String REST_BASE_URL = "https://api.exchange.coinbase.com";
    private static final String WS_BASE_URL = "wss://ws-feed.exchange.coinbase.com";
    
    // HTTP client
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final ApiKeyManager apiKeyManager;
    private final PerformanceMonitor performanceMonitor;
    
    // WebSocket connections
    private final Map<String, WebSocket> websockets = new ConcurrentHashMap<>();
    
    // Rate limiting
    private final AtomicLong requestCount = new AtomicLong(0);
    private final long[] requestTimestamps = new long[600]; // 10 minutes at 60 requests per minute
    private volatile int requestIndex = 0;
    
    // Order tracking
    private final Map<String, OrderStatus> orderStatuses = new ConcurrentHashMap<>();
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    
    public CoinbaseRealApi() {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKeyManager = ApiKeyManager.getInstance();
        this.performanceMonitor = PerformanceMonitor.getInstance();
        
        logger.info("Coinbase Real API initialized");
    }
    
    /**
     * Connect to market data WebSocket
     */
    public CompletableFuture<Void> connectMarketData(List<String> symbols) {
        if (!apiKeyManager.isExchangeConfigured("coinbase")) {
            logger.error("Coinbase API credentials not configured");
            return CompletableFuture.failedFuture(new RuntimeException("Coinbase not configured"));
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // Build subscription message
                JsonObject subscribeMessage = new JsonObject();
                subscribeMessage.addProperty("type", "subscribe");
                subscribeMessage.add("product_ids", gson.toJsonTree(symbols));
                subscribeMessage.add("channels", gson.toJsonTree(Arrays.asList("level2", "trades")));
                
                WebSocket ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(WS_BASE_URL), new CoinbaseWebSocketListener())
                    .join();
                
                // Send subscription message
                ws.sendText(subscribeMessage.toString(), true);
                
                websockets.put("marketdata", ws);
                logger.info("Connected to Coinbase market data for symbols: {}", symbols);
                
            } catch (Exception e) {
                logger.error("Failed to connect to Coinbase market data", e);
            }
        });
    }
    
    /**
     * Place real order on Coinbase
     */
    @Override
    public CompletableFuture placeOrder(Order order) {
        return CompletableFuture.supplyAsync(() -> {
            try (var measurement = performanceMonitor.startMeasurement("coinbase_order_place")) {
                
                checkRateLimit();
                
                // Build order request
                JsonObject orderRequest = new JsonObject();
                orderRequest.addProperty("client_oid", "hft_" + orderIdGenerator.getAndIncrement());
                orderRequest.addProperty("side", order.isBuy() ? "buy" : "sell");
                orderRequest.addProperty("product_id", getSymbol(order.symbolId));
                orderRequest.addProperty("type", "market"); // For HFT, use market orders
                orderRequest.addProperty("size", String.valueOf(order.quantity));
                
                // Sign request
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                String method = "POST";
                String path = "/orders";
                String body = orderRequest.toString();
                String message = timestamp + method + path + body;
                String signature = apiKeyManager.generateSignature("coinbase", message);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE_URL + path))
                    .header("CB-ACCESS-KEY", apiKeyManager.getCredentials("coinbase").apiKey)
                    .header("CB-ACCESS-SIGN", signature)
                    .header("CB-ACCESS-TIMESTAMP", timestamp)
                    .header("CB-ACCESS-PASSPHRASE", apiKeyManager.getCredentials("coinbase").passphrase)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject result = gson.fromJson(response.body(), JsonObject.class);
                    OrderResult orderResult = parseOrderResult(result);
                    
                    // Track order status
                    orderStatuses.put(orderResult.clientOrderId, new OrderStatus(orderResult.clientOrderId, orderResult));
                    
                    logger.info("Coinbase order placed successfully: {}", orderResult);
                    performanceMonitor.recordThroughput("coinbase_orders_placed", 1);
                    return orderResult;
                    
                } else {
                    logger.error("Coinbase order placement failed: {} - {}", response.statusCode(), response.body());
                    performanceMonitor.incrementCounter("coinbase_order_place_failed");
                    return new MultiExchangeManager.OrderResult("", "FAILED", response.body(), 0, 0, 0);
                }
                
            } catch (Exception e) {
                logger.error("Error placing Coinbase order", e);
                performanceMonitor.incrementCounter("coinbase_order_place_error");
                return new MultiExchangeManager.OrderResult("", "ERROR", e.getMessage(), 0, 0, 0);
            }
        });
    }
    
    /**
     * Cancel order on Coinbase
     */
    @Override
    public CompletableFuture<Boolean> cancelOrder(long orderId, String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                checkRateLimit();
                
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                String method = "DELETE";
                String path = "/orders/" + orderId;
                String message = timestamp + method + path;
                String signature = apiKeyManager.generateSignature("coinbase", message);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE_URL + path))
                    .header("CB-ACCESS-KEY", apiKeyManager.getCredentials("coinbase").apiKey)
                    .header("CB-ACCESS-SIGN", signature)
                    .header("CB-ACCESS-TIMESTAMP", timestamp)
                    .header("CB-ACCESS-PASSPHRASE", apiKeyManager.getCredentials("coinbase").passphrase)
                    .DELETE()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    logger.info("Coinbase order cancelled successfully: {}", orderId);
                    performanceMonitor.recordThroughput("coinbase_orders_cancelled", 1);
                    return true;
                } else {
                    logger.error("Coinbase order cancellation failed: {} - {}", response.statusCode(), response.body());
                    performanceMonitor.incrementCounter("coinbase_order_cancel_failed");
                    return false;
                }
                
            } catch (Exception e) {
                logger.error("Error cancelling Coinbase order", e);
                performanceMonitor.incrementCounter("coinbase_order_cancel_error");
                return false;
            }
        });
    }
    
    /**
     * Get account information
     */
    @Override
    public CompletableFuture<Object> getAccountInfo() {
        return CompletableFuture.supplyAsync(() -> {
            try (var measurement = performanceMonitor.startMeasurement("coinbase_account_info")) {
                checkRateLimit();
                
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                String method = "GET";
                String path = "/accounts";
                String message = timestamp + method + path;
                String signature = apiKeyManager.generateSignature("coinbase", message);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE_URL + path))
                    .header("CB-ACCESS-KEY", apiKeyManager.getCredentials("coinbase").apiKey)
                    .header("CB-ACCESS-SIGN", signature)
                    .header("CB-ACCESS-TIMESTAMP", timestamp)
                    .header("CB-ACCESS-PASSPHRASE", apiKeyManager.getCredentials("coinbase").passphrase)
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    // Parse account array
                    return new AccountInfo("SUCCESS", "Account info retrieved");
                } else {
                    logger.error("Failed to get Coinbase account info: {} - {}", response.statusCode(), response.body());
                    return new AccountInfo("ERROR", response.body());
                }
                
            } catch (Exception e) {
                logger.error("Error getting Coinbase account info", e);
                return new AccountInfo("ERROR", e.getMessage());
            }
        });
    }
    
    /**
     * Get order status
     */
    public CompletableFuture<OrderResult> getOrderStatus(String clientOrderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                checkRateLimit();
                
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                String method = "GET";
                String path = "/orders/" + clientOrderId;
                String message = timestamp + method + path;
                String signature = apiKeyManager.generateSignature("coinbase", message);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE_URL + path))
                    .header("CB-ACCESS-KEY", apiKeyManager.getCredentials("coinbase").apiKey)
                    .header("CB-ACCESS-SIGN", signature)
                    .header("CB-ACCESS-TIMESTAMP", timestamp)
                    .header("CB-ACCESS-PASSPHRASE", apiKeyManager.getCredentials("coinbase").passphrase)
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject result = gson.fromJson(response.body(), JsonObject.class);
                    return parseOrderResult(result);
                } else {
                    logger.error("Failed to get Coinbase order status: {} - {}", response.statusCode(), response.body());
                    return new OrderResult(clientOrderId, "ERROR", response.body(), 0, 0, 0);
                }
                
            } catch (Exception e) {
                logger.error("Error getting Coinbase order status", e);
                return new OrderResult(clientOrderId, "ERROR", e.getMessage(), 0, 0, 0);
            }
        });
    }
    
    /**
     * Check rate limits
     */
    private void checkRateLimit() {
        long now = System.currentTimeMillis();
        requestTimestamps[requestIndex] = now;
        requestIndex = (requestIndex + 1) % requestTimestamps.length;
        
        // Count requests in last minute
        long oneMinuteAgo = now - 60000;
        int requestsInMinute = 0;
        for (long timestamp : requestTimestamps) {
            if (timestamp > oneMinuteAgo) requestsInMinute++;
        }
        
        if (requestsInMinute > 50) { // Coinbase rate limit is more restrictive
            try {
                Thread.sleep(200); // Longer delay for Coinbase
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Get symbol name from ID
     */
    private String getSymbol(int symbolId) {
        // This should be implemented based on your symbol mapping
        return "BTC-USD"; // Coinbase uses different format
    }
    
    /**
     * Parse order result from API response
     */
    private OrderResult parseOrderResult(JsonObject result) {
        return new OrderResult(
            result.has("id") ? result.get("id").getAsString() : "",
            result.has("status") ? result.get("status").getAsString() : "unknown",
            result.has("client_oid") ? result.get("client_oid").getAsString() : "",
            result.has("filled_size") ? result.get("filled_size").getAsDouble() : 0,
            result.has("executed_value") ? result.get("executed_value").getAsDouble() : 0,
            result.has("price") ? result.get("price").getAsDouble() : 0
        );
    }
    
    /**
     * WebSocket listener for market data
     */
    private class CoinbaseWebSocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("Coinbase market data WebSocket opened");
            webSocket.request(1);
        }
        
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                JsonObject message = gson.fromJson(data.toString(), JsonObject.class);
                processMarketDataMessage(message);
            } catch (Exception e) {
                logger.error("Error processing Coinbase market data message", e);
            }
            if (last) {
                webSocket.request(1);
            }
            return null;
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.info("Coinbase market data WebSocket closed: {} - {}", statusCode, reason);
            return null;
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("Coinbase market data WebSocket error", error);
        }
    }
    
    /**
     * Process market data messages
     */
    private void processMarketDataMessage(JsonObject message) {
        String type = message.get("type").getAsString();
        
        switch (type) {
            case "snapshot":
                processLevel2Snapshot(message);
                break;
            case "l2update":
                processLevel2Update(message);
                break;
            case "match":
                processTrade(message);
                break;
            default:
                logger.debug("Unknown Coinbase message type: {}", type);
        }
        
        performanceMonitor.recordThroughput("coinbase_market_data_messages", 1);
    }
    
    /**
     * Process Level 2 snapshot
     */
    private void processLevel2Snapshot(JsonObject message) {
        // Process order book snapshot
        logger.debug("Received Coinbase L2 snapshot for {}", message.get("product_id"));
    }
    
    /**
     * Process Level 2 update
     */
    private void processLevel2Update(JsonObject message) {
        // Process order book updates
        logger.debug("Received Coinbase L2 update for {}", message.get("product_id"));
    }
    
    /**
     * Process trade message
     */
    private void processTrade(JsonObject message) {
        // Process trade data
        logger.debug("Received Coinbase trade for {}", message.get("product_id"));
    }
    
    /**
     * Disconnect all websockets
     */
    public void disconnect() {
        websockets.values().forEach(WebSocket::abort);
        websockets.clear();
        logger.info("Coinbase API disconnected");
    }
    
    // Data classes
    public static class OrderResult {
        public final String orderId;
        public final String status;
        public final String clientOrderId;
        public final double executedQty;
        public final double executedValue;
        public final double price;
        
        public OrderResult(String orderId, String status, String clientOrderId, 
                         double executedQty, double executedValue, double price) {
            this.orderId = orderId;
            this.status = status;
            this.clientOrderId = clientOrderId;
            this.executedQty = executedQty;
            this.executedValue = executedValue;
            this.price = price;
        }
    }
    
    public static class AccountInfo {
        public final String status;
        public final String message;
        
        public AccountInfo(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
    
    public static class OrderStatus {
        public final String clientOrderId;
        public volatile String status;
        public volatile long updateTime;
        
        public OrderStatus(String clientOrderId, OrderResult result) {
            this.clientOrderId = clientOrderId;
            this.status = result.status;
            this.updateTime = System.currentTimeMillis();
        }
        
        public void updateStatus(String newStatus) {
            this.status = newStatus;
            this.updateTime = System.currentTimeMillis();
        }
    }
}
