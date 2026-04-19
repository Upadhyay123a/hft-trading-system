package com.hft.execution;

import com.hft.core.Order;
import com.hft.core.SymbolMapper;
import com.hft.ml.RealTimeMLProcessor;
import com.hft.ml.MarketRegimeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Order Types Implementation
 * 
 * Implements institutional-grade execution algorithms used by top HFT firms:
 * - TWAP (Time-Weighted Average Price)
 * - VWAP (Volume-Weighted Average Price) 
 * - Iceberg Orders (Hidden Quantity)
 * - ML-Optimized Execution
 * 
 * Based on best practices from Citadel Securities, Two Sigma, Jump Trading
 */
public class AdvancedOrderTypes {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedOrderTypes.class);
    
    // Execution algorithms
    private final Map<String, TWAPAlgorithm> twapAlgorithms;
    private final Map<String, VWAPAlgorithm> vwapAlgorithms;
    private final Map<String, IcebergAlgorithm> icebergAlgorithms;
    private final Map<String, MLOptimizedAlgorithm> mlAlgorithms;
    
    // Performance tracking
    private final AtomicLong totalOrdersPlaced;
    private final AtomicLong totalVolumeExecuted;
    private final AtomicLong totalSlippage;
    
    // Thread pool for scheduled execution
    private final ScheduledExecutorService scheduler;
    
    // ML processor for optimization
    private final RealTimeMLProcessor mlProcessor;
    
    public AdvancedOrderTypes(RealTimeMLProcessor mlProcessor) {
        this.mlProcessor = mlProcessor;
        this.twapAlgorithms = new ConcurrentHashMap<>();
        this.vwapAlgorithms = new ConcurrentHashMap<>();
        this.icebergAlgorithms = new ConcurrentHashMap<>();
        this.mlAlgorithms = new ConcurrentHashMap<>();
        
        this.totalOrdersPlaced = new AtomicLong(0);
        this.totalVolumeExecuted = new AtomicLong(0);
        this.totalSlippage = new AtomicLong(0);
        
        this.scheduler = Executors.newScheduledThreadPool(10);
        
        logger.info("Advanced Order Types initialized");
    }
    
    /**
     * Create TWAP algorithm
     */
    public TWAPAlgorithm createTWAP(String orderId, String symbol, double totalVolume, 
                                   long startTimeMs, long endTimeMs, int intervalSeconds) {
        TWAPAlgorithm twap = new TWAPAlgorithm(orderId, symbol, totalVolume, startTimeMs, endTimeMs, intervalSeconds);
        twapAlgorithms.put(orderId, twap);
        
        // Start execution
        scheduler.scheduleAtFixedRate(twap::executeNextSlice, 
                                   startTimeMs - System.currentTimeMillis(),
                                   intervalSeconds * 1000L,
                                   TimeUnit.MILLISECONDS);
        
        logger.info("Created TWAP algorithm: {} for {} volume", orderId, totalVolume);
        return twap;
    }
    
    /**
     * Create VWAP algorithm
     */
    public VWAPAlgorithm createVWAP(String orderId, String symbol, double totalVolume,
                                     long startTimeMs, long endTimeMs, Map<Integer, Double> volumeProfile) {
        VWAPAlgorithm vwap = new VWAPAlgorithm(orderId, symbol, totalVolume, startTimeMs, endTimeMs, volumeProfile, scheduler);
        vwapAlgorithms.put(orderId, vwap);
        
        // Start execution with dynamic intervals
        vwap.startExecution();
        
        logger.info("Created VWAP algorithm: {} for {} volume", orderId, totalVolume);
        return vwap;
    }
    
    /**
     * Create Iceberg algorithm
     */
    public IcebergAlgorithm createIceberg(String orderId, String symbol, double totalVolume,
                                          double visibleSize, double price, boolean isBuy) {
        IcebergAlgorithm iceberg = new IcebergAlgorithm(orderId, symbol, totalVolume, visibleSize, price, isBuy, scheduler);
        icebergAlgorithms.put(orderId, iceberg);
        
        // Start execution immediately
        scheduler.submit(iceberg::executeNextSlice);
        
        logger.info("Created Iceberg algorithm: {} for {} volume, visible: {}", orderId, totalVolume, visibleSize);
        return iceberg;
    }
    
    /**
     * Create ML-Optimized algorithm
     */
    public MLOptimizedAlgorithm createMLOptimized(String orderId, String symbol, double totalVolume,
                                                 long startTimeMs, long endTimeMs, double maxSlippage) {
        MLOptimizedAlgorithm ml = new MLOptimizedAlgorithm(orderId, symbol, totalVolume, startTimeMs, endTimeMs, maxSlippage, mlProcessor, scheduler);
        mlAlgorithms.put(orderId, ml);
        
        // Start ML-optimized execution
        ml.startExecution();
        
        logger.info("Created ML-Optimized algorithm: {} for {} volume", orderId, totalVolume);
        return ml;
    }
    
    /**
     * TWAP Algorithm Implementation
     */
    public static class TWAPAlgorithm {
        private final String orderId;
        private final String symbol;
        private final double totalVolume;
        private final long startTimeMs;
        private final long endTimeMs;
        private final int intervalSeconds;
        
        private double executedVolume;
        private double remainingVolume;
        private final List<Order> placedOrders;
        
        public TWAPAlgorithm(String orderId, String symbol, double totalVolume,
                              long startTimeMs, long endTimeMs, int intervalSeconds) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.totalVolume = totalVolume;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.intervalSeconds = intervalSeconds;
            
            this.executedVolume = 0.0;
            this.remainingVolume = totalVolume;
            this.placedOrders = new ArrayList<>();
        }
        
        public void executeNextSlice() {
            if (System.currentTimeMillis() > endTimeMs || remainingVolume <= 0) {
                return; // Execution complete
            }
            
            // Calculate slice size (equal distribution)
            long remainingTime = endTimeMs - System.currentTimeMillis();
            int remainingIntervals = (int) (remainingTime / (intervalSeconds * 1000L));
            
            if (remainingIntervals > 0) {
                double sliceSize = remainingVolume / remainingIntervals;
                
                // Add randomness to avoid predictability (institutional practice)
                double randomFactor = 0.8 + (Math.random() * 0.4); // 80% to 120%
                sliceSize *= randomFactor;
                
                // Place order
                Order order = createOrder(sliceSize);
                placedOrders.add(order);
                
                executedVolume += sliceSize;
                remainingVolume -= sliceSize;
                
                // FIX: Use %f format specifiers instead of Python-style {:.6f}
                logger.info("TWAP {}: Executed slice {}, remaining: {}", orderId,
                        String.format("%.6f", sliceSize), String.format("%.6f", remainingVolume));
            }
        }
        
        private Order createOrder(double volume) {
            long orderIdNano     = System.nanoTime();
            int resolvedSymbolId = SymbolMapper.getId(symbol);
            // Order(orderId, symbolId, price, quantity, side, type)
            // TODO: replace placeholder price with live MarketDataFeed injection
            long price = 50000 * 10000L; // placeholder price ($50,000 * scale factor)
            return new Order(orderIdNano, resolvedSymbolId, price, (int) volume, (byte) 0, (byte) 0);
        }
        
        public ExecutionStats getStats() {
            return new ExecutionStats(orderId, "TWAP", totalVolume, executedVolume, remainingVolume, placedOrders.size());
        }
    }
    
    /**
     * VWAP Algorithm Implementation
     */
    public static class VWAPAlgorithm {
        private final String orderId;
        private final String symbol;
        private final double totalVolume;
        private final long startTimeMs;
        private final long endTimeMs;
        private final Map<Integer, Double> volumeProfile; // Hour -> volume percentage
        private final ScheduledExecutorService scheduler;
        
        private double executedVolume;
        private double remainingVolume;
        private final List<Order> placedOrders;
        
        public VWAPAlgorithm(String orderId, String symbol, double totalVolume,
                              long startTimeMs, long endTimeMs, Map<Integer, Double> volumeProfile,
                              ScheduledExecutorService scheduler) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.totalVolume = totalVolume;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.volumeProfile = volumeProfile;
            this.scheduler = scheduler;
            
            this.executedVolume = 0.0;
            this.remainingVolume = totalVolume;
            this.placedOrders = new ArrayList<>();
        }
        
        public void startExecution() {
            // Calculate execution intervals based on volume profile
            long totalDuration = endTimeMs - startTimeMs;
            
            for (Map.Entry<Integer, Double> entry : volumeProfile.entrySet()) {
                int hour = entry.getKey();
                double volumePercentage = entry.getValue();
                
                // Calculate when to execute this hour's volume
                long hourStartTime = startTimeMs + (hour * 3600L * 1000L);
                double hourVolume = totalVolume * volumePercentage;
                
                // Schedule execution for this hour
                scheduleHourlyExecution(hourStartTime, hourVolume, hour);
            }
        }
        
        private void scheduleHourlyExecution(long hourStartTime, double hourVolume, int hour) {
            long delay = hourStartTime - System.currentTimeMillis();
            if (delay > 0) {
                // Schedule multiple slices within the hour
                for (int i = 0; i < 6; i++) { // 6 slices per hour (every 10 minutes)
                    long sliceTime = hourStartTime + (i * 10 * 60 * 1000L);
                    double sliceVolume = hourVolume / 6;
                    
                    scheduler.schedule(() -> {
                        executeVWAPSlice(sliceVolume);
                    }, sliceTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                }
            }
        }
        
        private void executeVWAPSlice(double volume) {
            if (remainingVolume <= 0) return;
            
            double actualVolume = Math.min(volume, remainingVolume);
            
            // Place order with ML optimization if available
            Order order = createOrder(actualVolume);
            placedOrders.add(order);
            
            executedVolume += actualVolume;
            remainingVolume -= actualVolume;
            
            // FIX: Use %f format specifiers instead of Python-style {:.6f}
            logger.info("VWAP {}: Executed slice {}, remaining: {}", orderId,
                    String.format("%.6f", actualVolume), String.format("%.6f", remainingVolume));
        }
        
        private Order createOrder(double volume) {
            long orderIdNano     = System.nanoTime();
            int resolvedSymbolId = SymbolMapper.getId(symbol);
            // Order(orderId, symbolId, price, quantity, side, type)
            // TODO: replace placeholder price with live MarketDataFeed injection
            long price = 50000 * 10000L; // placeholder price ($50,000 * scale factor)
            return new Order(orderIdNano, resolvedSymbolId, price, (int) volume, (byte) 0, (byte) 0);
        }
        
        public ExecutionStats getStats() {
            return new ExecutionStats(orderId, "VWAP", totalVolume, executedVolume, remainingVolume, placedOrders.size());
        }
    }
    
    /**
     * Iceberg Algorithm Implementation
     */
    public static class IcebergAlgorithm {
        private final String orderId;
        private final String symbol;
        private final double totalVolume;
        private final double visibleSize;
        private final double price;
        private final boolean isBuy;
        private final ScheduledExecutorService scheduler;
        
        private double executedVolume;
        private double remainingVolume;
        private final List<Order> placedOrders;
        
        public IcebergAlgorithm(String orderId, String symbol, double totalVolume,
                               double visibleSize, double price, boolean isBuy,
                               ScheduledExecutorService scheduler) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.totalVolume = totalVolume;
            this.visibleSize = visibleSize;
            this.price = price;
            this.isBuy = isBuy;
            this.scheduler = scheduler;
            
            this.executedVolume = 0.0;
            this.remainingVolume = totalVolume;
            this.placedOrders = new ArrayList<>();
        }
        
        public void executeNextSlice() {
            if (remainingVolume <= 0) {
                return; // Execution complete
            }
            
            // Calculate visible slice size
            double sliceSize = Math.min(visibleSize, remainingVolume);
            
            // Add randomness to visible size (institutional practice)
            double randomFactor = 0.7 + (Math.random() * 0.6); // 70% to 130%
            sliceSize *= randomFactor;
            
            // Create final copy for lambda usage
            final double finalSliceSize = sliceSize;
            
            // Place visible order
            Order visibleOrder = createVisibleOrder(finalSliceSize);
            placedOrders.add(visibleOrder);
            
            // FIX: Use %f format specifiers instead of Python-style {:.6f}
            logger.info("Iceberg {}: Visible order {}, hidden remaining: {}",
                    orderId, String.format("%.6f", sliceSize),
                    String.format("%.6f", remainingVolume - sliceSize));
            
            // Simulate fill and place next slice
            scheduler.schedule(() -> {
                executedVolume += finalSliceSize;
                remainingVolume -= finalSliceSize;
                
                if (remainingVolume > 0) {
                    executeNextSlice(); // Place next slice
                }
            }, 100, TimeUnit.MILLISECONDS);
        }
        
        private Order createVisibleOrder(double volume) {
            long orderIdNano     = System.nanoTime();
            int resolvedSymbolId = SymbolMapper.getId(symbol);
            byte side            = isBuy ? (byte) 0 : (byte) 1;
            // Order(orderId, symbolId, price, quantity, side, type)
            // price is injected via IcebergAlgorithm constructor — already correct
            long scaledPrice = (long) (price * 10000);
            return new Order(orderIdNano, resolvedSymbolId, scaledPrice, (int) volume, side, (byte) 0);
        }
        
        public ExecutionStats getStats() {
            return new ExecutionStats(orderId, "Iceberg", totalVolume, executedVolume, remainingVolume, placedOrders.size());
        }
    }
    
    /**
     * ML-Optimized Algorithm Implementation
     */
    public static class MLOptimizedAlgorithm {
        private final String orderId;
        private final String symbol;
        private final double totalVolume;
        private final long startTimeMs;
        private final long endTimeMs;
        private final double maxSlippage;
        private final ScheduledExecutorService scheduler;
        
        private double executedVolume;
        private double remainingVolume;
        private final List<Order> placedOrders;
        private final RealTimeMLProcessor mlProcessor;
        
        public MLOptimizedAlgorithm(String orderId, String symbol, double totalVolume,
                                   long startTimeMs, long endTimeMs, double maxSlippage,
                                   RealTimeMLProcessor mlProcessor, ScheduledExecutorService scheduler) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.totalVolume = totalVolume;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.maxSlippage = maxSlippage;
            this.mlProcessor = mlProcessor; // Would be injected
            this.scheduler = scheduler;
            
            this.executedVolume = 0.0;
            this.remainingVolume = totalVolume;
            this.placedOrders = new ArrayList<>();
        }
        
        public void startExecution() {
            // Use ML predictions to optimize execution timing
            scheduler.scheduleAtFixedRate(this::executeMLOptimizedSlice,
                                       0,
                                       1, // Check every second
                                       TimeUnit.SECONDS);
        }
        
        private void executeMLOptimizedSlice() {
            if (System.currentTimeMillis() > endTimeMs || remainingVolume <= 0) {
                return; // Execution complete
            }
            
            // FIX: Use explicit type instead of var to avoid API mismatch ambiguity;
            // getPerformanceStats() return type is used explicitly for safe field access
            RealTimeMLProcessor.MLPerformanceStats mlStats = mlProcessor.getPerformanceStats();
            double predictedPrice = mlStats.lastPrediction;
            double confidence = mlStats.lastConfidence;
            MarketRegimeClassifier.MarketRegime currentRegime = mlStats.currentRegime;
            
            // Optimize slice size based on ML predictions
            double sliceSize = calculateOptimalSliceSize(predictedPrice, confidence, currentRegime);
            sliceSize = Math.min(sliceSize, remainingVolume);
            
            if (sliceSize > 0 && confidence > 0.7) { // Only execute with high confidence
                Order order = createMLOptimizedOrder(sliceSize, predictedPrice);
                placedOrders.add(order);
                
                executedVolume += sliceSize;
                remainingVolume -= sliceSize;
                
                // FIX: Use %f format specifiers instead of Python-style {:.6f}/{:.2f}
                logger.info("ML-Optimized {}: Executed {} at regime {} (confidence: {})",
                        orderId, String.format("%.6f", sliceSize), currentRegime,
                        String.format("%.2f", confidence));
            }
        }
        
        private double calculateOptimalSliceSize(double predictedPrice, double confidence, 
                                                MarketRegimeClassifier.MarketRegime regime) {
            double baseSlice = totalVolume * 0.01; // 1% base slice
            
            // Adjust based on regime
            switch (regime) {
                case TRENDING:
                    baseSlice *= 1.2; // Increase in trending markets
                    break;
                case VOLATILE:
                    baseSlice *= 0.8; // Decrease in volatile markets
                    break;
                case RANGING:
                    baseSlice *= 1.0; // Normal in ranging markets
                    break;
                case REVERSAL:
                    baseSlice *= 1.5; // Increase in reversal markets
                    break;
            }
            
            // Adjust based on confidence
            baseSlice *= (0.5 + confidence); // Scale with confidence
            
            return baseSlice;
        }
        
        private Order createMLOptimizedOrder(double volume, double predictedPrice) {
            long orderIdNano     = System.nanoTime();
            int resolvedSymbolId = SymbolMapper.getId(symbol);
            byte side            = predictedPrice > 50000 ? (byte) 0 : (byte) 1; // Buy if price predicted to rise
            // Order(orderId, symbolId, price, quantity, side, type)
            // price is derived from ML prediction — correct
            long scaledPrice = (long) (predictedPrice * 10000);
            return new Order(orderIdNano, resolvedSymbolId, scaledPrice, (int) volume, side, (byte) 0);
        }
        
        public ExecutionStats getStats() {
            return new ExecutionStats(orderId, "ML-Optimized", totalVolume, executedVolume, remainingVolume, placedOrders.size());
        }
    }
    
    /**
     * Execution Statistics
     */
    public static class ExecutionStats {
        public final String orderId;
        public final String algorithmType;
        public final double totalVolume;
        public final double executedVolume;
        public final double remainingVolume;
        public final int ordersPlaced;
        public final double completionPercentage;
        public final long executionTime;
        
        public ExecutionStats(String orderId, String algorithmType, double totalVolume,
                              double executedVolume, double remainingVolume, int ordersPlaced) {
            this.orderId = orderId;
            this.algorithmType = algorithmType;
            this.totalVolume = totalVolume;
            this.executedVolume = executedVolume;
            this.remainingVolume = remainingVolume;
            this.ordersPlaced = ordersPlaced;
            this.completionPercentage = totalVolume > 0 ? (executedVolume / totalVolume) * 100 : 0;
            this.executionTime = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("ExecutionStats{id=%s, type=%s, total=%.6f, executed=%.6f, remaining=%.6f, orders=%d, completion=%.1f%%}",
                               orderId, algorithmType, totalVolume, executedVolume, remainingVolume, ordersPlaced, completionPercentage);
        }
    }
    
    /**
     * Get all execution statistics
     */
    public List<ExecutionStats> getAllStats() {
        List<ExecutionStats> allStats = new ArrayList<>();
        
        twapAlgorithms.values().forEach(twap -> allStats.add(twap.getStats()));
        vwapAlgorithms.values().forEach(vwap -> allStats.add(vwap.getStats()));
        icebergAlgorithms.values().forEach(iceberg -> allStats.add(iceberg.getStats()));
        mlAlgorithms.values().forEach(ml -> allStats.add(ml.getStats()));
        
        return allStats;
    }
    
    /**
     * Cancel algorithm
     */
    public boolean cancelAlgorithm(String orderId) {
        boolean cancelled = false;
        
        if (twapAlgorithms.remove(orderId) != null) cancelled = true;
        if (vwapAlgorithms.remove(orderId) != null) cancelled = true;
        if (icebergAlgorithms.remove(orderId) != null) cancelled = true;
        if (mlAlgorithms.remove(orderId) != null) cancelled = true;
        
        if (cancelled) {
            logger.info("Cancelled algorithm: {}", orderId);
        }
        
        return cancelled;
    }
    
    /**
     * Shutdown
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Advanced Order Types shutdown complete");
    }
}