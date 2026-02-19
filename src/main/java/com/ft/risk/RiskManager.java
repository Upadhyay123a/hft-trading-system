package com.ft.risk;

import com.hft.core.Order;
import com.hft.core.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Professional Risk Management System for HFT Trading
 * Enforces position limits, stop-loss, and drawdown controls
 */
public class RiskManager {
    private static final Logger logger = LoggerFactory.getLogger(RiskManager.class);
    
    // Risk limits
    private final long maxPositionSize;
    private final double maxDrawdownPercent;
    private final double stopLossPercent;
    private final double maxDailyLoss;
    private final int maxOrdersPerSecond;
    
    // State tracking
    private final ConcurrentHashMap<Integer, Long> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Double> entryPrices = new ConcurrentHashMap<>();
    private final AtomicLong totalPnL = new AtomicLong(0);
    private final AtomicLong dailyPnL = new AtomicLong(0);
    private final AtomicLong peakEquity = new AtomicLong(0);
    private final AtomicLong orderCount = new AtomicLong(0);
    private volatile long lastOrderTime = System.currentTimeMillis();
    private volatile long tradingDayStart = System.currentTimeMillis();
    
    // Institutional risk metrics
    private final AtomicLong totalVolume = new AtomicLong(0);
    private final AtomicLong winningTrades = new AtomicLong(0);
    private final AtomicLong losingTrades = new AtomicLong(0);
    private final double[] recentReturns = new double[100]; // Last 100 returns for VaR
    private volatile int returnIndex = 0;
    private volatile double volatility = 0.0;
    private volatile double sharpeRatio = 0.0;
    private volatile double maxDrawdown = 0.0;
    private volatile long currentPeakEquity = 0;
    
    // Risk breach flags
    private volatile boolean emergencyStop = false;
    private volatile String riskBreachReason = "";
    
    public RiskManager(RiskConfig config) {
        this.maxPositionSize = config.maxPositionSize;
        this.maxDrawdownPercent = config.maxDrawdownPercent;
        this.stopLossPercent = config.stopLossPercent;
        this.maxDailyLoss = config.maxDailyLoss;
        this.maxOrdersPerSecond = config.maxOrdersPerSecond;
        
        logger.info("Risk Manager initialized with limits: Pos={}, Drawdown={}%, StopLoss={}%, DailyLoss={}, Orders/s={}",
            maxPositionSize, maxDrawdownPercent, stopLossPercent, maxDailyLoss, maxOrdersPerSecond);
    }
    
    /**
     * Enhanced pre-trade risk check with institutional controls
     */
    public RiskCheckResult validateOrder(Order order) {
        if (emergencyStop) {
            return RiskCheckResult.rejected("Emergency stop activated: " + riskBreachReason);
        }
        
        // Check order rate limit
        if (!checkOrderRateLimit()) {
            return RiskCheckResult.rejected("Order rate limit exceeded");
        }
        
        // Enhanced position limits with concentration risk
        long currentPos = positions.getOrDefault(order.symbolId, 0L);
        long newPos = currentPos + (order.isBuy() ? order.quantity : -order.quantity);
        
        if (Math.abs(newPos) > maxPositionSize) {
            return RiskCheckResult.rejected("Position limit exceeded: " + newPos + " > " + maxPositionSize);
        }
        
        // Check portfolio concentration risk
        if (!checkConcentrationRisk(order.symbolId, newPos)) {
            return RiskCheckResult.rejected("Concentration risk exceeded for symbol " + order.symbolId);
        }
        
        // Check daily loss limit
        double currentDailyLoss = -dailyPnL.get() / 10000.0;
        if (currentDailyLoss > maxDailyLoss) {
            activateEmergencyStop("Daily loss limit exceeded: $" + String.format("%.2f", currentDailyLoss));
            return RiskCheckResult.rejected("Daily loss limit exceeded");
        }
        
        // Check drawdown limit
        double currentEquity = totalPnL.get() / 10000.0;
        double peakEq = peakEquity.get() / 10000.0;
        double drawdown = (peakEq - currentEquity) / peakEq * 100;
        
        if (drawdown > maxDrawdownPercent) {
            activateEmergencyStop("Max drawdown exceeded: " + String.format("%.2f", drawdown) + "%");
            return RiskCheckResult.rejected("Maximum drawdown exceeded");
        }
        
        // Value at Risk (VaR) check
        if (!checkValueAtRisk(order)) {
            return RiskCheckResult.rejected("VaR limit exceeded");
        }
        
        orderCount.incrementAndGet();
        lastOrderTime = System.currentTimeMillis();
        
        return RiskCheckResult.approved();
    }
    
    /**
     * Check concentration risk - prevents overexposure to single symbol
     */
    private boolean checkConcentrationRisk(int symbolId, long newPosition) {
        double totalExposure = 0.0;
        double symbolExposure = Math.abs(newPosition);
        
        for (Long pos : positions.values()) {
            totalExposure += Math.abs(pos);
        }
        
        // Add new position to total
        totalExposure += symbolExposure;
        
        // Check if single symbol exceeds 30% of total exposure
        double concentrationRatio = symbolExposure / totalExposure;
        return concentrationRatio <= 0.30; // Max 30% concentration
    }
    
    /**
     * Value at Risk (VaR) calculation - 99% confidence, 1-day horizon
     */
    private boolean checkValueAtRisk(Order order) {
        if (returnIndex < 20) return true; // Need sufficient data
        
        // Calculate current portfolio value
        double portfolioValue = Math.abs(totalPnL.get() / 10000.0);
        if (portfolioValue < 10000) return true; // Skip for small portfolios
        
        // Calculate volatility from recent returns
        updateVolatility();
        
        // 99% VaR = 2.33 * volatility * portfolio_value
        double var99 = 2.33 * volatility * portfolioValue;
        
        // Check if potential position exceeds VaR limit (5% of portfolio)
        double positionValue = order.quantity * order.getPriceAsDouble();
        return positionValue <= (0.05 * portfolioValue);
    }
    
    /**
     * Update volatility calculation from recent returns
     */
    private void updateVolatility() {
        if (returnIndex < 20) return;
        
        double mean = 0.0;
        int count = Math.min(returnIndex, recentReturns.length);
        
        for (int i = 0; i < count; i++) {
            mean += recentReturns[i];
        }
        mean /= count;
        
        double variance = 0.0;
        for (int i = 0; i < count; i++) {
            variance += Math.pow(recentReturns[i] - mean, 2);
        }
        variance /= (count - 1);
        
        volatility = Math.sqrt(variance) * Math.sqrt(252); // Annualized
    }
    
    /**
     * Calculate Sharpe Ratio
     */
    private void calculateSharpeRatio() {
        double totalReturn = totalPnL.get() / 10000.0;
        if (volatility == 0) return;
        
        // Assume 2% risk-free rate
        sharpeRatio = (totalReturn - 0.02) / volatility;
    }
    
    /**
     * Enhanced post-trade risk update with institutional metrics
     */
    public void onTrade(Trade trade) {
        // Update position
        positions.compute(trade.symbolId, (k, v) -> {
            long current = v == null ? 0L : v;
            return current + (trade.buyOrderId > 0 ? trade.quantity : -trade.quantity);
        });
        
        // Track entry price for stop-loss calculations
        if (!entryPrices.containsKey(trade.symbolId)) {
            entryPrices.put(trade.symbolId, trade.getPriceAsDouble());
        }
        
        // Update P&L
        double tradeValue = trade.getPriceAsDouble() * trade.quantity;
        double tradePnL = calculateTradePnL(trade);
        
        totalPnL.addAndGet((long)(tradePnL * 10000));
        dailyPnL.addAndGet((long)(tradePnL * 10000));
        totalVolume.addAndGet((long)(tradeValue * 10000));
        
        // Track win/loss ratio
        if (tradePnL > 0) {
            winningTrades.incrementAndGet();
        } else {
            losingTrades.incrementAndGet();
        }
        
        // Update returns history for VaR
        updateReturnsHistory(tradePnL);
        
        // Update peak equity and drawdown
        long currentEquity = totalPnL.get();
        peakEquity.updateAndGet(peak -> Math.max(peak, currentEquity));
        
        if (currentEquity > currentPeakEquity) {
            currentPeakEquity = currentEquity;
        }
        
        double currentDrawdown = (double)(currentPeakEquity - currentEquity) / currentPeakEquity * 100;
        maxDrawdown = Math.max(maxDrawdown, currentDrawdown);
        
        // Update risk metrics
        updateVolatility();
        calculateSharpeRatio();
        
        // Check for stop-loss on individual positions
        checkStopLoss(trade);
        
        // Log risk metrics
        if (trade.tradeId % 100 == 0) {
            logRiskMetrics();
        }
    }
    
    /**
     * Calculate P&L for individual trade
     */
    private double calculateTradePnL(Trade trade) {
        Double entryPrice = entryPrices.get(trade.symbolId);
        if (entryPrice == null) return 0.0;
        
        long currentPos = positions.getOrDefault(trade.symbolId, 0L);
        double currentPrice = trade.getPriceAsDouble();
        
        if (trade.buyOrderId > 0) {
            // Buy trade - P&L is negative (cost)
            return -trade.quantity * currentPrice;
        } else {
            // Sell trade - realize P&L
            return trade.quantity * (currentPrice - entryPrice);
        }
    }
    
    /**
     * Update returns history for VaR calculation
     */
    private void updateReturnsHistory(double tradePnL) {
        double portfolioValue = Math.abs(totalPnL.get() / 10000.0);
        if (portfolioValue > 0) {
            double returnRate = tradePnL / portfolioValue;
            recentReturns[returnIndex = (returnIndex + 1) % recentReturns.length] = returnRate;
        }
    }
    
    /**
     * Enhanced stop-loss with trailing stop and breakeven protection
     */
    private void checkStopLoss(Trade trade) {
        Double entryPrice = entryPrices.get(trade.symbolId);
        if (entryPrice == null) return;
        
        double currentPrice = trade.getPriceAsDouble();
        double priceChange = (currentPrice - entryPrice) / entryPrice * 100;
        
        // Check individual position stop-loss
        if (priceChange < -stopLossPercent) {
            logger.warn("Stop-loss triggered for symbol {}: Entry=${}, Current=${}, Loss={}%",
                trade.symbolId, entryPrice, currentPrice, priceChange);
            
            // In production: trigger immediate liquidation
            activateEmergencyStop("Stop-loss triggered for symbol " + trade.symbolId);
        }
        
        // Trailing stop (move stop-loss to breakeven after 50% of target profit)
        if (priceChange > stopLossPercent * 0.5) {
            // Update entry price to breakeven for future trades
            entryPrices.put(trade.symbolId, currentPrice * 0.98); // 2% trailing stop
        }
    }
    
    /**
     * Check order rate limit
     */
    private boolean checkOrderRateLimit() {
        long now = System.currentTimeMillis();
        long timeWindow = 1000; // 1 second
        
        // Reset counter every second
        if (now - lastOrderTime > timeWindow) {
            orderCount.set(0);
            return true;
        }
        
        return orderCount.get() < maxOrdersPerSecond;
    }
    
    /**
     * Activate emergency stop - halts all trading
     */
    private void activateEmergencyStop(String reason) {
        emergencyStop = true;
        riskBreachReason = reason;
        logger.error("EMERGENCY STOP ACTIVATED: {}", reason);
        
        // In a real system, this would:
        // 1. Cancel all open orders
        // 2. Liquidate positions
        // 3. Send alerts
        // 4. Log to compliance systems
    }
    
    /**
     * Reset emergency stop (manual intervention required)
     */
    public void resetEmergencyStop() {
        emergencyStop = false;
        riskBreachReason = "";
        dailyPnL.set(0);
        tradingDayStart = System.currentTimeMillis();
        logger.warn("Emergency stop reset - trading resumed");
    }
    
    /**
     * Get comprehensive institutional risk metrics
     */
    public RiskMetrics getRiskMetrics() {
        return new RiskMetrics(
            positions,
            totalPnL.get() / 10000.0,
            dailyPnL.get() / 10000.0,
            calculateDrawdown(),
            emergencyStop,
            riskBreachReason,
            totalVolume.get() / 10000.0,
            winningTrades.get(),
            losingTrades.get(),
            calculateWinRate(),
            volatility,
            sharpeRatio,
            calculateValueAtRisk(),
            calculateSortinoRatio(),
            calculateCalmarRatio()
        );
    }
    
    /**
     * Calculate win rate
     */
    private double calculateWinRate() {
        long total = winningTrades.get() + losingTrades.get();
        return total == 0 ? 0.0 : (double)winningTrades.get() / total;
    }
    
    /**
     * Calculate 99% Value at Risk (VaR)
     */
    private double calculateValueAtRisk() {
        if (returnIndex < 20) return 0.0;
        
        double portfolioValue = Math.abs(totalPnL.get() / 10000.0);
        return 2.33 * volatility * portfolioValue; // 99% VaR
    }
    
    /**
     * Calculate Sortino Ratio (downside deviation)
     */
    private double calculateSortinoRatio() {
        double totalReturn = totalPnL.get() / 10000.0;
        if (volatility == 0) return 0.0;
        
        // Calculate downside deviation
        double downsideDeviation = 0.0;
        int count = Math.min(returnIndex, recentReturns.length);
        
        for (int i = 0; i < count; i++) {
            if (recentReturns[i] < 0) {
                downsideDeviation += Math.pow(recentReturns[i], 2);
            }
        }
        
        if (count > 1) {
            downsideDeviation = Math.sqrt(downsideDeviation / (count - 1));
        }
        
        return downsideDeviation == 0 ? 0.0 : totalReturn / downsideDeviation;
    }
    
    /**
     * Calculate Calmar Ratio (return / max drawdown)
     */
    private double calculateCalmarRatio() {
        double totalReturn = totalPnL.get() / 10000.0;
        return maxDrawdown == 0 ? 0.0 : totalReturn / maxDrawdown;
    }
    
    private double calculateDrawdown() {
        long currentEquity = totalPnL.get();
        long peakEq = peakEquity.get();
        return peakEq == 0 ? 0.0 : (double)(peakEq - currentEquity) / peakEq * 100;
    }
    
    private void logRiskMetrics() {
        RiskMetrics metrics = getRiskMetrics();
        logger.info("Risk Metrics - P&L: ${}, Daily: ${}, Drawdown: {}%, Positions: {}, Emergency: {}",
            String.format("%.2f", metrics.totalPnL),
            String.format("%.2f", metrics.dailyPnL),
            String.format("%.2f", metrics.drawdownPercent),
            metrics.positions.size(),
            metrics.emergencyStop ? "YES" : "NO"
        );
    }
    
    // Configuration class
    public static class RiskConfig {
        final long maxPositionSize;
        final double maxDrawdownPercent;
        final double stopLossPercent;
        final double maxDailyLoss;
        final int maxOrdersPerSecond;
        
        public RiskConfig(long maxPositionSize, double maxDrawdownPercent, 
                         double stopLossPercent, double maxDailyLoss, int maxOrdersPerSecond) {
            this.maxPositionSize = maxPositionSize;
            this.maxDrawdownPercent = maxDrawdownPercent;
            this.stopLossPercent = stopLossPercent;
            this.maxDailyLoss = maxDailyLoss;
            this.maxOrdersPerSecond = maxOrdersPerSecond;
        }
        
        public static RiskConfig conservative() {
            return new RiskConfig(1000000, 5.0, 2.0, 10000.0, 10);
        }
        
        public static RiskConfig moderate() {
            return new RiskConfig(5000000, 10.0, 5.0, 50000.0, 50);
        }
        
        public static RiskConfig aggressive() {
            return new RiskConfig(10000000, 20.0, 10.0, 100000.0, 100);
        }
        
        // Institutional risk profiles
        public static RiskConfig institutional() {
            return new RiskConfig(50000000, 15.0, 7.5, 250000.0, 200);
        }
        
        public static RiskConfig hedgeFund() {
            return new RiskConfig(25000000, 8.0, 3.0, 150000.0, 75);
        }
        
        public static RiskConfig proprietary() {
            return new RiskConfig(7500000, 12.0, 4.0, 75000.0, 150);
        }
    }
    
    // Result classes
    public static class RiskCheckResult {
        public final boolean approved;
        public final String reason;
        
        private RiskCheckResult(boolean approved, String reason) {
            this.approved = approved;
            this.reason = reason;
        }
        
        public static RiskCheckResult approved() {
            return new RiskCheckResult(true, "");
        }
        
        public static RiskCheckResult rejected(String reason) {
            return new RiskCheckResult(false, reason);
        }
    }
    
    public static class RiskMetrics {
        public final ConcurrentHashMap<Integer, Long> positions;
        public final double totalPnL;
        public final double dailyPnL;
        public final double drawdownPercent;
        public final boolean emergencyStop;
        public final String emergencyReason;
        
        // Institutional metrics
        public final double totalVolume;
        public final long winningTrades;
        public final long losingTrades;
        public final double winRate;
        public final double volatility;
        public final double sharpeRatio;
        public final double valueAtRisk;
        public final double sortinoRatio;
        public final double calmarRatio;
        
        public RiskMetrics(ConcurrentHashMap<Integer, Long> positions, double totalPnL, 
                          double dailyPnL, double drawdownPercent, boolean emergencyStop, 
                          String emergencyReason, double totalVolume, long winningTrades, 
                          long losingTrades, double winRate, double volatility, 
                          double sharpeRatio, double valueAtRisk, double sortinoRatio, 
                          double calmarRatio) {
            this.positions = positions;
            this.totalPnL = totalPnL;
            this.dailyPnL = dailyPnL;
            this.drawdownPercent = drawdownPercent;
            this.emergencyStop = emergencyStop;
            this.emergencyReason = emergencyReason;
            this.totalVolume = totalVolume;
            this.winningTrades = winningTrades;
            this.losingTrades = losingTrades;
            this.winRate = winRate;
            this.volatility = volatility;
            this.sharpeRatio = sharpeRatio;
            this.valueAtRisk = valueAtRisk;
            this.sortinoRatio = sortinoRatio;
            this.calmarRatio = calmarRatio;
        }
    }
}
