package com.hft.strategy;

import com.hft.ai.AIMarketIntelligence;
import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI-Enhanced Trading Strategy
 * Integrates AI market intelligence for superior trading decisions:
 * - Sentiment-driven position sizing
 * - News event-based trading
 * - Risk-adjusted entries/exits
 * - Trend confirmation with AI
 */
public class AIEnhancedStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(AIEnhancedStrategy.class);
    
    private final int symbolId;
    private final int baseOrderSize;
    private final long maxPosition;
    
    // AI Integration
    private final AIMarketIntelligence aiIntelligence;
    private AIMarketIntelligence.AISignals lastAISignals;
    
    // Strategy state
    private final AtomicLong orderIdGenerator = new AtomicLong(2000);
    private long currentPosition = 0;
    private double totalPnL = 0.0;
    private long lastTradeTime = 0;
    private final long minTimeBetweenTrades = 500_000_000; // 500ms
    
    // AI-driven parameters
    private double aiSentimentMultiplier = 1.0;
    private boolean aiNewsMode = false;
    private boolean aiRiskMode = false;
    
    // Performance tracking
    private int aiTrades = 0;
    private int profitableAITrades = 0;
    private double aiPnL = 0.0;
    
    public AIEnhancedStrategy(int symbolId, int baseOrderSize, long maxPosition) {
        this.symbolId = symbolId;
        this.baseOrderSize = baseOrderSize;
        this.maxPosition = maxPosition;
        this.aiIntelligence = new AIMarketIntelligence();
        
        logger.info("AI-Enhanced Strategy initialized for symbol {}", symbolId);
        logger.info("Base order size: {}, Max position: {}", baseOrderSize, maxPosition);
    }
    
    @Override
    public void initialize() {
        logger.info("Initializing AI-Enhanced Trading Strategy");
        logger.info("AI Components: Gemini, Perplexity, News Analysis, Risk Assessment");
        
        // Start AI monitoring
        aiIntelligence.updateMarketPrices(0.0, 0.0); // Will be updated with real prices
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (tick.symbolId != symbolId) {
            return orders;
        }
        
        // Update AI with current market prices
        aiIntelligence.updateMarketPrices(tick.getPriceAsDouble(), 0.0);
        
        // Get latest AI signals
        lastAISignals = aiIntelligence.getAISignals();
        
        // Rate limiting
        long now = System.nanoTime();
        if (now - lastTradeTime < minTimeBetweenTrades) {
            return orders;
        }
        
        // AI-driven trading logic
        orders.addAll(generateSentimentBasedOrders(tick, orderBook));
        orders.addAll(generateNewsBasedOrders(tick, orderBook));
        orders.addAll(generateRiskBasedOrders(tick, orderBook));
        
        if (!orders.isEmpty()) {
            lastTradeTime = now;
            aiTrades++;
            logger.info("AI generated {} orders based on signals: {}", orders.size(), lastAISignals);
        }
        
        return orders;
    }
    
    /**
     * Generate orders based on AI sentiment analysis
     */
    private List<Order> generateSentimentBasedOrders(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (lastAISignals == null) {
            return orders;
        }
        
        // Calculate AI-adjusted position size
        double sentimentMultiplier = calculateSentimentMultiplier();
        int adjustedOrderSize = (int) (baseOrderSize * sentimentMultiplier);
        
        // Bullish sentiment - buy signal
        if (lastAISignals.sentiment.equals("BULLISH") && 
            lastAISignals.confidence > 0.6 && 
            currentPosition < maxPosition) {
            
            Order buyOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price, // Market price
                adjustedOrderSize,
                (byte)0, // Buy
                (byte)1  // Market order
            );
            orders.add(buyOrder);
            
            logger.info("AI BULLISH signal: Buy {} units at ${}", 
                adjustedOrderSize, tick.getPriceAsDouble());
        }
        
        // Bearish sentiment - sell signal
        else if (lastAISignals.sentiment.equals("BEARISH") && 
                 lastAISignals.confidence > 0.6 && 
                 currentPosition > -maxPosition) {
            
            Order sellOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price,
                adjustedOrderSize,
                (byte)1, // Sell
                (byte)1  // Market order
            );
            orders.add(sellOrder);
            
            logger.info("AI BEARISH signal: Sell {} units at ${}", 
                adjustedOrderSize, tick.getPriceAsDouble());
        }
        
        return orders;
    }
    
    /**
     * Generate orders based on AI news alerts
     */
    private List<Order> generateNewsBasedOrders(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (lastAISignals == null || !lastAISignals.newsAlert) {
            return orders;
        }
        
        // News-driven trading with larger positions
        int newsOrderSize = (int) (baseOrderSize * 2.0); // Double size for news events
        
        // Strong bullish news - aggressive buy
        if (lastAISignals.sentiment.equals("BULLISH") && 
            lastAISignals.confidence > 0.7 && 
            currentPosition < maxPosition) {
            
            Order buyOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price,
                newsOrderSize,
                (byte)0, // Buy
                (byte)1  // Market order
            );
            orders.add(buyOrder);
            
            aiNewsMode = true;
            logger.info("AI NEWS ALERT: Aggressive buy {} units at ${}", 
                newsOrderSize, tick.getPriceAsDouble());
        }
        
        // Strong bearish news - aggressive sell
        else if (lastAISignals.sentiment.equals("BEARISH") && 
                 lastAISignals.confidence > 0.7 && 
                 currentPosition > -maxPosition) {
            
            Order sellOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price,
                newsOrderSize,
                (byte)1, // Sell
                (byte)1  // Market order
            );
            orders.add(sellOrder);
            
            aiNewsMode = true;
            logger.info("AI NEWS ALERT: Aggressive sell {} units at ${}", 
                newsOrderSize, tick.getPriceAsDouble());
        }
        
        return orders;
    }
    
    /**
     * Generate orders based on AI risk assessment
     */
    private List<Order> generateRiskBasedOrders(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (lastAISignals == null) {
            return orders;
        }
        
        // High volatility alert - reduce position
        if (lastAISignals.volatilityAlert && Math.abs(currentPosition) > baseOrderSize) {
            int reduceSize = (int) (Math.abs(currentPosition) * 0.5); // Reduce by 50%
            
            Order reduceOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price,
                reduceSize,
                currentPosition > 0 ? (byte)1 : (byte)0, // Opposite of current position
                (byte)1  // Market order
            );
            orders.add(reduceOrder);
            
            aiRiskMode = true;
            logger.info("AI VOLATILITY ALERT: Reducing position by {} units", reduceSize);
        }
        
        // Trend reversal alert - close current position
        if (lastAISignals.trendReversalAlert && currentPosition != 0) {
            Order closeOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                tick.price,
                Math.abs(currentPosition),
                currentPosition > 0 ? (byte)1 : (byte)0, // Close position
                (byte)1  // Market order
            );
            orders.add(closeOrder);
            
            logger.info("AI TREND REVERSAL: Closing position of {} units", Math.abs(currentPosition));
        }
        
        return orders;
    }
    
    /**
     * Calculate sentiment-based position size multiplier
     */
    private double calculateSentimentMultiplier() {
        if (lastAISignals == null) {
            return 1.0;
        }
        
        double multiplier = 1.0;
        
        // Adjust based on sentiment
        switch (lastAISignals.sentiment) {
            case "BULLISH":
                multiplier = 0.5 + (lastAISignals.confidence * 1.5); // 0.5 to 2.0
                break;
            case "BEARISH":
                multiplier = 0.5 + (lastAISignals.confidence * 1.5); // 0.5 to 2.0
                break;
            case "NEUTRAL":
                multiplier = 0.5; // Reduce size in neutral markets
                break;
        }
        
        // Reduce size during high volatility
        if (lastAISignals.volatilityAlert) {
            multiplier *= 0.5;
        }
        
        // Increase size during news events
        if (lastAISignals.newsAlert) {
            multiplier *= 1.5;
        }
        
        return Math.max(0.1, Math.min(3.0, multiplier)); // Clamp between 0.1 and 3.0
    }
    
    @Override
    public void onTrade(Trade trade) {
        // Update position
        boolean wasBuyer = trade.buyOrderId >= 2000; // Our AI orders start at 2000
        
        if (wasBuyer) {
            currentPosition += trade.quantity;
            double tradeValue = trade.getPriceAsDouble() * trade.quantity;
            totalPnL -= tradeValue;
            aiPnL -= tradeValue;
            
            if (aiPnL > 0) {
                profitableAITrades++;
            }
            
            logger.info("AI BUY executed: price={}, qty={}, pos={}, AI P&L=${}", 
                trade.getPriceAsDouble(), trade.quantity, currentPosition, aiPnL);
        } else {
            currentPosition -= trade.quantity;
            double tradeValue = trade.getPriceAsDouble() * trade.quantity;
            totalPnL += tradeValue;
            aiPnL += tradeValue;
            
            if (aiPnL > 0) {
                profitableAITrades++;
            }
            
            logger.info("AI SELL executed: price={}, qty={}, pos={}, AI P&L=${}", 
                trade.getPriceAsDouble(), trade.quantity, currentPosition, aiPnL);
        }
        
        // Reset AI modes after trade execution
        if (Math.abs(currentPosition) == 0) {
            aiNewsMode = false;
            aiRiskMode = false;
        }
    }
    
    @Override
    public String getName() {
        return "AI-Enhanced";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    @Override
    public void reset() {
        currentPosition = 0;
        totalPnL = 0.0;
        aiPnL = 0.0;
        aiTrades = 0;
        profitableAITrades = 0;
        aiNewsMode = false;
        aiRiskMode = false;
        lastAISignals = null;
    }
    
    /**
     * Get AI performance statistics
     */
    public AIPerformanceStats getAIPerformanceStats() {
        double aiWinRate = aiTrades > 0 ? (double) profitableAITrades / aiTrades : 0.0;
        return new AIPerformanceStats(aiTrades, profitableAITrades, aiWinRate, aiPnL);
    }
    
    /**
     * Get current AI signals
     */
    public AIMarketIntelligence.AISignals getCurrentAISignals() {
        return lastAISignals;
    }
    
    /**
     * AI Performance Statistics
     */
    public static class AIPerformanceStats {
        public final int totalAITrades;
        public final int profitableAITrades;
        public final double aiWinRate;
        public final double aiPnL;
        
        public AIPerformanceStats(int totalAITrades, int profitableAITrades, 
                                 double aiWinRate, double aiPnL) {
            this.totalAITrades = totalAITrades;
            this.profitableAITrades = profitableAITrades;
            this.aiWinRate = aiWinRate;
            this.aiPnL = aiPnL;
        }
        
        @Override
        public String toString() {
            return String.format("AI Stats: %d trades, %d profitable, %.1f%% win rate, $%.2f P&L",
                totalAITrades, profitableAITrades, aiWinRate * 100, aiPnL);
        }
    }
    
    /**
     * Shutdown AI components
     */
    public void shutdown() {
        aiIntelligence.shutdown();
        logger.info("AI-Enhanced Strategy shutdown complete");
    }
    
    public long getCurrentPosition() {
        return currentPosition;
    }
    
    public boolean isAINewsMode() {
        return aiNewsMode;
    }
    
    public boolean isAIRiskMode() {
        return aiRiskMode;
    }
}
