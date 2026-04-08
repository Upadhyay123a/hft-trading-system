package com.hft.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.ml.LSTMPricePredictor;
import com.hft.ml.MarketRegimeClassifier;
import com.hft.ml.ReinforcementLearningAgent;
import com.hft.ml.TechnicalIndicators;
import com.hft.orderbook.OrderBook;

/**
 * Advanced ML-Enhanced Trading Strategy
 * 
 * Combines LSTM price prediction with Reinforcement Learning optimization
 * Phase 2 implementation for institutional-grade HFT
 * 
 * Features:
 * - LSTM neural network for price prediction
 * - Reinforcement Learning for strategy optimization
 * - Dynamic parameter adjustment
 * - Real-time model inference
 * 
 * Used by top firms like Two Sigma, Renaissance Technologies, and Jump Trading
 */
public class AdvancedMLStrategy implements Strategy {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedMLStrategy.class);
    
    // Strategy parameters
    private final int symbolId;
    private final double baseSpreadPercent;
    private final int baseOrderSize;
    private final int maxPosition;
    
    // ML Components
    private final TechnicalIndicators indicators;
    private final MarketRegimeClassifier regimeClassifier;
    private final LSTMPricePredictor lstmPredictor;
    private final ReinforcementLearningAgent rlAgent;
    
    // Dynamic parameters (adjusted by RL)
    private double currentSpreadPercent;
    private int currentOrderSize;
    private int currentMaxPosition;
    
    // State tracking
    private int currentPosition;
    private double currentPnL;
    private double lastPrice;
    private long lastUpdateTime;
    
    // Performance tracking
    private final AtomicLong predictions;
    private final AtomicLong rlUpdates;
    private final AtomicLong trades;
    private double totalReward;
    
    // Training configuration
    private static final int LSTM_TRAINING_EPOCHS = 50;
    private static final int RL_TRAINING_EPISODES = 100;
    private static final int PRICE_HISTORY_SIZE = 100;
    
    // Price history for LSTM
    private final List<Double> priceHistory;
    
    public AdvancedMLStrategy(int symbolId, double baseSpreadPercent, int baseOrderSize, int maxPosition) {
        this.symbolId = symbolId;
        this.baseSpreadPercent = baseSpreadPercent;
        this.baseOrderSize = baseOrderSize;
        this.maxPosition = maxPosition;
        
        // Initialize ML components
        this.indicators = new TechnicalIndicators(100);
        this.regimeClassifier = new MarketRegimeClassifier();
        this.lstmPredictor = new LSTMPricePredictor(0.001);
        this.rlAgent = new ReinforcementLearningAgent(20, 8); // 20 state features, 8 actions
        
        // Initialize dynamic parameters
        this.currentSpreadPercent = baseSpreadPercent;
        this.currentOrderSize = baseOrderSize;
        this.currentMaxPosition = maxPosition;
        
        // Initialize state tracking
        this.currentPosition = 0;
        this.currentPnL = 0.0;
        this.lastPrice = 0.0;
        this.lastUpdateTime = System.nanoTime();
        
        // Initialize performance tracking
        this.predictions = new AtomicLong(0);
        this.rlUpdates = new AtomicLong(0);
        this.trades = new AtomicLong(0);
        this.totalReward = 0.0;
        
        // Initialize price history
        this.priceHistory = new ArrayList<>();
        
        logger.info("Advanced ML Strategy initialized for symbol {}", symbolId);
        logger.info("Base spread: {}%, Order size: {}, Max position: {}", baseSpreadPercent, baseOrderSize, maxPosition);
    }
    
    @Override
    public void initialize() {
        logger.info("Advanced ML Strategy initialization complete");
        logger.info("Components: LSTM Price Predictor, RL Agent, Regime Classifier");
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        // Update indicators with new tick data
        indicators.addData(tick.price / 10000.0, tick.volume / 1000000.0);
        
        // Update price history
        double currentPrice = tick.price / 10000.0;
        priceHistory.add(currentPrice);
        if (priceHistory.size() > PRICE_HISTORY_SIZE) {
            priceHistory.remove(0);
        }
        
        // Update LSTM with new price
        lstmPredictor.addTrainingData(currentPrice);
        
        // Only make decisions if we have enough data
        if (!indicators.hasEnoughData(50) || priceHistory.size() < 50) {
            return new ArrayList<>();
        }
        
        // Get ML predictions and recommendations
        MLDecision decision = makeMLDecision(tick, orderBook);
        
        // Generate orders based on ML decision
        List<Order> orders = generateOrders(tick, orderBook, decision);
        
        // Update RL agent with experience
        updateRLAgent(tick, decision);
        
        return orders;
    }
    
    @Override
    public void onTrade(Trade trade) {
        // Update position and P&L
        double tradePrice = trade.price / 10000.0;
        double tradeValue = tradePrice * trade.quantity;
        
        // Simple P&L calculation (would be more sophisticated in production)
        currentPnL += tradeValue * 0.001; // Simplified P&L
        trades.incrementAndGet();
        
        // Update last price for reference
        lastPrice = tradePrice;
        lastUpdateTime = System.nanoTime();
    }
    
    /**
     * Make ML-based trading decision
     */
    private MLDecision makeMLDecision(Tick tick, OrderBook orderBook) {
        // Get all technical indicators
        double[] allIndicators = indicators.getAllIndicators();
        
        // Get market regime
        MarketRegimeClassifier.MarketRegime regime = regimeClassifier.predict(allIndicators);
        
        // Get LSTM price prediction
        double[] pricePrediction = lstmPredictor.predict(getRecentPrices());
        double predictedPrice = pricePrediction[0];
        double predictionConfidence = pricePrediction[1];
        
        // Get RL agent recommendation
        double[] state = buildState(allIndicators, regime, predictedPrice, predictionConfidence);
        ReinforcementLearningAgent.ActionRecommendation rlRec = rlAgent.getRecommendation(state);
        
        // Combine all ML signals
        return new MLDecision(
            regime,
            predictedPrice,
            predictionConfidence,
            rlRec.action,
            rlRec.value,
            rlRec.confidence
        );
    }
    
    /**
     * Build state vector for RL agent
     */
    private double[] buildState(double[] indicators, MarketRegimeClassifier.MarketRegime regime,
                               double predictedPrice, double confidence) {
        double[] state = new double[20]; // 20-dimensional state space
        
        // Technical indicators (first 14)
        System.arraycopy(indicators, 0, state, 0, Math.min(14, indicators.length));
        
        // Market regime (15-18)
        state[14] = regime.getValue() / 3.0; // Normalize to [0,1]
        
        // Price prediction (19)
        double currentPrice = indicators[13]; // Current price is last element
        state[15] = currentPrice > 0 ? (predictedPrice - currentPrice) / currentPrice : 0.0;
        
        // Prediction confidence (16)
        state[16] = confidence;
        
        // Current position (17)
        state[17] = maxPosition > 0 ? (double) currentPosition / maxPosition : 0.0;
        
        // Current P&L (18)
        state[18] = Math.max(-1.0, Math.min(1.0, currentPnL / 1000.0)); // Normalize P&L
        
        // Time since last update (19)
        long timeDiff = System.nanoTime() - lastUpdateTime;
        state[19] = Math.min(1.0, timeDiff / 1_000_000_000.0); // Normalize to seconds
        
        return state;
    }
    
    /**
     * Get recent prices for LSTM
     */
    private double[] getRecentPrices() {
        double[] recent = new double[50];
        int start = Math.max(0, priceHistory.size() - 50);
        
        for (int i = 0; i < 50 && start + i < priceHistory.size(); i++) {
            recent[i] = priceHistory.get(start + i);
        }
        
        return recent;
    }
    
    /**
     * Generate orders based on ML decision
     */
    private List<Order> generateOrders(Tick tick, OrderBook orderBook, MLDecision decision) {
        List<Order> orders = new ArrayList<>();
        
        // Adjust parameters based on RL action
        adjustParameters(decision.rlAction);
        
        // Get current market prices
        long bestBid = orderBook.getBestBid();
        long bestAsk = orderBook.getBestAsk();
        
        if (bestBid == 0 || bestAsk == 0) {
            return orders;
        }
        
        // Calculate quotes based on ML predictions
        long midPrice = (bestBid + bestAsk) / 2;
        
        // Adjust spread based on prediction confidence
        double spreadAdjustment = 1.0 + (1.0 - decision.predictionConfidence) * 0.5;
        double adjustedSpread = currentSpreadPercent * spreadAdjustment;
        
        // Adjust position based on predicted price movement
        double priceAdjustment = (decision.predictedPrice - (midPrice / 10000.0)) / (midPrice / 10000.0);
        long priceOffset = (long)(priceAdjustment * 10000 * 0.1); // Small adjustment
        
        long halfSpread = (long)(midPrice * adjustedSpread / 20000);
        
        long ourBid = midPrice - halfSpread + priceOffset;
        long ourAsk = midPrice + halfSpread + priceOffset;
        
        // Position limits
        if (currentPosition >= currentMaxPosition) {
            ourBid = 0; // Don't place more bids
        }
        if (currentPosition <= -currentMaxPosition) {
            ourAsk = 0; // Don't place more asks
        }
        
        // Generate orders
        if (ourBid > 0 && ourBid < bestBid) {
            Order bidOrder = createOrder(ourBid, currentOrderSize, (byte)0, (byte)0); // LIMIT, BUY
            orders.add(bidOrder);
        }
        
        if (ourAsk > 0 && ourAsk > bestAsk) {
            Order askOrder = createOrder(ourAsk, currentOrderSize, (byte)0, (byte)1); // LIMIT, SELL
            orders.add(askOrder);
        }
        
        predictions.incrementAndGet();
        
        return orders;
    }
    
    /**
     * Adjust strategy parameters based on RL action
     */
    private void adjustParameters(int rlAction) {
        ReinforcementLearningAgent.TradingAction action = ReinforcementLearningAgent.TradingAction.fromValue(rlAction);
        
        switch (action) {
            case INCREASE_SPREAD:
                currentSpreadPercent = Math.min(0.1, currentSpreadPercent * 1.1);
                break;
            case DECREASE_SPREAD:
                currentSpreadPercent = Math.max(0.001, currentSpreadPercent * 0.9);
                break;
            case INCREASE_SIZE:
                currentOrderSize = Math.min(maxPosition, currentOrderSize + 100);
                break;
            case DECREASE_SIZE:
                currentOrderSize = Math.max(100, currentOrderSize - 100);
                break;
            case REDUCE_EXPOSURE:
                currentMaxPosition = Math.max(1, currentMaxPosition - 1);
                break;
            case INCREASE_EXPOSURE:
                currentMaxPosition = Math.min(maxPosition, currentMaxPosition + 1);
                break;
            case HOLD_POSITION:
            case SWITCH_STRATEGY:
            default:
                // Keep current parameters
                break;
        }
    }
    
    /**
     * Update RL agent with experience
     */
    private void updateRLAgent(Tick tick, MLDecision decision) {
        // Calculate reward based on current performance
        double reward = calculateReward();
        
        // Build next state (after this tick)
        double[] nextState = buildState(
            indicators.getAllIndicators(),
            decision.regime,
            decision.predictedPrice,
            decision.predictionConfidence
        );
        
        // Update RL agent
        rlAgent.updateExperience(
            buildState(indicators.getAllIndicators(), decision.regime, decision.predictedPrice, decision.predictionConfidence),
            decision.rlAction,
            reward,
            nextState,
            false
        );
        
        totalReward += reward;
        rlUpdates.incrementAndGet();
        
        // Periodic training
        if (rlUpdates.get() % 1000 == 0) {
            rlAgent.train(10);
        }
    }
    
    /**
     * Calculate reward for RL agent
     */
    private double calculateReward() {
        // Multi-objective reward function
        double pnlReward = currentPnL * 0.4;                    // P&L component
        double positionReward = Math.abs(currentPosition) > 0 ? -0.1 : 0.1; // Prefer balanced positions
        double spreadReward = currentSpreadPercent < baseSpreadPercent ? 0.1 : -0.1; // Prefer tight spreads
        double sizeReward = currentOrderSize > baseOrderSize ? 0.05 : -0.05; // Prefer larger sizes
        
        return pnlReward + positionReward + spreadReward + sizeReward;
    }
    
    /**
     * Create order
     */
    private Order createOrder(long price, int quantity, byte type, byte side) {
        Order order = new Order();
        order.orderId = System.nanoTime();
        order.symbolId = symbolId;
        order.price = price;
        order.quantity = quantity;
        order.side = side;
        order.type = type;
        order.timestamp = System.nanoTime();
        order.status = 0; // New
        order.filledQuantity = 0;
        
        return order;
    }
    
    /**
     * Train ML models
     */
    public void trainModels() {
        logger.info("Training ML models...");
        
        // Train LSTM
        logger.info("Training LSTM Price Predictor...");
        lstmPredictor.train(LSTM_TRAINING_EPOCHS, 32);
        
        // Train RL agent
        logger.info("Training Reinforcement Learning Agent...");
        rlAgent.train(RL_TRAINING_EPISODES);

        // Train/prepare Market Regime Classifier using sample historical data
        try {
            logger.info("Training Market Regime Classifier from sample data...");
            String dataFile = "data/sample_market_data.csv";
            java.io.File f = new java.io.File(dataFile);
            if (f.exists()) {
                java.util.List<Double> priceList = new java.util.ArrayList<>();
                java.util.List<double[]> featureList = new java.util.ArrayList<>();

                TechnicalIndicators ti = new TechnicalIndicators(100);

                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                    String header = br.readLine(); // skip header
                    String line;
                    int index = 0;
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length < 4) continue;
                        double price = Long.parseLong(parts[2]) / 10000.0;
                        double volume = Long.parseLong(parts[3]) / 1000000.0;

                        priceList.add(price);
                        ti.addData(price, volume);

                        // Ensure we have enough data for MACD (26) and lookback labeling
                        int lookback = 20;
                        int requiredIndicators = 26;
                        if (index >= Math.max(lookback, requiredIndicators) && ti.hasEnoughData(requiredIndicators)) {
                            featureList.add(ti.getAllIndicators());
                        }
                        index++;
                    }
                }

                // Prepare labels aligned with features
                double[] pricesArr = new double[priceList.size()];
                for (int i = 0; i < priceList.size(); i++) pricesArr[i] = priceList.get(i);

                int lookback = 20;
                java.util.List<com.hft.ml.MarketRegimeClassifier.MarketRegime> labels = com.hft.ml.MarketRegimeClassifier.generateLabels(pricesArr, lookback);

                // Align labels with features: features were collected starting at index >= Math.max(lookback, requiredIndicators)
                // generateLabels produces labels for indices [lookback .. n-1], so we need to trim/align accordingly
                int startFeatureIndex = Math.max(lookback, 26);
                int expected = Math.max(0, pricesArr.length - startFeatureIndex);

                if (featureList.size() > labels.size()) {
                    // Trim featureList to labels size
                    featureList = featureList.subList(featureList.size() - labels.size(), featureList.size());
                } else if (labels.size() > featureList.size()) {
                    // Trim labels to featureList size
                    labels = labels.subList(labels.size() - featureList.size(), labels.size());
                }

                // Convert lists for training
                java.util.List<double[]> featuresForTrain = new java.util.ArrayList<>(featureList);
                java.util.List<com.hft.ml.MarketRegimeClassifier.MarketRegime> labelsForTrain = new java.util.ArrayList<>(labels);

                if (featuresForTrain.size() > 50 && labelsForTrain.size() > 50) {
                    regimeClassifier.train(featuresForTrain, labelsForTrain);
                    logger.info("Market Regime Classifier trained on {} samples", featuresForTrain.size());
                } else {
                    logger.warn("Not enough samples to train regime classifier (features={}, labels={}) — skipping training", featuresForTrain.size(), labelsForTrain.size());
                }
            } else {
                logger.warn("Sample data file {} not found — skipping regime classifier training", dataFile);
            }
        } catch (Exception ex) {
            logger.error("Failed to train Market Regime Classifier: {}", ex.getMessage(), ex);
        }

        logger.info("ML model training completed");
    }
    
    /**
     * Get strategy statistics
     */
    public AdvancedMLStats getStats() {
        return new AdvancedMLStats(
            predictions.get(),
            rlUpdates.get(),
            trades.get(),
            currentPnL,
            currentPosition,
            currentSpreadPercent,
            currentOrderSize,
            currentMaxPosition,
            totalReward,
            lstmPredictor.getStats(),
            rlAgent.getStats()
        );
    }
    
    /**
     * Advanced ML statistics holder
     */
    public static class AdvancedMLStats {
        public final long predictions;
        public final long rlUpdates;
        public final long trades;
        public final double currentPnL;
        public final int currentPosition;
        public final double currentSpreadPercent;
        public final int currentOrderSize;
        public final int currentMaxPosition;
        public final double totalReward;
        public final LSTMPricePredictor.ModelStats lstmStats;
        public final ReinforcementLearningAgent.AgentStats rlStats;
        
        public AdvancedMLStats(long predictions, long rlUpdates, long trades, double currentPnL,
                               int currentPosition, double currentSpreadPercent, int currentOrderSize,
                               int currentMaxPosition, double totalReward,
                               LSTMPricePredictor.ModelStats lstmStats, ReinforcementLearningAgent.AgentStats rlStats) {
            this.predictions = predictions;
            this.rlUpdates = rlUpdates;
            this.trades = trades;
            this.currentPnL = currentPnL;
            this.currentPosition = currentPosition;
            this.currentSpreadPercent = currentSpreadPercent;
            this.currentOrderSize = currentOrderSize;
            this.currentMaxPosition = currentMaxPosition;
            this.totalReward = totalReward;
            this.lstmStats = lstmStats;
            this.rlStats = rlStats;
        }
        
        @Override
        public String toString() {
            return String.format("AdvancedMLStats{predictions=%d, rlUpdates=%d, trades=%d, pnl=%.2f, position=%d, spread=%.3f%%, size=%d, maxPos=%d, reward=%.2f}",
                               predictions, rlUpdates, trades, currentPnL, currentPosition, currentSpreadPercent,
                               currentOrderSize, currentMaxPosition, totalReward);
        }
    }
    
    /**
     * ML decision holder
     */
    private static class MLDecision {
        final MarketRegimeClassifier.MarketRegime regime;
        final double predictedPrice;
        final double predictionConfidence;
        final int rlAction;
        final double rlValue;
        final double rlConfidence;
        
        MLDecision(MarketRegimeClassifier.MarketRegime regime, double predictedPrice, double predictionConfidence,
                   int rlAction, double rlValue, double rlConfidence) {
            this.regime = regime;
            this.predictedPrice = predictedPrice;
            this.predictionConfidence = predictionConfidence;
            this.rlAction = rlAction;
            this.rlValue = rlValue;
            this.rlConfidence = rlConfidence;
        }
    }
    
    @Override
    public String getName() {
        return "Advanced ML-Enhanced Strategy";
    }
    
    @Override
    public double getPnL() {
        return currentPnL;
    }
    
    /**
     * Reset strategy state
     */
    public void reset() {
        indicators.reset();
        lstmPredictor.reset();
        rlAgent.reset();
        
        currentPosition = 0;
        currentPnL = 0.0;
        lastPrice = 0.0;
        lastUpdateTime = System.nanoTime();
        
        currentSpreadPercent = baseSpreadPercent;
        currentOrderSize = baseOrderSize;
        currentMaxPosition = maxPosition;
        
        predictions.set(0);
        rlUpdates.set(0);
        trades.set(0);
        totalReward = 0.0;
        
        priceHistory.clear();
    }
    
    /**
     * Get current model confidence
     */
    public double getModelConfidence() {
        double lstmConfidence = lstmPredictor.getConfidence();
        double rlConfidence = rlAgent.getEpsilon() < 0.1 ? 1.0 - rlAgent.getEpsilon() : 0.5;
        
        return (lstmConfidence + rlConfidence) / 2.0;
    }
    
    /**
     * Check if models are trained
     */
    public boolean areModelsTrained() {
        return lstmPredictor.getStats().isTrained && rlAgent.isTrained();
    }
}
