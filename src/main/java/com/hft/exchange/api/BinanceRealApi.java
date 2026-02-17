package com.hft.exchange.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hft.core.Order;
import com.hft.core.Tick;
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
import java.util.function.Supplier;

/**
 * Real Binance API Integration with Authentication
 * Supports both market data WebSocket and private REST API endpoints
 */
public class BinanceRealApi implements MultiExchangeManager.ExchangeApi {
    private static final Logger logger = LoggerFactory.getLogger(BinanceRealApi.class);
    
    // API endpoints
    private static final String REST_BASE_URL = "https://api.binance.com";
    private static final String WS_BASE_URL = "wss://stream.binance.com:9443/ws";
    private static final String USER_DATA_STREAM = "wss://stream.binance.com:9443/ws";
    
    // HTTP client for REST API
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final ApiKeyManager apiKeyManager;
    private final PerformanceMonitor performanceMonitor;
    
    // WebSocket connections
    private final Map<String, WebSocket> websockets = new ConcurrentHashMap<>();
    private final String listenKey;
    
    // Rate limiting
    private final AtomicLong requestCount = new AtomicLong(0);
    private final long[] requestTimestamps = new long[1200]; // 20 minutes at 100 requests per minute
    private volatile int requestIndex = 0;
    
    // Order tracking
    private final Map<Long, OrderStatus> orderStatuses = new ConcurrentHashMap<>();
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    
    public BinanceRealApi() {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKeyManager = ApiKeyManager.getInstance();
        this.performanceMonitor = PerformanceMonitor.getInstance();
        this.listenKey = apiKeyManager.isExchangeConfigured("binance") ? 
            createUserDataStream() : null;
        
        logger.info("Binance Real API initialized");
    }
    
    /**
     * Create user data stream for private events
     */
    private String createUserDataStream() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REST_BASE_URL + "/api/v3/userDataStream"))
                .header("X-MBX-APIKEY", apiKeyManager.getCredentials("binance").apiKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject result = gson.fromJson(response.body(), JsonObject.class);
                String listenKey = result.get("listenKey").getAsString();
                logger.info("Created Binance user data stream: {}", listenKey);
                return listenKey;
            } else {
                logger.error("Failed to create user data stream: {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error creating user data stream", e);
            return null;
        }
    }
    
    /**
     * Connect to market data WebSocket
     */
    public CompletableFuture<Void> connectMarketData(List<String> symbols) {
        if (!apiKeyManager.isExchangeConfigured("binance")) {
            logger.error("Binance API credentials not configured");
            return CompletableFuture.failedFuture(new RuntimeException("Binance not configured"));
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                StringBuilder streamUrl = new StringBuilder(WS_BASE_URL + "/");
                for (int i = 0; i < symbols.size(); i++) {
                    if (i > 0) streamUrl.append("/");
                    streamUrl.append(symbols.get(i).toLowerCase()).append("@depth20@100ms");
                }
                
                WebSocket ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(streamUrl.toString()), new BinanceWebSocketListener())
                    .join();
                
                websockets.put("marketdata", ws);
                logger.info("Connected to Binance market data: {}", streamUrl);
                
            } catch (Exception e) {
                logger.error("Failed to connect to Binance market data", e);
            }
        });
    }
    
    /**
     * Connect to user data stream for private events
     */
    public CompletableFuture<Void> connectUserData() {
        if (listenKey == null) {
            return CompletableFuture.failedFuture(new RuntimeException("No listen key"));
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                String userDataUrl = USER_DATA_STREAM + "/" + listenKey;
                WebSocket ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(userDataUrl), new UserDataListener())
                    .join();
                
                websockets.put("userdata", ws);
                logger.info("Connected to Binance user data stream");
                
            } catch (Exception e) {
                logger.error("Failed to connect to Binance user data", e);
            }
        });
    }
    
    /**
     * Place real order on Binance
     */
    @Override
    public CompletableFuture<MultiExchangeManager.OrderResult> placeOrder(Order order) {
        return CompletableFuture.<MultiExchangeManager.OrderResult>supplyAsync(() -> {
            try (var measurement = performanceMonitor.startMeasurement("binance_order_place")) {
                
                checkRateLimit();
                
                // Build order parameters
                Map<String, String> params = new HashMap<>();
                params.put("symbol", getSymbol(order.symbolId));
                params.put("side", order.isBuy() ? "BUY" : "SELL");
                params.put("type", "MARKET"); // For HFT, use market orders
                params.put("quantity", String.valueOf(order.quantity));
                params.put("newClientOrderId", "hft_" + orderIdGenerator.getAndIncrement());
                
                // Generate signature
                String queryString = buildQueryString(params);
                String signature = apiKeyManager.generateSignature("binance", queryString);
                String fullUrl = REST_BASE_URL + "/api/v3/order?" + queryString + "&signature=" + signature;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("X-MBX-APIKEY", apiKeyManager.getCredentials("binance").apiKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject result = gson.fromJson(response.body(), JsonObject.class);
                    MultiExchangeManager.OrderResult orderResult = parseOrderResult(result);
                    
                    // Track order status
                    orderStatuses.put(order.orderId, new OrderStatus(order.orderId, orderResult));
                    
                    logger.info("Order placed successfully: {}", orderResult);
                    performanceMonitor.recordThroughput("orders_placed", 1);
                    return orderResult;
                    
                } else {
                    logger.error("Order placement failed: {} - {}", response.statusCode(), response.body());
                    performanceMonitor.incrementCounter("order_place_failed");
                    return new MultiExchangeManager.OrderResult(
                    String.valueOf(order.orderId), "FAILED", response.body(), 0.0, 0.0, 0.0);
                }
                
            } catch (Exception e) {
                logger.error("Error placing order", e);
                performanceMonitor.incrementCounter("order_place_error");
                return new MultiExchangeManager.OrderResult(
                        String.valueOf(order.orderId), "ERROR", e.getMessage(), 0.0, 0.0, 0.0);
            }
        });
    }
    
    /**
     * Cancel order
     */
    @Override
    public CompletableFuture<Boolean> cancelOrder(long orderId, String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                checkRateLimit();
                
                Map<String, String> params = new HashMap<>();
                params.put("symbol", symbol);
                params.put("origClientOrderId", "hft_" + orderId);
                
                String queryString = buildQueryString(params);
                String signature = apiKeyManager.generateSignature("binance", queryString);
                String fullUrl = REST_BASE_URL + "/api/v3/order?" + queryString + "&signature=" + signature;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("X-MBX-APIKEY", apiKeyManager.getCredentials("binance").apiKey)
                    .DELETE()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    logger.info("Order cancelled successfully: {}", orderId);
                    performanceMonitor.recordThroughput("orders_cancelled", 1);
                    return true;
                } else {
                    logger.error("Order cancellation failed: {} - {}", response.statusCode(), response.body());
                    performanceMonitor.incrementCounter("order_cancel_failed");
                    return false;
                }
                
            } catch (Exception e) {
                logger.error("Error cancelling order", e);
                performanceMonitor.incrementCounter("order_cancel_error");
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
            try (var measurement = performanceMonitor.startMeasurement("binance_account_info")) {
                checkRateLimit();
                
                Map<String, String> params = new HashMap<>();
                String queryString = buildQueryString(params);
                String signature = apiKeyManager.generateSignature("binance", queryString);
                String fullUrl = REST_BASE_URL + "/api/v3/account?" + queryString + "&signature=" + signature;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("X-MBX-APIKEY", apiKeyManager.getCredentials("binance").apiKey)
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject result = gson.fromJson(response.body(), JsonObject.class);
                    return parseAccountInfo(result);
                } else {
                    logger.error("Failed to get account info: {} - {}", response.statusCode(), response.body());
                    return new AccountInfo("ERROR", response.body());
                }
                
            } catch (Exception e) {
                logger.error("Error getting account info", e);
                return new AccountInfo("ERROR", e.getMessage());
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
        
        if (requestsInMinute > 100) { // Binance rate limit
            try {
                Thread.sleep(100); // Small delay to respect rate limits
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Build query string for API requests
     */
    private String buildQueryString(Map<String, String> params) {
        List<String> paramList = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            paramList.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join("&", paramList);
    }
    
    /**
     * Get symbol name from ID
     */
    private String getSymbol(int symbolId) {
        // This should be implemented based on your symbol mapping
        return "BTCUSDT"; // Simplified
    }
    
    /**
     * Parse order result from API response
     */
    private MultiExchangeManager.OrderResult parseOrderResult(JsonObject result) {
        return new MultiExchangeManager.OrderResult(
            String.valueOf(result.get("orderId").getAsLong()),
            result.get("status").getAsString(),
            result.get("clientOrderId").getAsString(),
            result.get("executedQty").getAsDouble(),
            result.get("cummulativeQuoteQty").getAsDouble(),
            result.get("price").getAsDouble()
        );
    }
    
    /**
     * Parse account info from API response
     */
    private AccountInfo parseAccountInfo(JsonObject result) {
        return new AccountInfo(
            result.get("makerCommission").getAsDouble(),
            result.get("takerCommission").getAsDouble(),
            result.get("buyerCommission").getAsDouble(),
            result.get("sellerCommission").getAsDouble(),
            result.get("canTrade").getAsBoolean(),
            result.get("canWithdraw").getAsBoolean(),
            result.get("canDeposit").getAsBoolean()
        );
    }
    
    /**
     * WebSocket listener for market data
     */
    private class BinanceWebSocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("Binance market data WebSocket opened");
            webSocket.request(1); // Request first message
        }
        
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                JsonObject message = gson.fromJson(data.toString(), JsonObject.class);
                processMarketDataMessage(message);
            } catch (Exception e) {
                logger.error("Error processing market data message", e);
            }
            if (last) {
                webSocket.request(1); // Request next message
            }
            return null;
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.info("Binance market data WebSocket closed: {} - {}", statusCode, reason);
            return null;
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("Binance market data WebSocket error", error);
        }
    }
    
    /**
     * WebSocket listener for user data
     */
    private class UserDataListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("Binance user data WebSocket opened");
            webSocket.request(1);
        }
        
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                JsonObject message = gson.fromJson(data.toString(), JsonObject.class);
                processUserDataMessage(message);
            } catch (Exception e) {
                logger.error("Error processing user data message", e);
            }
            if (last) {
                webSocket.request(1);
            }
            return null;
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.info("Binance user data WebSocket closed: {} - {}", statusCode, reason);
            return null;
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("Binance user data WebSocket error", error);
        }
    }
    
    /**
     * Process market data messages
     */
    private void processMarketDataMessage(JsonObject message) {
        // Process depth updates, trades, etc.
        performanceMonitor.recordThroughput("market_data_messages", 1);
    }
    
    /**
     * Process user data messages (order updates, account updates)
     */
    private void processUserDataMessage(JsonObject message) {
        String eventType = message.get("e").getAsString();
        
        switch (eventType) {
            case "executionReport":
                processOrderUpdate(message);
                break;
            case "balanceUpdate":
                processBalanceUpdate(message);
                break;
            default:
                logger.debug("Unknown user data event type: {}", eventType);
        }
        
        performanceMonitor.recordThroughput("user_data_messages", 1);
    }
    
    /**
     * Process order update from user data stream
     */
    private void processOrderUpdate(JsonObject message) {
        long clientOrderId = Long.parseLong(message.get("c").getAsString().replace("hft_", ""));
        String orderStatus = message.get("X").getAsString();
        
        OrderStatus status = orderStatuses.get(clientOrderId);
        if (status != null) {
            status.updateStatus(orderStatus);
            logger.info("Order {} updated to status: {}", clientOrderId, orderStatus);
        }
    }
    
    /**
     * Process balance update
     */
    private void processBalanceUpdate(JsonObject message) {
        logger.info("Balance update received: {}", message);
    }
    
    /**
     * Disconnect all websockets
     */
    public void disconnect() {
        websockets.values().forEach(WebSocket::abort);
        websockets.clear();
        logger.info("Binance API disconnected");
    }
    
    // Data classes
    public static class AccountInfo {
        public final String status;
        public final String message;
        public final double makerCommission;
        public final double takerCommission;
        public final double buyerCommission;
        public final double sellerCommission;
        public final boolean canTrade;
        public final boolean canWithdraw;
        public final boolean canDeposit;
        
        public AccountInfo(String status, String message) {
            this.status = status;
            this.message = message;
            this.makerCommission = 0;
            this.takerCommission = 0;
            this.buyerCommission = 0;
            this.sellerCommission = 0;
            this.canTrade = false;
            this.canWithdraw = false;
            this.canDeposit = false;
        }
        
        public AccountInfo(double makerCommission, double takerCommission, 
                          double buyerCommission, double sellerCommission,
                          boolean canTrade, boolean canWithdraw, boolean canDeposit) {
            this.status = "SUCCESS";
            this.message = "";
            this.makerCommission = makerCommission;
            this.takerCommission = takerCommission;
            this.buyerCommission = buyerCommission;
            this.sellerCommission = sellerCommission;
            this.canTrade = canTrade;
            this.canWithdraw = canWithdraw;
            this.canDeposit = canDeposit;
        }
    }
    
    public static class OrderStatus {
        public final long orderId;
        public volatile String status;
        public volatile long updateTime;
        
        public OrderStatus(long orderId, MultiExchangeManager.OrderResult result) {
            this.orderId = orderId;
            this.status = result.status;
            this.updateTime = System.currentTimeMillis();
        }
        
        public void updateStatus(String newStatus) {
            this.status = newStatus;
            this.updateTime = System.currentTimeMillis();
        }
    }
}
