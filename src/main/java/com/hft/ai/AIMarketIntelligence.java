package com.hft.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * AI Market Intelligence Integration
 * Integrates Gemini, Perplexity, and other AI tools for:
 * - News sentiment analysis
 * - Market trend prediction
 * - Event-driven trading signals
 * - Risk assessment enhancement
 */
public class AIMarketIntelligence {
    private static final Logger logger = LoggerFactory.getLogger(AIMarketIntelligence.class);
    
    // AI API configurations
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
    private static final String PERPLEXITY_API_URL = "https://api.perplexity.ai/chat/completions";
    
    // API Keys (should be loaded from environment variables)
    private final String geminiApiKey;
    private final String perplexityApiKey;
    
    // Cache for AI responses
    private final Map<String, AIResponse> responseCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    // Market data integration
    private volatile double currentBTCPrice = 0.0;
    private volatile double currentETHPrice = 0.0;
    private volatile String marketSentiment = "NEUTRAL";
    private volatile double marketConfidence = 0.5;
    
    // AI-driven signals
    private volatile boolean newsAlert = false;
    private volatile boolean volatilityAlert = false;
    private volatile boolean trendReversalAlert = false;
    
    public AIMarketIntelligence() {
        // Load API keys from environment or config
        this.geminiApiKey = System.getenv().getOrDefault("GEMINI_API_KEY", "your-gemini-key");
        this.perplexityApiKey = System.getenv().getOrDefault("PERPLEXITY_API_KEY", "your-perplexity-key");
        
        logger.info("AI Market Intelligence initialized");
        logger.info("Gemini API: {}", geminiApiKey != null && !geminiApiKey.equals("your-gemini-key") ? "Configured" : "Not configured");
        logger.info("Perplexity API: {}", perplexityApiKey != null && !perplexityApiKey.equals("your-perplexity-key") ? "Configured" : "Not configured");
        
        // Start periodic AI analysis
        startPeriodicAnalysis();
    }
    
    /**
     * Start periodic AI analysis
     */
    private void startPeriodicAnalysis() {
        // News sentiment analysis every 5 minutes
        scheduler.scheduleAtFixedRate(this::analyzeNewsSentiment, 0, 5, TimeUnit.MINUTES);
        
        // Market trend analysis every 2 minutes
        scheduler.scheduleAtFixedRate(this::analyzeMarketTrends, 1, 2, TimeUnit.MINUTES);
        
        // Risk assessment every 30 seconds
        scheduler.scheduleAtFixedRate(this::assessMarketRisk, 2, 30, TimeUnit.SECONDS);
        
        // Event monitoring every minute
        scheduler.scheduleAtFixedRate(this::monitorMarketEvents, 3, 1, TimeUnit.MINUTES);
        
        logger.info("AI analysis tasks scheduled");
    }
    
    /**
     * Analyze news sentiment using Gemini AI
     */
    public CompletableFuture<String> analyzeNewsSentiment() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildNewsAnalysisPrompt();
                String response = callGeminiAPI(prompt);
                
                // Parse sentiment from response
                marketSentiment = extractSentiment(response);
                marketConfidence = extractConfidence(response);
                
                logger.info("News sentiment updated: {} (confidence: {})", marketSentiment, marketConfidence);
                
                // Check for significant sentiment changes
                if (Math.abs(marketConfidence - 0.5) > 0.3) {
                    newsAlert = true;
                    logger.warn("Significant news sentiment detected: {}", marketSentiment);
                }
                
                return response;
                
            } catch (Exception e) {
                logger.error("Error analyzing news sentiment", e);
                return "ERROR";
            }
        });
    }
    
    /**
     * Analyze market trends using Perplexity AI
     */
    public CompletableFuture<String> analyzeMarketTrends() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildTrendAnalysisPrompt();
                String response = callPerplexityAPI(prompt);
                
                // Extract trend signals
                String trend = extractTrend(response);
                double strength = extractTrendStrength(response);
                
                logger.info("Market trend: {} (strength: {})", trend, strength);
                
                // Check for trend reversals
                if (strength > 0.7 && !trend.equals("NEUTRAL")) {
                    trendReversalAlert = true;
                    logger.warn("Strong trend signal detected: {}", trend);
                }
                
                return response;
                
            } catch (Exception e) {
                logger.error("Error analyzing market trends", e);
                return "ERROR";
            }
        });
    }
    
    /**
     * Assess market risk using AI analysis
     */
    public CompletableFuture<String> assessMarketRisk() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildRiskAssessmentPrompt();
                String response = callGeminiAPI(prompt);
                
                // Extract risk metrics
                double riskScore = extractRiskScore(response);
                String riskFactors = extractRiskFactors(response);
                
                logger.info("Market risk score: {} ({})", riskScore, riskFactors);
                
                // Check for high volatility
                if (riskScore > 0.7) {
                    volatilityAlert = true;
                    logger.warn("High market risk detected: {}", riskScore);
                }
                
                return response;
                
            } catch (Exception e) {
                logger.error("Error assessing market risk", e);
                return "ERROR";
            }
        });
    }
    
    /**
     * Monitor market events using AI
     */
    public CompletableFuture<String> monitorMarketEvents() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildEventMonitoringPrompt();
                String response = callPerplexityAPI(prompt);
                
                // Extract market events
                List<String> events = extractMarketEvents(response);
                
                if (!events.isEmpty()) {
                    logger.info("Market events detected: {}", events);
                    // Process events and generate trading signals
                    processMarketEvents(events);
                }
                
                return response;
                
            } catch (Exception e) {
                logger.error("Error monitoring market events", e);
                return "ERROR";
            }
        });
    }
    
    /**
     * Call Gemini API
     */
    private String callGeminiAPI(String prompt) throws Exception {
        if (geminiApiKey.equals("your-gemini-key")) {
            logger.warn("Gemini API key not configured, returning mock response");
            return generateMockGeminiResponse(prompt);
        }
        
        URL url = new URL(GEMINI_API_URL + "?key=" + geminiApiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        // Build request body
        JsonObject requestBody = new JsonObject();
        JsonObject content = new JsonObject();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        content.add("parts", new Gson().toJsonTree(new Object[]{part}));
        requestBody.add("contents", new Gson().toJsonTree(new Object[]{content}));
        
        // Send request
        conn.getOutputStream().write(requestBody.toString().getBytes());
        
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return response.toString();
    }
    
    /**
     * Call Perplexity API
     */
    private String callPerplexityAPI(String prompt) throws Exception {
        if (perplexityApiKey.equals("your-perplexity-key")) {
            logger.warn("Perplexity API key not configured, returning mock response");
            return generateMockPerplexityResponse(prompt);
        }
        
        URL url = new URL(PERPLEXITY_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + perplexityApiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        // Build request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "llama-3.1-sonar-small-128k-online");
        requestBody.addProperty("messages", "[{\"role\": \"user\", \"content\": \"" + prompt + "\"}]");
        
        // Send request
        conn.getOutputStream().write(requestBody.toString().getBytes());
        
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return response.toString();
    }
    
    /**
     * Build news analysis prompt
     */
    private String buildNewsAnalysisPrompt() {
        return String.format(
            "Analyze the current cryptocurrency market sentiment based on recent news.\n" +
            "Current market data:\n" +
            "- BTC Price: $%.2f\n" +
            "- ETH Price: $%.2f\n" +
            "- Current sentiment: %s\n" +
            "\n" +
            "Please provide:\n" +
            "1. Overall market sentiment (BULLISH/BEARISH/NEUTRAL)\n" +
            "2. Confidence level (0.0-1.0)\n" +
            "3. Key news drivers\n" +
            "4. Expected short-term impact\n" +
            "\n" +
            "Focus on crypto-specific news, regulatory developments, and market events.",
            currentBTCPrice, currentETHPrice, marketSentiment);
    }
    
    /**
     * Build trend analysis prompt
     */
    private String buildTrendAnalysisPrompt() {
        return String.format(
            "Analyze current cryptocurrency market trends.\n" +
            "Current prices:\n" +
            "- BTC: $%.2f\n" +
            "- ETH: $%.2f\n" +
            "\n" +
            "Technical indicators suggest:\n" +
            "- Recent volume trends\n" +
            "- Price momentum\n" +
            "- Market correlations\n" +
            "\n" +
            "Provide:\n" +
            "1. Current trend direction (UPTREND/DOWNTREND/SIDEWAYS)\n" +
            "2. Trend strength (0.0-1.0)\n" +
            "3. Key support/resistance levels\n" +
            "4. Expected next 24h movement",
            currentBTCPrice, currentETHPrice);
    }
    
    /**
     * Build risk assessment prompt
     */
    private String buildRiskAssessmentPrompt() {
        return String.format(
            "Assess current cryptocurrency market risk.\n" +
            "Market conditions:\n" +
            "- BTC: $%.2f\n" +
            "- ETH: $%.2f\n" +
            "- Current sentiment: %s\n" +
            "- Alert status: News=%s, Volatility=%s, Trend=%s\n" +
            "\n" +
            "Evaluate:\n" +
            "1. Overall risk score (0.0-1.0)\n" +
            "2. Primary risk factors\n" +
            "3. Recommended position sizing\n" +
            "4. Stop-loss recommendations",
            currentBTCPrice, currentETHPrice, marketSentiment, newsAlert, volatilityAlert, trendReversalAlert);
    }
    
    /**
     * Build event monitoring prompt
     */
    private String buildEventMonitoringPrompt() {
        return String.format(
            "Monitor for significant cryptocurrency market events.\n" +
            "Current market state:\n" +
            "- BTC: $%.2f\n" +
            "- ETH: $%.2f\n" +
            "- Market alerts: %s\n" +
            "\n" +
            "Identify:\n" +
            "1. Major news events\n" +
            "2. Regulatory announcements\n" +
            "3. Exchange activities\n" +
            "4. Whale movements\n" +
            "5. Technical breakouts\n" +
            "\n" +
            "List events with potential trading impact.",
            currentBTCPrice, currentETHPrice, getAlertStatus());
    }
    
    /**
     * Extract sentiment from AI response
     */
    private String extractSentiment(String response) {
        // Simple sentiment extraction - in production, use proper JSON parsing
        if (response.toLowerCase().contains("bullish")) return "BULLISH";
        if (response.toLowerCase().contains("bearish")) return "BEARISH";
        return "NEUTRAL";
    }
    
    /**
     * Extract confidence from AI response
     */
    private double extractConfidence(String response) {
        // Simple confidence extraction - in production, use proper parsing
        if (response.toLowerCase().contains("high")) return 0.8;
        if (response.toLowerCase().contains("low")) return 0.3;
        return 0.5;
    }
    
    /**
     * Extract trend from AI response
     */
    private String extractTrend(String response) {
        if (response.toLowerCase().contains("uptrend")) return "UPTREND";
        if (response.toLowerCase().contains("downtrend")) return "DOWNTREND";
        return "SIDEWAYS";
    }
    
    /**
     * Extract trend strength from AI response
     */
    private double extractTrendStrength(String response) {
        if (response.toLowerCase().contains("strong")) return 0.8;
        if (response.toLowerCase().contains("weak")) return 0.3;
        return 0.5;
    }
    
    /**
     * Extract risk score from AI response
     */
    private double extractRiskScore(String response) {
        if (response.toLowerCase().contains("high risk")) return 0.8;
        if (response.toLowerCase().contains("low risk")) return 0.2;
        return 0.5;
    }
    
    /**
     * Extract risk factors from AI response
     */
    private String extractRiskFactors(String response) {
        if (response.toLowerCase().contains("volatility")) return "High Volatility";
        if (response.toLowerCase().contains("regulation")) return "Regulatory Risk";
        return "Market Risk";
    }
    
    /**
     * Extract market events from AI response
     */
    private List<String> extractMarketEvents(String response) {
        List<String> events = new ArrayList<>();
        // Simple event extraction - in production, use proper parsing
        if (response.toLowerCase().contains("news")) events.add("Significant News Event");
        if (response.toLowerCase().contains("regulation")) events.add("Regulatory Announcement");
        if (response.toLowerCase().contains("exchange")) events.add("Exchange Activity");
        return events;
    }
    
    /**
     * Process market events and generate signals
     */
    private void processMarketEvents(List<String> events) {
        for (String event : events) {
            logger.info("Processing market event: {}", event);
            // Generate trading signals based on events
            // This would integrate with your trading strategies
        }
    }
    
    /**
     * Get alert status
     */
    private String getAlertStatus() {
        List<String> alerts = new ArrayList<>();
        if (newsAlert) alerts.add("News");
        if (volatilityAlert) alerts.add("Volatility");
        if (trendReversalAlert) alerts.add("Trend");
        return alerts.isEmpty() ? "None" : String.join(",", alerts);
    }
    
    /**
     * Generate mock Gemini response for testing
     */
    private String generateMockGeminiResponse(String prompt) {
        return "{\n" +
               "  \"candidates\": [{\n" +
               "    \"content\": {\n" +
               "      \"parts\": [{\n" +
               "        \"text\": \"Market sentiment: BULLISH with 0.75 confidence. Key drivers: positive regulatory news, institutional adoption.\"\n" +
               "      }]\n" +
               "    }\n" +
               "  }]\n" +
               "}";
    }
    
    /**
     * Generate mock Perplexity response for testing
     */
    private String generateMockPerplexityResponse(String prompt) {
        return "{\n" +
               "  \"choices\": [{\n" +
               "    \"message\": {\n" +
               "      \"content\": \"Current trend: UPTREND with 0.8 strength. Key support at $42,000, resistance at $44,000.\"\n" +
               "    }\n" +
               "  }]\n" +
               "}";
    }
    
    /**
     * Update market prices
     */
    public void updateMarketPrices(double btcPrice, double ethPrice) {
        this.currentBTCPrice = btcPrice;
        this.currentETHPrice = ethPrice;
    }
    
    /**
     * Get AI-driven trading signals
     */
    public AISignals getAISignals() {
        return new AISignals(
            marketSentiment,
            marketConfidence,
            newsAlert,
            volatilityAlert,
            trendReversalAlert
        );
    }
    
    /**
     * AI Response data structure
     */
    public static class AIResponse {
        public final String response;
        public final long timestamp;
        public final String source;
        
        public AIResponse(String response, String source) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
            this.source = source;
        }
    }
    
    /**
     * AI Trading Signals
     */
    public static class AISignals {
        public final String sentiment;
        public final double confidence;
        public final boolean newsAlert;
        public final boolean volatilityAlert;
        public final boolean trendReversalAlert;
        
        public AISignals(String sentiment, double confidence, boolean newsAlert, 
                         boolean volatilityAlert, boolean trendReversalAlert) {
            this.sentiment = sentiment;
            this.confidence = confidence;
            this.newsAlert = newsAlert;
            this.volatilityAlert = volatilityAlert;
            this.trendReversalAlert = trendReversalAlert;
        }
        
        @Override
        public String toString() {
            return String.format("AISignals{sentiment=%s, confidence=%.2f, news=%s, volatility=%s, reversal=%s}",
                sentiment, confidence, newsAlert, volatilityAlert, trendReversalAlert);
        }
    }
    
    /**
     * Shutdown AI services
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        logger.info("AI Market Intelligence shutdown complete");
    }
}
