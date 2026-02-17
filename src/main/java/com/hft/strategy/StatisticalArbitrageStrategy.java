package com.hft.strategy;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.orderbook.OrderBook;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistical Arbitrage Strategy for HFT
 * Uses mean reversion and cointegration to identify trading opportunities
 */
public class StatisticalArbitrageStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(StatisticalArbitrageStrategy.class);
    
    // Strategy parameters
    private final int[] symbols;                    // Symbols to trade
    private final int lookbackPeriod;               // Lookback period for statistics
    private final double zScoreThreshold;           // Z-score threshold for signals
    private final double minSpread;                 // Minimum spread requirement
    private final int orderSize;                    // Base order size
    
    // Data storage
    private final Map<Integer, ConcurrentLinkedQueue<Double>> priceHistory = new HashMap<>();
    private final Map<Integer, Double> lastPrices = new HashMap<>();
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    
    // Statistical model
    private double[] hedgeRatios;                   // Hedge ratios for pairs trading
    private double meanSpread;                      // Mean of the spread
    private double spreadStdDev;                    // Standard deviation of spread
    private boolean modelReady = false;
    
    // Performance tracking
    private double totalPnL = 0.0;
    private long signalsGenerated = 0;
    private long tradesExecuted = 0;
    private volatile ArbitragePosition activePosition = null;
    
    public StatisticalArbitrageStrategy(int[] symbols, int lookbackPeriod, 
                                      double zScoreThreshold, double minSpread, int orderSize) {
        this.symbols = symbols;
        this.lookbackPeriod = lookbackPeriod;
        this.zScoreThreshold = zScoreThreshold;
        this.minSpread = minSpread;
        this.orderSize = orderSize;
        
        // Initialize price history queues
        for (int symbol : symbols) {
            priceHistory.put(symbol, new ConcurrentLinkedQueue<>());
        }
        
        logger.info("Statistical Arbitrage Strategy initialized:");
        logger.info("Symbols: {}, Lookback: {}, Z-score threshold: {}, Min spread: {}%", 
            Arrays.toString(symbols), lookbackPeriod, zScoreThreshold, minSpread * 100);
    }
    
    @Override
    public void initialize() {
        logger.info("Initializing Statistical Arbitrage Strategy");
        hedgeRatios = new double[symbols.length - 1];
        logger.info("Strategy ready for data collection");
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Update price data
        updatePriceData(tick);
        
        // Check if we have enough data
        if (!hasSufficientData()) {
            return orders;
        }
        
        // Update statistical model
        updateStatisticalModel();
        
        // Skip if model is not ready or we have active position
        if (!modelReady || (activePosition != null && !activePosition.isComplete())) {
            return orders;
        }
        
        // Generate trading signals
        TradingSignal signal = generateSignal();
        if (signal != null) {
            signalsGenerated++;
            logger.info("Statistical arbitrage signal: {}, Z-score: {}", 
                signal.direction, String.format("%.3f", signal.zScore));
            
            // Execute trades
            orders = executeSignal(signal, orderBook);
        }
        
        return orders;
    }
    
    /**
     * Update price data from incoming tick
     */
    private void updatePriceData(Tick tick) {
        double price = tick.getPriceAsDouble();
        lastPrices.put(tick.symbolId, price);
        
        // Add to price history
        ConcurrentLinkedQueue<Double> history = priceHistory.get(tick.symbolId);
        if (history != null) {
            history.offer(price);
            
            // Maintain lookback period
            while (history.size() > lookbackPeriod) {
                history.poll();
            }
        }
    }
    
    /**
     * Check if we have sufficient data for analysis
     */
    private boolean hasSufficientData() {
        for (int symbol : symbols) {
            ConcurrentLinkedQueue<Double> history = priceHistory.get(symbol);
            if (history == null || history.size() < lookbackPeriod) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Update statistical model (cointegration analysis)
     */
    private void updateStatisticalModel() {
        if (symbols.length < 2) return;
        
        // Get price arrays
        double[][] prices = new double[symbols.length][lookbackPeriod];
        for (int i = 0; i < symbols.length; i++) {
            ConcurrentLinkedQueue<Double> history = priceHistory.get(symbols[i]);
            Double[] histArray = history.toArray(new Double[0]);
            for (int j = 0; j < lookbackPeriod; j++) {
                prices[i][j] = histArray[j];
            }
        }
        
        // Perform linear regression to find hedge ratios
        // Use first symbol as dependent variable, others as independent
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        
        // Prepare data: Y (dependent) and X (independent variables)
        double[] y = prices[0];
        double[][] x = new double[lookbackPeriod][symbols.length - 1];
        for (int i = 0; i < lookbackPeriod; i++) {
            for (int j = 1; j < symbols.length; j++) {
                x[i][j-1] = prices[j][i];
            }
        }
        
        regression.newSampleData(y, x);
        hedgeRatios = regression.estimateRegressionParameters();
        
        // Calculate spread
        double[] spreads = calculateSpreads(prices);
        
        // Calculate spread statistics
        meanSpread = calculateMean(spreads);
        spreadStdDev = calculateStdDev(spreads, meanSpread);
        
        modelReady = true;
    }
    
    /**
     * Calculate spreads using hedge ratios
     */
    private double[] calculateSpreads(double[][] prices) {
        double[] spreads = new double[lookbackPeriod];
        
        for (int i = 0; i < lookbackPeriod; i++) {
            double spread = prices[0][i]; // Base symbol
            
            // Subtract hedged positions
            for (int j = 1; j < symbols.length; j++) {
                spread -= hedgeRatios[j] * prices[j][i];
            }
            
            spreads[i] = spread;
        }
        
        return spreads;
    }
    
    /**
     * Generate trading signal based on current spread
     */
    private TradingSignal generateSignal() {
        if (!modelReady || lastPrices.size() < symbols.length) {
            return null;
        }
        
        // Calculate current spread
        double currentSpread = lastPrices.get(symbols[0]);
        for (int i = 1; i < symbols.length; i++) {
            currentSpread -= hedgeRatios[i] * lastPrices.get(symbols[i]);
        }
        
        // Calculate Z-score
        double zScore = (currentSpread - meanSpread) / spreadStdDev;
        
        // Generate signal based on Z-score threshold
        if (zScore > zScoreThreshold) {
            // Spread is too high - short the spread
            return new TradingSignal(TradeDirection.SHORT_SPREAD, zScore, currentSpread);
        } else if (zScore < -zScoreThreshold) {
            // Spread is too low - long the spread
            return new TradingSignal(TradeDirection.LONG_SPREAD, zScore, currentSpread);
        }
        
        return null;
    }
    
    /**
     * Execute trading signal
     */
    private List<Order> executeSignal(TradingSignal signal, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (signal.direction == TradeDirection.LONG_SPREAD) {
            // Buy base symbol, sell others
            orders.addAll(createLongSpreadOrders(orderBook));
        } else {
            // Sell base symbol, buy others
            orders.addAll(createShortSpreadOrders(orderBook));
        }
        
        // Track position
        activePosition = new ArbitragePosition(signal, orders);
        
        return orders;
    }
    
    /**
     * Create orders for long spread position
     */
    private List<Order> createLongSpreadOrders(OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Buy base symbol
        Order buyBase = new Order(
            orderIdGenerator.getAndIncrement(),
            symbols[0],
            orderBook.getBestAsk(),
            orderSize,
            (byte)0, // Buy
            (byte)0  // Limit order
        );
        orders.add(buyBase);
        
        // Sell other symbols with hedge ratios
        for (int i = 1; i < symbols.length; i++) {
            int hedgeSize = (int)(orderSize * hedgeRatios[i]);
            if (hedgeSize > 0) {
                Order sellHedge = new Order(
                    orderIdGenerator.getAndIncrement(),
                    symbols[i],
                    orderBook.getBestBid(),
                    hedgeSize,
                    (byte)1, // Sell
                    (byte)0  // Limit order
                );
                orders.add(sellHedge);
            }
        }
        
        return orders;
    }
    
    /**
     * Create orders for short spread position
     */
    private List<Order> createShortSpreadOrders(OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Sell base symbol
        Order sellBase = new Order(
            orderIdGenerator.getAndIncrement(),
            symbols[0],
            orderBook.getBestBid(),
            orderSize,
            (byte)1, // Sell
            (byte)0  // Limit order
        );
        orders.add(sellBase);
        
        // Buy other symbols with hedge ratios
        for (int i = 1; i < symbols.length; i++) {
            int hedgeSize = (int)(orderSize * hedgeRatios[i]);
            if (hedgeSize > 0) {
                Order buyHedge = new Order(
                    orderIdGenerator.getAndIncrement(),
                    symbols[i],
                    orderBook.getBestAsk(),
                    hedgeSize,
                    (byte)0, // Buy
                    (byte)0  // Limit order
                );
                orders.add(buyHedge);
            }
        }
        
        return orders;
    }
    
    public void onTrade(com.hft.core.Trade trade) {
        if (activePosition != null) {
            activePosition.onTrade(trade);
            
            if (activePosition.isComplete()) {
                double realizedPnL = activePosition.getRealizedPnL();
                totalPnL += realizedPnL;
                tradesExecuted++;
                
                logger.info("Statistical arbitrage completed: P&L ${}, Total P&L ${}", 
                    String.format("%.4f", realizedPnL), 
                    String.format("%.4f", totalPnL));
                
                activePosition = null;
            }
        }
    }
    
    @Override
    public String getName() {
        return "StatisticalArbitrage";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    @Override
    public void reset() {
        totalPnL = 0.0;
        signalsGenerated = 0;
        tradesExecuted = 0;
        activePosition = null;
        modelReady = false;
        
        // Clear price history
        for (ConcurrentLinkedQueue<Double> history : priceHistory.values()) {
            history.clear();
        }
        lastPrices.clear();
    }
    
    // Utility methods
    private double calculateMean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
    
    private double calculateStdDev(double[] values, double mean) {
        double sum = 0;
        for (double v : values) sum += Math.pow(v - mean, 2);
        return Math.sqrt(sum / values.length);
    }
    
    // Supporting classes
    public enum TradeDirection {
        LONG_SPREAD,
        SHORT_SPREAD
    }
    
    public static class TradingSignal {
        public final TradeDirection direction;
        public final double zScore;
        public final double currentSpread;
        
        public TradingSignal(TradeDirection direction, double zScore, double currentSpread) {
            this.direction = direction;
            this.zScore = zScore;
            this.currentSpread = currentSpread;
        }
    }
    
    public static class ArbitragePosition {
        private final TradingSignal signal;
        private final List<Order> orders;
        private final Map<Long, Double> fills = new HashMap<>();
        private boolean complete = false;
        
        public ArbitragePosition(TradingSignal signal, List<Order> orders) {
            this.signal = signal;
            this.orders = orders;
        }
        
        public void onTrade(com.hft.core.Trade trade) {
            fills.put(trade.tradeId, trade.getPriceAsDouble() * trade.quantity);
            
            // Simplified completion check
            if (fills.size() >= orders.size()) {
                complete = true;
            }
        }
        
        public boolean isComplete() {
            return complete;
        }
        
        public double getRealizedPnL() {
            // Simplified P&L based on Z-score reversion
            return Math.abs(signal.zScore) * 1000; // Scale factor
        }
    }
}
