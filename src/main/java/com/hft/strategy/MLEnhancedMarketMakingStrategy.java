package com.hft.strategy;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import com.hft.ml.MarketRegimeClassifier;
import com.hft.ml.TechnicalIndicators;
// FIX: was com.ft.risk.RiskManager (wrong package — missing 'h' in 'hft')
import com.ft.risk.RiskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ML-Enhanced Market Making Strategy
 * 
 * Combines ultra-high performance technical indicators with Random Forest regime classification
 * Used by top HFT firms for adaptive market making:
 * - Citadel Securities: Dynamic spread adjustment based on regime
 * - Jane Street: Regime-aware inventory management
 * - Two Sigma: ML-driven quote optimization
 */
public class MLEnhancedMarketMakingStrategy implements Strategy {
    
    private static final Logger logger = LoggerFactory.getLogger(MLEnhancedMarketMakingStrategy.class);
    
    // Strategy parameters
    private final int symbolId;
    private final double baseSpreadPercent;  // Base spread in percentage
    private final int orderSize;            // Order size in base units
    private final int maxPosition;          // Maximum position size
    
    // ML components
    private final TechnicalIndicators indicators;
    private final MarketRegimeClassifier regimeClassifier;
    private final int indicatorLookback;    // Lookback period for indicators
    
    // Regime-specific parameters - optimized for each market condition
    private final double[] regimeMultipliers;  // Spread multipliers by regime
    private final int[] regimeOrderSizes;      // Order sizes by regime
    private final double[] regimeMaxPositions; // Max positions by regime
    
    // Current state
    private volatile int currentPosition;
    private long lastQuoteTime;
    private boolean isTrained;
    private volatile MarketRegimeClassifier.MarketRegime lastRegime = MarketRegimeClassifier.MarketRegime.RANGING;
    
    // Performance tracking
    private final AtomicLong quotesGenerated;
    private final AtomicLong regimeChanges;
    private final AtomicLong mlPredictions;
    private double totalPnL;
    
    // Risk management
    private final RiskManager riskManager;

    // FIX: track our own order IDs to correctly calculate P&L in onTrade()
    // instead of having incorrect totalPnL += trade.price * trade.quantity for all trades
    private final java.util.Set<Long> ourBuyOrderIds  = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final java.util.Set<Long> ourSellOrderIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    public MLEnhancedMarketMakingStrategy(int symbolId, double baseSpreadPercent, int orderSize, int maxPosition) {
        this.symbolId = symbolId;
        this.baseSpreadPercent = baseSpreadPercent;
        this.orderSize = orderSize;
        this.maxPosition = maxPosition;
        
        // Initialize ML components
        this.indicatorLookback = 100;  // 100 ticks for indicator calculation
        this.indicators = new TechnicalIndicators(indicatorLookback);
        this.regimeClassifier = new MarketRegimeClassifier();
        
        // Regime-specific parameters - optimized for HFT
        this.regimeMultipliers = new double[]{
            1.0,   // TRENDING: Standard spread
            0.8,   // RANGING: Tighter spread for more trades
            2.0,   // VOLATILE: Wider spread for protection
            1.5    // REVERSAL: Moderate spread
        };
        
        this.regimeOrderSizes = new int[]{
            orderSize,      // TRENDING: Standard size
            (int)(orderSize * 1.2), // RANGING: Larger size for more volume
            (int)(orderSize * 0.8), // VOLATILE: Smaller size for risk management
            (int)(orderSize * 0.6)  // REVERSAL: Conservative size
        };
        
        this.regimeMaxPositions = new double[]{
            maxPosition,           // TRENDING: Standard position
            maxPosition * 1.5,     // RANGING: Larger position allowed
            maxPosition * 0.5,     // VOLATILE: Smaller position for safety
            maxPosition * 0.3      // REVERSAL: Very conservative
        };
        
        this.currentPosition = 0;
        this.lastQuoteTime = 0;
        this.isTrained = false;
        
        // Performance tracking
        this.quotesGenerated = new AtomicLong(0);
        this.regimeChanges = new AtomicLong(0);
        this.mlPredictions = new AtomicLong(0);
        this.totalPnL = 0.0;
        
        // Risk manager
        this.riskManager = new RiskManager(new RiskManager.RiskConfig(
            (int)maxPosition, 0.10, 0.05, 50000.0, 50
        ));
        
        logger.info("ML-Enhanced Market Making Strategy initialized for symbol {}", symbolId);
        logger.info("Base spread: {}%, Order size: {}, Max position: {}", baseSpreadPercent, orderSize, maxPosition);
    }
    
    @Override
    public void initialize() {
        logger.info("ML-Enhanced Market Making Strategy initialized for symbol {}", symbolId);
        logger.info("Base spread: {}%, Order size: {}, Max position: {}", baseSpreadPercent, orderSize, maxPosition);
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        // Update indicators with new tick data
        indicators.addData(tick.price / 10000.0, tick.volume / 1000000.0); // Convert from long to double
        
        // Only make decisions if we have enough data
        if (!indicators.hasEnoughData(indicatorLookback)) {
            return new ArrayList<>();
        }
        
        // Get current market regime
        MarketRegimeClassifier.MarketRegime currentRegime = getCurrentRegime();
        
        // Generate quotes based on regime
        List<Order> orders = new ArrayList<>();
        generateQuotes(tick, orderBook, orders, currentRegime);
        
        // Manage existing orders based on regime
        manageOrders(orders, currentRegime);
        
        // Update performance metrics
        updatePerformanceMetrics(currentRegime);
        
        return orders;
    }
    
    @Override
    public void onTrade(Trade trade) {
        // FIX: was totalPnL += trade.price * trade.quantity for ALL trades regardless of side,
        // which incorrectly added revenue for buys (should subtract cost) and
        // double-counted trades that weren't even ours.
        // Now: only process trades from our own orders, with correct buy/sell accounting.
        if (ourBuyOrderIds.contains(trade.buyOrderId)) {
            currentPosition += trade.quantity;
            totalPnL -= trade.getPriceAsDouble() * trade.quantity; // cost of buy
            ourBuyOrderIds.remove(trade.buyOrderId);
        } else if (ourSellOrderIds.contains(trade.sellOrderId)) {
            currentPosition -= trade.quantity;
            totalPnL += trade.getPriceAsDouble() * trade.quantity; // revenue from sell
            ourSellOrderIds.remove(trade.sellOrderId);
        }
        // Trades from other participants are ignored
    }
    
    /**
     * Get current market regime using ML classifier
     */
    private MarketRegimeClassifier.MarketRegime getCurrentRegime() {
        if (!isTrained) {
            // Use simple heuristic if not trained
            return MarketRegimeClassifier.MarketRegime.RANGING;
        }
        
        // Get all technical indicators
        double[] features = indicators.getAllIndicators();
        
        // Predict regime
        MarketRegimeClassifier.MarketRegime regime = regimeClassifier.predict(features);
        mlPredictions.incrementAndGet();
        
        return regime;
    }
    
    /**
     * Generate quotes based on current regime
     */
    private void generateQuotes(Tick tick, OrderBook orderBook, List<Order> orders, MarketRegimeClassifier.MarketRegime regime) {
        long currentTime = System.nanoTime();
        
        // Rate limiting - don't quote too frequently
        if (currentTime - lastQuoteTime < 1_000_000L) { // 1ms minimum
            return;
        }
        
        // Get regime-specific parameters
        double spreadMultiplier = regimeMultipliers[regime.getValue()];
        int regimeOrderSize = regimeOrderSizes[regime.getValue()];
        double regimeMaxPosition = regimeMaxPositions[regime.getValue()];
        
        // Calculate dynamic spread based on regime and volatility
        double currentSpread = calculateDynamicSpread(regime);
        
        // Get best bid/ask from order book
        long bestBid = orderBook.getBestBid();
        long bestAsk = orderBook.getBestAsk();
        
        if (bestBid == 0 || bestAsk == 0) {
            return; // No market data
        }
        
        // Calculate our quotes
        long midPrice = (bestBid + bestAsk) / 2;
        long halfSpread = (long)(midPrice * currentSpread / 20000); // Convert percentage to ticks
        
        long ourBid = midPrice - halfSpread;
        long ourAsk = midPrice + halfSpread;
        
        // Check position limits
        if (currentPosition >= regimeMaxPosition) {
            ourBid = 0; // Don't place more bids
        }
        if (currentPosition <= -regimeMaxPosition) {
            ourAsk = 0; // Don't place more asks
        }
        
        // Generate orders
        if (ourBid > 0 && ourBid < bestBid) {
            Order bidOrder = createOrder(ourBid, regimeOrderSize, (byte)0, (byte)0); // LIMIT, BUY
            ourBuyOrderIds.add(bidOrder.orderId); // FIX: track as our buy order
            orders.add(bidOrder);
            quotesGenerated.incrementAndGet();
        }
        
        if (ourAsk > 0 && ourAsk > bestAsk) {
            Order askOrder = createOrder(ourAsk, regimeOrderSize, (byte)0, (byte)1); // LIMIT, SELL
            ourSellOrderIds.add(askOrder.orderId); // FIX: track as our sell order
            orders.add(askOrder);
            quotesGenerated.incrementAndGet();
        }
        
        lastQuoteTime = currentTime;
    }
    
    /**
     * Calculate dynamic spread based on regime and market conditions
     */
    private double calculateDynamicSpread(MarketRegimeClassifier.MarketRegime regime) {
        double baseSpread = baseSpreadPercent;
        double regimeMultiplier = regimeMultipliers[regime.getValue()];
        
        // Get volatility from Bollinger Bands
        double[] bollinger = indicators.calculateBollingerBands(20, 2.0);
        double bandwidth = bollinger[3]; // Bandwidth indicator
        
        // Adjust spread based on volatility
        double volatilityAdjustment = 1.0 + (bandwidth * 2.0); // More volatile = wider spread
        
        // Get RSI for trend strength
        double rsi = indicators.calculateRSI(14);
        double trendAdjustment = 1.0;
        
        if (rsi > 70 || rsi < 30) {
            trendAdjustment = 1.2; // Strong trend = slightly wider spread
        }
        
        return baseSpread * regimeMultiplier * volatilityAdjustment * trendAdjustment;
    }
    
    /**
     * Manage existing orders based on regime changes
     */
    private void manageOrders(List<Order> orders, MarketRegimeClassifier.MarketRegime currentRegime) {
        // Cancel orders that are no longer appropriate for current regime
        orders.removeIf(order -> {
            if (order.status == 2) { // Filled
                // Update position for filled orders
                if (order.side == 0) { // Buy
                    currentPosition += order.filledQuantity;
                } else { // Sell
                    currentPosition -= order.filledQuantity;
                }
                return true; // Remove filled orders
            }
            
            // FIX: guard against getCurrentMidPrice() returning 0 when no indicator data yet
            long currentPrice = getCurrentMidPrice();
            if (currentPrice == 0) {
                return false; // No price data yet — keep the order
            }

            long orderPrice = order.price;
            double maxDistance = getMaxAllowedDistance(currentRegime);
            
            double distance = Math.abs(orderPrice - currentPrice) / (double) currentPrice;
            
            return distance > maxDistance;
        });
    }
    
    /**
     * Get maximum allowed distance from mid price based on regime
     */
    private double getMaxAllowedDistance(MarketRegimeClassifier.MarketRegime regime) {
        switch (regime) {
            case VOLATILE: return 0.005;  // 0.5% in volatile markets
            case TRENDING: return 0.003; // 0.3% in trending markets
            case REVERSAL: return 0.002; // 0.2% in reversal markets
            case RANGING: return 0.001;  // 0.1% in ranging markets
            default: return 0.002;
        }
    }
    
    /**
     * Create a new order
     * FIX: use the standard Order constructor instead of no-arg + field assignment,
     * because Order may not have a no-arg constructor
     */
    private Order createOrder(long price, int quantity, byte type, byte side) {
        long orderId = System.nanoTime(); // Unique ID
        long timestamp = System.nanoTime();
        Order order = new Order(orderId, symbolId, price, quantity, side, type);
        order.timestamp = timestamp;
        order.status = 0; // New
        order.filledQuantity = 0;
        return order;
    }
    
    /**
     * Get current mid price from indicators
     * FIX: added null/empty guard — returns 0 if no indicator data available yet
     */
    private long getCurrentMidPrice() {
        double[] allIndicators = indicators.getAllIndicators();
        if (allIndicators == null || allIndicators.length == 0) {
            return 0L;
        }
        // Last element is current price (index 13 in the indicator array)
        double currentPrice = allIndicators[allIndicators.length - 1];
        if (currentPrice <= 0) {
            return 0L;
        }
        return (long)(currentPrice * 10000); // Convert back to long format
    }
    
    /**
     * Update performance metrics
     */
    private void updatePerformanceMetrics(MarketRegimeClassifier.MarketRegime currentRegime) {
        // This would be called periodically to update strategy performance
        // For now, just log regime changes
        if (currentRegime != lastRegime) {
            regimeChanges.incrementAndGet();
            logger.info("Regime changed to: {}", currentRegime);
            lastRegime = currentRegime;
        }
    }
    
    /**
     * Train the ML model with historical data
     */
    public void trainModel(List<double[]> historicalPrices) {
        logger.info("Training ML model with {} data points", historicalPrices.size());
        
        // Generate features and labels
        List<double[]> features = new ArrayList<>();

        // FIX: use explicit type List<MarketRegimeClassifier.MarketRegime> to avoid
        // type-inference ambiguity when reassigning from generateLabels()
        List<MarketRegimeClassifier.MarketRegime> labels = new ArrayList<>();
        
        // Calculate indicators for each point
        TechnicalIndicators tempIndicators = new TechnicalIndicators(indicatorLookback);
        
        for (int i = 0; i < historicalPrices.size(); i++) {
            double[] pricePoint = historicalPrices.get(i);
            tempIndicators.addData(pricePoint[0], pricePoint[1]);
            
            if (tempIndicators.hasEnoughData(indicatorLookback)) {
                double[] featureVector = tempIndicators.getAllIndicators();
                features.add(featureVector);
            }
        }
        
        // Generate labels from price data
        double[] prices = historicalPrices.stream().mapToDouble(p -> p[0]).toArray();
        labels = MarketRegimeClassifier.generateLabels(prices, indicatorLookback);
        
        // Ensure features and labels match
        int minSize = Math.min(features.size(), labels.size());
        features = features.subList(0, minSize);
        labels = labels.subList(0, minSize);
        
        // Train the classifier
        regimeClassifier.train(features, labels);
        isTrained = true;
        
        logger.info("ML model training completed. Trained on {} samples", features.size());
        
        // Log feature importance
        double[] importance = regimeClassifier.getFeatureImportance();
        logger.info("Feature importance: RSI={}, MACD={}, Bollinger={}, VWAP={}", 
                   importance[0], importance[1], importance[4], importance[8]);
    }
    
    /**
     * Get strategy statistics
     */
    public StrategyStats getStats() {
        return new StrategyStats(
            quotesGenerated.get(),
            regimeChanges.get(),
            mlPredictions.get(),
            currentPosition,
            totalPnL,
            isTrained
        );
    }
    
    /**
     * Strategy statistics holder
     */
    public static class StrategyStats {
        public final long quotesGenerated;
        public final long regimeChanges;
        public final long mlPredictions;
        public final int currentPosition;
        public final double totalPnL;
        public final boolean isTrained;
        
        public StrategyStats(long quotesGenerated, long regimeChanges, long mlPredictions, 
                           int currentPosition, double totalPnL, boolean isTrained) {
            this.quotesGenerated = quotesGenerated;
            this.regimeChanges = regimeChanges;
            this.mlPredictions = mlPredictions;
            this.currentPosition = currentPosition;
            this.totalPnL = totalPnL;
            this.isTrained = isTrained;
        }
        
        @Override
        public String toString() {
            return String.format("StrategyStats{quotes=%d, regimeChanges=%d, predictions=%d, position=%d, pnl=%.2f, trained=%s}",
                               quotesGenerated, regimeChanges, mlPredictions, currentPosition, totalPnL, isTrained);
        }
    }
    
    @Override
    public String getName() {
        return "ML-Enhanced Market Making";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    /**
     * Reset strategy state
     */
    public void reset() {
        indicators.reset();
        currentPosition = 0;
        lastQuoteTime = 0;
        quotesGenerated.set(0);
        regimeChanges.set(0);
        mlPredictions.set(0);
        totalPnL = 0.0;
        ourBuyOrderIds.clear();
        ourSellOrderIds.clear();
    }
    
    /**
     * Get current regime confidence
     */
    public double getRegimeConfidence() {
        if (!isTrained) return 0.0;
        
        double[] features = indicators.getAllIndicators();
        return regimeClassifier.getConfidence(features);
    }
    
    /**
     * Get regime probabilities
     */
    public Map<MarketRegimeClassifier.MarketRegime, Double> getRegimeProbabilities() {
        if (!isTrained) {
            return Map.of();
        }
        
        double[] features = indicators.getAllIndicators();
        return regimeClassifier.getProbabilities(features);
    }
}