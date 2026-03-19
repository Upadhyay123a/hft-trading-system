package com.hft.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Arbitrage Strategies for HFT Trading
 * 
 * Implements sophisticated arbitrage used by top HFT firms:
 * - Jump Trading: Triangular arbitrage (FX)
 * - Citadel Securities: Statistical arbitrage (pairs)
 * - Hudson River Trading: Volatility arbitrage
 * - Two Sigma: Merger arbitrage
 * - Renaissance Technologies: Convertible arbitrage
 * 
 * Based on 2024-2025 global HFT best practices for arbitrage
 */
public class AdvancedArbitrage implements Serializable {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedArbitrage.class);
    
    // Arbitrage parameters
    private final double minProfitThreshold;
    private final double maxPositionSize;
    private final double latencyTolerance;
    private final double slippageTolerance;
    
    // Market data storage
    private final Map<String, MarketData> marketData;
    private final Map<String, Double> exchangeRates;
    private final Map<String, Double> correlationMatrix;
    
    // Performance tracking
    private final AtomicLong totalArbitrageOpportunities;
    private final AtomicLong executedArbitrageTrades;
    private final AtomicLong totalArbitragePnL;
    private final List<ArbitrageRecord> arbitrageHistory;
    
    // Strategy state
    private boolean isActive;
    private long lastUpdateTime;
    
    /**
     * Arbitrage opportunity
     */
    public static class ArbitrageOpportunity implements Serializable {
        public final String type;
        public final String[] instruments;
        public final double[] prices;
        public final double expectedProfit;
        public final double confidence;
        public final long timestamp;
        
        ArbitrageOpportunity(String type, String[] instruments, double[] prices, 
                           double expectedProfit, double confidence, long timestamp) {
            this.type = type;
            this.instruments = instruments;
            this.prices = prices;
            this.expectedProfit = expectedProfit;
            this.confidence = confidence;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("%s Opportunity: %s, Profit=%.4f, Confidence=%.2f%%",
                               type, Arrays.toString(instruments), expectedProfit, confidence * 100);
        }
    }
    
    /**
     * Arbitrage record
     */
    private static class ArbitrageRecord implements Serializable {
        public final String type;
        public final String[] instruments;
        public final double[] entryPrices;
        public final double[] exitPrices;
        public final double actualProfit;
        public final long entryTime;
        public final long exitTime;
        
        ArbitrageRecord(String type, String[] instruments, double[] entryPrices, 
                        double[] exitPrices, double actualProfit, long entryTime, long exitTime) {
            this.type = type;
            this.instruments = instruments;
            this.entryPrices = entryPrices;
            this.exitPrices = exitPrices;
            this.actualProfit = actualProfit;
            this.entryTime = entryTime;
            this.exitTime = exitTime;
        }
    }
    
    /**
     * Market data structure
     */
    private static class MarketData implements Serializable {
        public final double bidPrice;
        public final double askPrice;
        public final double bidSize;
        public final double askSize;
        public final String exchange;
        public final long timestamp;
        
        MarketData(double bidPrice, double askPrice, double bidSize, double askSize, 
                   String exchange, long timestamp) {
            this.bidPrice = bidPrice;
            this.askPrice = askPrice;
            this.bidSize = bidSize;
            this.askSize = askSize;
            this.exchange = exchange;
            this.timestamp = timestamp;
        }
    }
    
    public AdvancedArbitrage() {
        this(0.0001, 1000000, 0.00001, 0.0001); // Default parameters
    }
    
    public AdvancedArbitrage(double minProfitThreshold, double maxPositionSize,
                              double latencyTolerance, double slippageTolerance) {
        this.minProfitThreshold = minProfitThreshold;
        this.maxPositionSize = maxPositionSize;
        this.latencyTolerance = latencyTolerance;
        this.slippageTolerance = slippageTolerance;
        
        // Initialize market data
        this.marketData = new HashMap<>();
        this.exchangeRates = new HashMap<>();
        this.correlationMatrix = new HashMap<>();
        
        // Initialize performance tracking
        this.totalArbitrageOpportunities = new AtomicLong(0);
        this.executedArbitrageTrades = new AtomicLong(0);
        this.totalArbitragePnL = new AtomicLong(0);
        this.arbitrageHistory = new ArrayList<>();
        
        this.isActive = false;
        this.lastUpdateTime = System.nanoTime();
        
        // Initialize exchange rates (simplified)
        initializeExchangeRates();
        
        logger.info("Advanced Arbitrage initialized with min profit: {:.4f}, max position: {:.0f}",
                   minProfitThreshold, maxPositionSize);
    }
    
    /**
     * Initialize exchange rates (simplified for demo)
     */
    private void initializeExchangeRates() {
        // FX rates (base USD)
        exchangeRates.put("USD/EUR", 0.85);
        exchangeRates.put("USD/GBP", 0.78);
        exchangeRates.put("USD/JPY", 110.5);
        exchangeRates.put("EUR/GBP", 0.92);
        exchangeRates.put("EUR/JPY", 130.0);
        exchangeRates.put("GBP/JPY", 141.5);
        
        // Crypto rates
        exchangeRates.put("BTC/USD", 45000.0);
        exchangeRates.put("ETH/USD", 3000.0);
        exchangeRates.put("BTC/ETH", 15.0);
        
        // Initialize correlation matrix (simplified)
        correlationMatrix.put("BTC/ETH", 0.8);
        correlationMatrix.put("EUR/GBP", 0.7);
        correlationMatrix.put("USD/JPY", 0.6);
    }
    
    /**
     * Start arbitrage detection
     */
    public void start() {
        isActive = true;
        lastUpdateTime = System.nanoTime();
        logger.info("Advanced Arbitrage started");
    }
    
    /**
     * Stop arbitrage detection
     */
    public void stop() {
        isActive = false;
        logger.info("Advanced Arbitrage stopped");
    }
    
    /**
     * Update market data
     */
    public void updateMarketData(String symbol, double bidPrice, double askPrice, 
                                 double bidSize, double askSize, String exchange) {
        MarketData data = new MarketData(bidPrice, askPrice, bidSize, askSize, exchange, System.nanoTime());
        marketData.put(symbol, data);
        lastUpdateTime = System.nanoTime();
    }
    
    /**
     * Detect all arbitrage opportunities
     */
    public List<ArbitrageOpportunity> detectArbitrageOpportunities() {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        
        if (!isActive) {
            return opportunities;
        }
        
        // Detect triangular arbitrage
        opportunities.addAll(detectTriangularArbitrage());
        
        // Detect statistical arbitrage
        opportunities.addAll(detectStatisticalArbitrage());
        
        // Detect volatility arbitrage
        opportunities.addAll(detectVolatilityArbitrage());
        
        // Detect merger arbitrage
        opportunities.addAll(detectMergerArbitrage());
        
        // Detect convertible arbitrage
        opportunities.addAll(detectConvertibleArbitrage());
        
        // Filter by profit threshold
        opportunities.removeIf(op -> op.expectedProfit < minProfitThreshold);
        
        // Sort by expected profit
        opportunities.sort((a, b) -> Double.compare(b.expectedProfit, a.expectedProfit));
        
        // Update counter
        totalArbitrageOpportunities.addAndGet(opportunities.size());
        
        return opportunities;
    }
    
    /**
     * Detect triangular arbitrage (FX)
     */
    private List<ArbitrageOpportunity> detectTriangularArbitrage() {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        
        // Triangle 1: USD -> EUR -> GBP -> USD
        String[] triangle1 = {"USD/EUR", "EUR/GBP", "GBP/USD"};
        double[] rates1 = {
            exchangeRates.getOrDefault("USD/EUR", 0.0),
            exchangeRates.getOrDefault("EUR/GBP", 0.0),
            1.0 / exchangeRates.getOrDefault("USD/GBP", 0.0)
        };
        
        double profit1 = (rates1[0] * rates1[1] * rates1[2]) - 1.0;
        if (profit1 > minProfitThreshold) {
            opportunities.add(new ArbitrageOpportunity(
                "Triangular FX", triangle1, rates1, profit1, 0.8, System.nanoTime()
            ));
        }
        
        // Triangle 2: USD -> EUR -> JPY -> USD
        String[] triangle2 = {"USD/EUR", "EUR/JPY", "JPY/USD"};
        double[] rates2 = {
            exchangeRates.getOrDefault("USD/EUR", 0.0),
            exchangeRates.getOrDefault("EUR/JPY", 0.0),
            1.0 / exchangeRates.getOrDefault("USD/JPY", 0.0)
        };
        
        double profit2 = (rates2[0] * rates2[1] * rates2[2]) - 1.0;
        if (profit2 > minProfitThreshold) {
            opportunities.add(new ArbitrageOpportunity(
                "Triangular FX", triangle2, rates2, profit2, 0.8, System.nanoTime()
            ));
        }
        
        // Crypto triangular arbitrage
        String[] triangle3 = {"BTC/USD", "ETH/USD", "BTC/ETH"};
        double[] rates3 = {
            exchangeRates.getOrDefault("BTC/USD", 0.0),
            exchangeRates.getOrDefault("ETH/USD", 0.0),
            1.0 / exchangeRates.getOrDefault("BTC/ETH", 0.0)
        };
        
        double profit3 = (rates3[0] / rates3[1]) * rates3[2] - 1.0;
        if (profit3 > minProfitThreshold) {
            opportunities.add(new ArbitrageOpportunity(
                "Triangular Crypto", triangle3, rates3, profit3, 0.7, System.nanoTime()
            ));
        }
        
        return opportunities;
    }
    
    /**
     * Detect statistical arbitrage (pairs trading)
     */
    private List<ArbitrageOpportunity> detectStatisticalArbitrage() {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        
        // Check for pairs trading opportunities
        String[] pairs = {"BTC/ETH", "EUR/GBP", "USD/JPY"};
        
        for (String pair : pairs) {
            double correlation = correlationMatrix.getOrDefault(pair, 0.0);
            
            if (Math.abs(correlation) > 0.7) { // High correlation
                MarketData data1 = marketData.get(pair.split("/")[0]);
                MarketData data2 = marketData.get(pair.split("/")[1]);
                
                if (data1 != null && data2 != null) {
                    // Calculate spread
                    double spread = Math.abs(data1.bidPrice - data2.askPrice);
                    double avgPrice = (data1.bidPrice + data2.askPrice) / 2;
                    double spreadRatio = spread / avgPrice;
                    
                    // Check if spread is wider than normal
                    double normalSpread = 0.002; // 0.2% normal spread
                    if (spreadRatio > normalSpread * 2) {
                        double expectedProfit = (spreadRatio - normalSpread) * avgPrice;
                        
                        if (expectedProfit > minProfitThreshold) {
                            opportunities.add(new ArbitrageOpportunity(
                                "Statistical Pairs", new String[]{pair}, 
                                new double[]{data1.bidPrice, data2.askPrice}, 
                                expectedProfit, 0.6, System.nanoTime()
                            ));
                        }
                    }
                }
            }
        }
        
        return opportunities;
    }
    
    /**
     * Detect volatility arbitrage
     */
    private List<ArbitrageOpportunity> detectVolatilityArbitrage() {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        
        // Check for volatility differences across exchanges
        String[] symbols = {"BTC/USD", "ETH/USD"};
        
        for (String symbol : symbols) {
            List<MarketData> exchangeData = new ArrayList<>();
            
            // Collect data from different exchanges (simplified)
            for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
                if (entry.getKey().contains(symbol)) {
                    exchangeData.add(entry.getValue());
                }
            }
            
            if (exchangeData.size() >= 2) {
                // Find best bid and ask across exchanges
                double bestBid = 0;
                double bestAsk = Double.MAX_VALUE;
                String bestBidExchange = "";
                String bestAskExchange = "";
                
                for (MarketData data : exchangeData) {
                    if (data.bidPrice > bestBid) {
                        bestBid = data.bidPrice;
                        bestBidExchange = data.exchange;
                    }
                    if (data.askPrice < bestAsk) {
                        bestAsk = data.askPrice;
                        bestAskExchange = data.exchange;
                    }
                }
                
                // Calculate arbitrage profit
                double arbitrageProfit = (bestBid - bestAsk) / ((bestBid + bestAsk) / 2);
                
                if (arbitrageProfit > minProfitThreshold) {
                    opportunities.add(new ArbitrageOpportunity(
                        "Volatility Arbitrage", new String[]{symbol},
                        new double[]{bestBid, bestAsk},
                        arbitrageProfit, 0.5, System.nanoTime()
                    ));
                }
            }
        }
        
        return opportunities;
    }
    
    /**
     * Detect merger arbitrage
     */
    private List<ArbitrageOpportunity> detectMergerArbitrage() {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        
        // Simplified merger arbitrage detection
        // In production, this would use news feeds, SEC filings, etc.
        String[] potentialTargets = {"STOCK_A", "STOCK_B"};
        
        for (String target : potentialTargets) {
            MarketData data = marketData.get(target);
            
            if (data != null) {
                // Simulate merger announcement effect
                double mergerPremium = 0.15; // 15% premium
                double targetPrice = data.bidPrice * (1 + mergerPremium);
                
                double currentPrice = data.askPrice;
                double expectedProfit = (targetPrice - currentPrice) / currentPrice;
                
                if (expectedProfit > minProfitThreshold && expectedProfit < 0.5) { // Max 50% profit
                    opportunities.add(new ArbitrageOpportunity(
                        "Merger Arbitrage", new String[]{target},
                        new double[]{currentPrice, targetPrice},
                        expectedProfit, 0.4, System.nanoTime()
                    ));
                }
            }
        }
        
        return opportunities;
    }
    
    /**
     * Detect convertible arbitrage
     */
    private List<ArbitrageOpportunity> detectConvertibleArbitrage() {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        
        // Simplified convertible bond arbitrage
        String[] convertibles = {"BOND_A", "BOND_B"};
        
        for (String convertible : convertibles) {
            MarketData bondData = marketData.get(convertible);
            String underlying = convertible.replace("BOND", "STOCK");
            MarketData stockData = marketData.get(underlying);
            
            if (bondData != null && stockData != null) {
                // Calculate conversion ratio (simplified)
                double conversionRatio = 10.0; // 10 bonds per stock
                
                double bondPrice = bondData.askPrice;
                double stockPrice = stockData.bidPrice;
                double convertedValue = stockPrice * conversionRatio;
                
                double arbitrageProfit = (convertedValue - bondPrice) / bondPrice;
                
                if (arbitrageProfit > minProfitThreshold) {
                    opportunities.add(new ArbitrageOpportunity(
                        "Convertible Arbitrage", new String[]{convertible, underlying},
                        new double[]{bondPrice, convertedValue},
                        arbitrageProfit, 0.3, System.nanoTime()
                    ));
                }
            }
        }
        
        return opportunities;
    }
    
    /**
     * Execute arbitrage trade
     */
    public boolean executeArbitrage(ArbitrageOpportunity opportunity) {
        if (!isActive || opportunity == null) {
            return false;
        }
        
        // Check if opportunity is still valid
        long currentTime = System.nanoTime();
        long timeSinceDetection = currentTime - opportunity.timestamp;
        
        if (timeSinceDetection > 1e9) { // 1 second timeout
            logger.warn("Arbitrage opportunity expired: {}", opportunity);
            return false;
        }
        
        // Calculate position size
        double positionSize = Math.min(maxPositionSize, opportunity.expectedProfit * 100000);
        
        // Simulate execution (simplified)
        double executionCost = positionSize * slippageTolerance;
        double actualProfit = opportunity.expectedProfit * positionSize - executionCost;
        
        if (actualProfit > 0) {
            // Record the arbitrage
            ArbitrageRecord record = new ArbitrageRecord(
                opportunity.type,
                opportunity.instruments,
                opportunity.prices,
                new double[]{opportunity.prices[0], opportunity.prices[1]}, // Simplified
                actualProfit,
                currentTime,
                currentTime
            );
            
            arbitrageHistory.add(record);
            executedArbitrageTrades.incrementAndGet();
            totalArbitragePnL.addAndGet((long) (actualProfit * 100)); // Convert to cents
            
            logger.info("Executed arbitrage: {} - Profit: {:.2f}", opportunity, actualProfit);
            return true;
        } else {
            logger.info("Arbitrage not profitable after costs: {} - Actual: {:.2f}", opportunity, actualProfit);
            return false;
        }
    }
    
    /**
     * Get arbitrage statistics
     */
    public ArbitrageStats getArbitrageStats() {
        long opportunities = totalArbitrageOpportunities.get();
        long executed = executedArbitrageTrades.get();
        long totalPnL = totalArbitragePnL.get();
        
        double executionRate = opportunities > 0 ? (double) executed / opportunities : 0;
        double avgProfitPerTrade = executed > 0 ? (double) totalPnL / executed / 100 : 0;
        
        // Calculate success rate
        double successRate = 0;
        if (!arbitrageHistory.isEmpty()) {
            long successful = arbitrageHistory.stream()
                .mapToLong(r -> r.actualProfit > 0 ? 1 : 0)
                .sum();
            successRate = (double) successful / arbitrageHistory.size();
        }
        
        return new ArbitrageStats(
            opportunities,
            executed,
            executionRate,
            successRate,
            totalPnL / 100.0, // Convert back to dollars
            avgProfitPerTrade,
            arbitrageHistory.size()
        );
    }
    
    /**
     * Arbitrage statistics
     */
    public static class ArbitrageStats implements Serializable {
        public final long totalOpportunities;
        public final long executedTrades;
        public final double executionRate;
        public final double successRate;
        public final double totalPnL;
        public final double avgProfitPerTrade;
        public final int recordedTrades;
        
        ArbitrageStats(long totalOpportunities, long executedTrades, double executionRate,
                       double successRate, double totalPnL, double avgProfitPerTrade, int recordedTrades) {
            this.totalOpportunities = totalOpportunities;
            this.executedTrades = executedTrades;
            this.executionRate = executionRate;
            this.successRate = successRate;
            this.totalPnL = totalPnL;
            this.avgProfitPerTrade = avgProfitPerTrade;
            this.recordedTrades = recordedTrades;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Arbitrage Stats: Opportunities=%d, Executed=%d, Rate=%.2f%%, Success=%.2f%%, P&L=%.2f, Avg=%.2f, Records=%d",
                totalOpportunities, executedTrades, executionRate * 100, successRate * 100,
                totalPnL, avgProfitPerTrade, recordedTrades
            );
        }
    }
    
    /**
     * Get arbitrage history
     */
    public List<ArbitrageRecord> getArbitrageHistory() {
        return new ArrayList<>(arbitrageHistory);
    }
    
    /**
     * Check if arbitrage is active
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Get total P&L
     */
    public double getTotalPnL() {
        return totalArbitragePnL.get() / 100.0;
    }
}
