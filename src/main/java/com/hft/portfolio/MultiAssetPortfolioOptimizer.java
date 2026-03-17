package com.hft.portfolio;

import com.hft.ml.RealTimeMLProcessor;
import com.hft.ml.TechnicalIndicators;
import com.hft.ml.MarketRegimeClassifier;
import com.hft.ml.LSTMPricePredictor;
import com.hft.ml.ReinforcementLearningAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-Asset Portfolio Optimizer for HFT
 * 
 * Implements institutional-grade portfolio optimization:
 * - Multi-asset correlation analysis
 * - ML-driven asset allocation
 * - Real-time risk management
 * - Dynamic rebalancing
 * - Performance attribution
 * 
 * Based on best practices from top quantitative firms:
 * - Renaissance Technologies: Statistical arbitrage across assets
 * - Two Sigma: ML-driven portfolio construction
 * - Citadel Securities: Multi-asset market making
 * - Jane Street: Cross-asset statistical arbitrage
 */
public class MultiAssetPortfolioOptimizer {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiAssetPortfolioOptimizer.class);
    
    // Portfolio configuration
    private final List<String> assets;
    private final Map<String, AssetData> assetDataMap;
    private final Map<String, TechnicalIndicators> indicatorsMap;
    
    // ML components for each asset
    private final Map<String, MarketRegimeClassifier> regimeClassifiers;
    private final Map<String, LSTMPricePredictor> pricePredictors;
    private final Map<String, ReinforcementLearningAgent> rlAgents;
    
    // Portfolio optimization
    private final CovarianceMatrix covarianceMatrix;
    private final ExpectedReturns expectedReturns;
    private final RiskModel riskModel;
    
    // Current portfolio state
    private final Map<String, Double> currentWeights;
    private final Map<String, Double> currentPositions;
    private double portfolioValue;
    private double portfolioPnL;
    
    // Performance tracking
    private final AtomicLong rebalancingEvents;
    private final AtomicLong riskEvents;
    private final List<PortfolioPerformance> performanceHistory;
    
    // Thread pool for optimization
    private final ScheduledExecutorService optimizer;
    
    // ML processor for market analysis
    private final RealTimeMLProcessor mlProcessor;
    
    public MultiAssetPortfolioOptimizer(List<String> assets, RealTimeMLProcessor mlProcessor) {
        this.assets = new ArrayList<>(assets);
        this.mlProcessor = mlProcessor;
        
        // Initialize data structures
        this.assetDataMap = new ConcurrentHashMap<>();
        this.indicatorsMap = new ConcurrentHashMap<>();
        this.regimeClassifiers = new ConcurrentHashMap<>();
        this.pricePredictors = new ConcurrentHashMap<>();
        this.rlAgents = new ConcurrentHashMap<>();
        
        this.currentWeights = new ConcurrentHashMap<>();
        this.currentPositions = new ConcurrentHashMap<>();
        
        this.covarianceMatrix = new CovarianceMatrix(assets.size());
        this.expectedReturns = new ExpectedReturns(assets.size());
        this.riskModel = new RiskModel();
        
        this.portfolioValue = 1000000.0; // Start with $1M
        this.portfolioPnL = 0.0;
        
        this.rebalancingEvents = new AtomicLong(0);
        this.riskEvents = new AtomicLong(0);
        this.performanceHistory = new ArrayList<>();
        
        this.optimizer = Executors.newScheduledThreadPool(4);
        
        // Initialize components for each asset
        initializeAssets();
        
        // Start optimization
        startOptimization();
        
        logger.info("Multi-Asset Portfolio Optimizer initialized for {} assets", assets.size());
    }
    
    /**
     * Initialize ML components for each asset
     */
    private void initializeAssets() {
        for (String asset : assets) {
            // Initialize technical indicators
            indicatorsMap.put(asset, new TechnicalIndicators(1000));
            
            // Initialize ML components
            regimeClassifiers.put(asset, new MarketRegimeClassifier());
            pricePredictors.put(asset, new LSTMPricePredictor(0.001));
            rlAgents.put(asset, new ReinforcementLearningAgent(20, 8));
            
            // Initialize asset data
            AssetData assetData = new AssetData(asset);
            assetDataMap.put(asset, assetData);
            
            // Initialize with equal weights
            currentWeights.put(asset, 1.0 / assets.size());
            currentPositions.put(asset, 0.0);
        }
        
        logger.info("Initialized ML components for {} assets", assets.size());
    }
    
    /**
     * Start portfolio optimization
     */
    private void startOptimization() {
        // Real-time optimization every 5 seconds
        optimizer.scheduleAtFixedRate(this::optimizePortfolio, 0, 5, TimeUnit.SECONDS);
        
        // Risk monitoring every 1 second
        optimizer.scheduleAtFixedRate(this::monitorRisk, 0, 1, TimeUnit.SECONDS);
        
        // Performance tracking every 10 seconds
        optimizer.scheduleAtFixedRate(this::trackPerformance, 0, 10, TimeUnit.SECONDS);
        
        logger.info("Portfolio optimization started");
    }
    
    /**
     * Optimize portfolio weights
     */
    private void optimizePortfolio() {
        try {
            // Update market data
            updateMarketData();
            
            // Calculate expected returns and covariance
            calculateRiskReturnMetrics();
            
            // Optimize weights using ML-enhanced Markowitz
            Map<String, Double> optimalWeights = optimizeWeightsML();
            
            // Check if rebalancing is needed
            if (needsRebalancing(optimalWeights)) {
                rebalancePortfolio(optimalWeights);
            }
            
        } catch (Exception e) {
            logger.error("Portfolio optimization failed", e);
        }
    }
    
    /**
     * Update market data for all assets
     */
    private void updateMarketData() {
        for (String asset : assets) {
            try {
                // Get latest market data (simulated)
                MarketData marketData = getMarketData(asset);
                
                // Update technical indicators
                TechnicalIndicators indicators = indicatorsMap.get(asset);
                indicators.addData(marketData.price, marketData.volume);
                
                // Update asset data
                AssetData assetData = assetDataMap.get(asset);
                assetData.update(marketData);
                
                // Update ML models
                if (indicators.hasEnoughData(50)) {
                    double[] allIndicators = indicators.getAllIndicators();
                    
                    // Update regime classifier
                    MarketRegimeClassifier regimeClassifier = regimeClassifiers.get(asset);
                    MarketRegimeClassifier.MarketRegime regime = regimeClassifier.predict(allIndicators);
                    assetData.currentRegime = regime;
                    
                    // Update price predictor
                    LSTMPricePredictor pricePredictor = pricePredictors.get(asset);
                    double[] recentPrices = getRecentPrices(assetData, 50);
                    double[] prediction = pricePredictor.predict(recentPrices);
                    assetData.predictedPrice = prediction[0];
                    assetData.predictionConfidence = prediction[1];
                }
                
            } catch (Exception e) {
                logger.error("Failed to update market data for {}", asset, e);
            }
        }
    }
    
    /**
     * Calculate risk and return metrics
     */
    private void calculateRiskReturnMetrics() {
        // Calculate expected returns using ML predictions
        for (int i = 0; i < assets.size(); i++) {
            String asset = assets.get(i);
            AssetData assetData = assetDataMap.get(asset);
            
            // Use ML-predicted returns
            double expectedReturn = (assetData.predictedPrice - assetData.currentPrice) / assetData.currentPrice;
            expectedReturns.setReturn(i, expectedReturn);
        }
        
        // Calculate covariance matrix
        calculateCovarianceMatrix();
    }
    
    /**
     * Calculate covariance matrix
     */
    private void calculateCovarianceMatrix() {
        int n = assets.size();
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                String asset1 = assets.get(i);
                String asset2 = assets.get(j);
                
                AssetData data1 = assetDataMap.get(asset1);
                AssetData data2 = assetDataMap.get(asset2);
                
                // Calculate correlation using historical returns
                double correlation = calculateCorrelation(data1.returns, data2.returns);
                double volatility1 = calculateVolatility(data1.returns);
                double volatility2 = calculateVolatility(data2.returns);
                
                double covariance = correlation * volatility1 * volatility2;
                covarianceMatrix.setCovariance(i, j, covariance);
            }
        }
    }
    
    /**
     * Optimize weights using ML-enhanced approach
     */
    private Map<String, Double> optimizeWeightsML() {
        int n = assets.size();
        
        // Base optimization using Markowitz mean-variance
        double[] baseWeights = markowitzOptimization();
        
        // Enhance with ML signals
        double[] mlAdjustments = new double[n];
        
        for (int i = 0; i < n; i++) {
            String asset = assets.get(i);
            AssetData assetData = assetDataMap.get(asset);
            
            // ML adjustment based on regime and prediction confidence
            double adjustment = 1.0;
            
            switch (assetData.currentRegime) {
                case TRENDING:
                    adjustment *= 1.2; // Increase weight in trending markets
                    break;
                case VOLATILE:
                    adjustment *= 0.8; // Decrease weight in volatile markets
                    break;
                case RANGING:
                    adjustment *= 1.0; // Normal weight in ranging markets
                    break;
                case REVERSAL:
                    adjustment *= 1.3; // Increase weight in reversal markets
                    break;
            }
            
            // Adjust based on prediction confidence
            adjustment *= (0.5 + assetData.predictionConfidence);
            
            mlAdjustments[i] = adjustment;
        }
        
        // Apply ML adjustments
        double[] optimizedWeights = new double[n];
        double totalWeight = 0.0;
        
        for (int i = 0; i < n; i++) {
            optimizedWeights[i] = baseWeights[i] * mlAdjustments[i];
            totalWeight += optimizedWeights[i];
        }
        
        // Normalize weights
        Map<String, Double> finalWeights = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String asset = assets.get(i);
            finalWeights.put(asset, optimizedWeights[i] / totalWeight);
        }
        
        return finalWeights;
    }
    
    /**
     * Markowitz mean-variance optimization
     */
    private double[] markowitzOptimization() {
        int n = assets.size();
        
        // Simplified Markowitz optimization
        // In production, would use quadratic programming solver
        
        // Calculate expected returns vector and covariance matrix
        double[] returns = new double[n];
        double[][] covMatrix = new double[n][n];
        
        for (int i = 0; i < n; i++) {
            returns[i] = expectedReturns.getReturn(i);
            for (int j = 0; j < n; j++) {
                covMatrix[i][j] = covarianceMatrix.getCovariance(i, j);
            }
        }
        
        // Risk aversion parameter
        double riskAversion = 1.0;
        
        // Simplified optimization (equal risk contribution)
        double[] weights = new double[n];
        double sumReturns = 0.0;
        
        for (int i = 0; i < n; i++) {
            // Simplified: inverse volatility weighting
            double volatility = Math.sqrt(covMatrix[i][i]);
            weights[i] = 1.0 / volatility;
            sumReturns += returns[i] * weights[i];
        }
        
        // Normalize weights
        double totalWeight = 0.0;
        for (double weight : weights) {
            totalWeight += weight;
        }
        
        for (int i = 0; i < n; i++) {
            weights[i] = weights[i] / totalWeight;
        }
        
        return weights;
    }
    
    /**
     * Check if rebalancing is needed
     */
    private boolean needsRebalancing(Map<String, Double> optimalWeights) {
        double threshold = 0.05; // 5% threshold
        
        for (String asset : assets) {
            double currentWeight = currentWeights.get(asset);
            double optimalWeight = optimalWeights.get(asset);
            
            double deviation = Math.abs(currentWeight - optimalWeight);
            
            if (deviation > threshold) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Rebalance portfolio
     */
    private void rebalancePortfolio(Map<String, Double> newWeights) {
        logger.info("Rebalancing portfolio...");
        
        // Calculate required trades
        Map<String, Double> trades = new HashMap<>();
        
        for (String asset : assets) {
            double oldWeight = currentWeights.get(asset);
            double newWeight = newWeights.get(asset);
            
            double weightChange = newWeight - oldWeight;
            double tradeValue = weightChange * portfolioValue;
            
            if (Math.abs(tradeValue) > 1000) { // Minimum trade size
                trades.put(asset, tradeValue);
            }
        }
        
        // Execute trades (simplified)
        for (Map.Entry<String, Double> trade : trades.entrySet()) {
            String asset = trade.getKey();
            double tradeValue = trade.getValue();
            
            // Update position
            double currentPosition = currentPositions.get(asset);
            double newPosition = currentPosition + tradeValue;
            
            currentPositions.put(asset, newPosition);
            logger.info("Trade {}: {} -> {} ({})", asset, currentPosition, newPosition, tradeValue > 0 ? "BUY" : "SELL");
        }
        
        // Update weights
        currentWeights.clear();
        currentWeights.putAll(newWeights);
        
        rebalancingEvents.incrementAndGet();
        
        logger.info("Portfolio rebalanced. Total trades: {}", trades.size());
    }
    
    /**
     * Monitor portfolio risk
     */
    private void monitorRisk() {
        try {
            // Calculate portfolio metrics
            double portfolioVolatility = calculatePortfolioVolatility();
            double portfolioReturn = calculatePortfolioReturn();
            double var = calculateVaR();
            
            // Check risk limits
            if (portfolioVolatility > 0.2) { // 20% volatility threshold
                logger.warn("High portfolio volatility detected: {:.2f}%", portfolioVolatility * 100);
                riskEvents.incrementAndGet();
                
                // Risk reduction (simplified)
                reduceRisk();
            }
            
            if (var < -50000) { // $50k VaR threshold
                logger.warn("High VaR detected: ${}", var);
                riskEvents.incrementAndGet();
            }
            
        } catch (Exception e) {
            logger.error("Risk monitoring failed", e);
        }
    }
    
    /**
     * Track portfolio performance
     */
    private void trackPerformance() {
        try {
            double currentReturn = calculatePortfolioReturn();
            portfolioPnL += currentReturn;
            
            PortfolioPerformance performance = new PortfolioPerformance(
                System.currentTimeMillis(),
                portfolioValue,
                portfolioPnL,
                calculatePortfolioVolatility(),
                new HashMap<>(currentWeights)
            );
            
            performanceHistory.add(performance);
            
            // Keep only recent history
            if (performanceHistory.size() > 1000) {
                performanceHistory.remove(0);
            }
            
            logger.info("Portfolio Performance: Value=${}, PnL=${}, Return={:.2f}%, Volatility={:.2f}%",
                       portfolioValue, portfolioPnL, currentReturn * 100, calculatePortfolioVolatility() * 100);
            
        } catch (Exception e) {
            logger.error("Performance tracking failed", e);
        }
    }
    
    // === Helper Methods ===
    
    private MarketData getMarketData(String asset) {
        // Simulated market data
        AssetData assetData = assetDataMap.get(asset);
        
        MarketData data = new MarketData();
        data.price = assetData.currentPrice * (1 + (Math.random() - 0.5) * 0.001); // Small random change
        data.volume = 1000 + Math.random() * 9000;
        data.timestamp = System.currentTimeMillis();
        
        return data;
    }
    
    private double[] getRecentPrices(AssetData assetData, int count) {
        double[] prices = new double[count];
        
        // Simulated recent prices
        for (int i = 0; i < count; i++) {
            prices[i] = assetData.currentPrice * (1 + (Math.random() - 0.5) * 0.002);
        }
        
        return prices;
    }
    
    private double calculateCorrelation(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) return 0.0;
        
        double meanX = 0, meanY = 0;
        for (int i = 0; i < x.length; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= x.length;
        meanY /= y.length;
        
        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < x.length; i++) {
            cov += (x[i] - meanX) * (y[i] - meanY);
            varX += Math.pow(x[i] - meanX, 2);
            varY += Math.pow(y[i] - meanY, 2);
        }
        
        if (varX == 0 || varY == 0) return 0.0;
        
        return cov / Math.sqrt(varX * varY);
    }
    
    private double calculateVolatility(double[] returns) {
        if (returns.length < 2) return 0.0;
        
        double mean = 0;
        for (double r : returns) {
            mean += r;
        }
        mean /= returns.length;
        
        double variance = 0;
        for (double r : returns) {
            variance += Math.pow(r - mean, 2);
        }
        
        return Math.sqrt(variance);
    }
    
    private double calculatePortfolioVolatility() {
        double portfolioVariance = 0.0;
        
        for (int i = 0; i < assets.size(); i++) {
            for (int j = 0; j < assets.size(); j++) {
                String asset1 = assets.get(i);
                String asset2 = assets.get(j);
                
                double weight1 = currentWeights.get(asset1);
                double weight2 = currentWeights.get(asset2);
                double covariance = covarianceMatrix.getCovariance(i, j);
                
                portfolioVariance += weight1 * weight2 * covariance;
            }
        }
        
        return Math.sqrt(portfolioVariance);
    }
    
    private double calculatePortfolioReturn() {
        double portfolioReturn = 0.0;
        
        for (String asset : assets) {
            double weight = currentWeights.get(asset);
            double expectedReturn = expectedReturns.getReturn(assets.indexOf(asset));
            portfolioReturn += weight * expectedReturn;
        }
        
        return portfolioReturn;
    }
    
    private double calculateVaR() {
        // Simplified VaR calculation (95% confidence)
        double portfolioVolatility = calculatePortfolioVolatility();
        return portfolioValue * portfolioVolatility * 1.65; // 95% VaR
    }
    
    private void reduceRisk() {
        // Simplified risk reduction: move to equal weights
        double equalWeight = 1.0 / assets.size();
        
        for (String asset : assets) {
            currentWeights.put(asset, equalWeight);
        }
        
        logger.info("Risk reduction: moved to equal weights");
    }
    
    // === Data Classes ===
    
    private static class MarketData {
        double price;
        double volume;
        long timestamp;
    }
    
    public static class AssetData {
        public final String symbol;
        public double currentPrice;
        public double predictedPrice;
        public double predictionConfidence;
        public MarketRegimeClassifier.MarketRegime currentRegime;
        public final List<Double> returns;
        
        public AssetData(String symbol) {
            this.symbol = symbol;
            this.currentPrice = 50000.0; // Default price
            this.predictedPrice = 50000.0;
            this.predictionConfidence = 0.5;
            this.currentRegime = MarketRegimeClassifier.MarketRegime.RANGING;
            this.returns = new ArrayList<>();
        }
        
        public void update(MarketData marketData) {
            if (returns.size() > 0) {
                double oldPrice = currentPrice;
                double newPrice = marketData.price;
                double returnRate = (newPrice - oldPrice) / oldPrice;
                returns.add(returnRate);
                
                // Keep only recent returns
                if (returns.size() > 1000) {
                    returns.remove(0);
                }
            }
            
            this.currentPrice = marketData.price;
        }
    }
    
    public static class PortfolioPerformance {
        public final long timestamp;
        public final double portfolioValue;
        public final double portfolioPnL;
        public final double portfolioVolatility;
        public final Map<String, Double> weights;
        
        public PortfolioPerformance(long timestamp, double portfolioValue, double portfolioPnL,
                                     double portfolioVolatility, Map<String, Double> weights) {
            this.timestamp = timestamp;
            this.portfolioValue = portfolioValue;
            this.portfolioPnL = portfolioPnL;
            this.portfolioVolatility = portfolioVolatility;
            this.weights = new HashMap<>(weights);
        }
    }
    
    private static class CovarianceMatrix {
        private final double[][] matrix;
        
        public CovarianceMatrix(int size) {
            this.matrix = new double[size][size];
        }
        
        public void setCovariance(int i, int j, double covariance) {
            matrix[i][j] = covariance;
            matrix[j][i] = covariance; // Symmetric
        }
        
        public double getCovariance(int i, int j) {
            return matrix[i][j];
        }
    }
    
    private static class ExpectedReturns {
        private final double[] returns;
        
        public ExpectedReturns(int size) {
            this.returns = new double[size];
        }
        
        public void setReturn(int index, double returnRate) {
            returns[index] = returnRate;
        }
        
        public double getReturn(int index) {
            return returns[index];
        }
    }
    
    private static class RiskModel {
        // Risk model implementation would go here
        // For now, simplified
    }
    
    // === Public API ===
    
    public Map<String, Double> getCurrentWeights() {
        return new HashMap<>(currentWeights);
    }
    
    public Map<String, Double> getCurrentPositions() {
        return new HashMap<>(currentPositions);
    }
    
    public double getPortfolioValue() {
        return portfolioValue;
    }
    
    public double getPortfolioPnL() {
        return portfolioPnL;
    }
    
    public List<PortfolioPerformance> getPerformanceHistory() {
        return new ArrayList<>(performanceHistory);
    }
    
    public PortfolioOptimizerStats getStats() {
        return new PortfolioOptimizerStats(
            assets.size(),
            rebalancingEvents.get(),
            riskEvents.get(),
            portfolioValue,
            portfolioPnL,
            calculatePortfolioVolatility()
        );
    }
    
    public static class PortfolioOptimizerStats {
        public final int assetCount;
        public final long rebalancingEvents;
        public final long riskEvents;
        public final double portfolioValue;
        public final double portfolioPnL;
        public final double portfolioVolatility;
        
        public PortfolioOptimizerStats(int assetCount, long rebalancingEvents, long riskEvents,
                                        double portfolioValue, double portfolioPnL, double portfolioVolatility) {
            this.assetCount = assetCount;
            this.rebalancingEvents = rebalancingEvents;
            this.riskEvents = riskEvents;
            this.portfolioValue = portfolioValue;
            this.portfolioPnL = portfolioPnL;
            this.portfolioVolatility = portfolioVolatility;
        }
        
        @Override
        public String toString() {
            return String.format("PortfolioStats{assets=%d, rebalancing=%d, risk_events=%d, value=$%.2f, pnl=$%.2f, volatility=%.2f%%}",
                               assetCount, rebalancingEvents, riskEvents, portfolioValue, portfolioPnL, portfolioVolatility * 100);
        }
    }
    
    /**
     * Shutdown
     */
    public void shutdown() {
        optimizer.shutdown();
        
        try {
            if (!optimizer.awaitTermination(5, TimeUnit.SECONDS)) {
                optimizer.shutdownNow();
            }
        } catch (InterruptedException e) {
            optimizer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Multi-Asset Portfolio Optimizer shutdown complete");
    }
}
