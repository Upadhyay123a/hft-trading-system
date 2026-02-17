package com.hft.risk;

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
    private final AtomicLong totalPnL = new AtomicLong(0);
    private final AtomicLong dailyPnL = new AtomicLong(0);
    private final AtomicLong peakEquity = new AtomicLong(0);
    private final AtomicLong orderCount = new AtomicLong(0);
    private volatile long lastOrderTime = System.currentTimeMillis();
    private volatile long tradingDayStart = System.currentTimeMillis();
    
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
     * Pre-trade risk check - validates order before execution
     */
    public RiskCheckResult validateOrder(Order order) {
        if (emergencyStop) {
            return RiskCheckResult.rejected("Emergency stop activated: " + riskBreachReason);
        }
        
        // Check order rate limit
        if (!checkOrderRateLimit()) {
            return RiskCheckResult.rejected("Order rate limit exceeded");
        }
        
        // Check position limits
        long currentPos = positions.getOrDefault(order.symbolId, 0L);
        long newPos = currentPos + (order.isBuy() ? order.quantity : -order.quantity);
        
        if (Math.abs(newPos) > maxPositionSize) {
            return RiskCheckResult.rejected("Position limit exceeded: " + newPos + " > " + maxPositionSize);
        }
        
        // Check daily loss limit
        double currentDailyLoss = -dailyPnL.get() / 10000.0; // Convert ticks to dollars
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
        
        orderCount.incrementAndGet();
        lastOrderTime = System.currentTimeMillis();
        
        return RiskCheckResult.approved();
    }
    
    /**
     * Post-trade risk update - updates state after trade execution
     */
    public void onTrade(Trade trade) {
        // Update position
        positions.compute(trade.symbolId, (k, v) -> {
            long current = v == null ? 0L : v;
            return current + (trade.buyOrderId > 0 ? trade.quantity : -trade.quantity);
        });
        
        // Update P&L
        double tradeValue = trade.getPriceAsDouble() * trade.quantity;
        totalPnL.addAndGet((long)(tradeValue * 10000)); // Convert to ticks
        
        // Update daily P&L
        dailyPnL.addAndGet((long)(tradeValue * 10000));
        
        // Update peak equity
        long currentEquity = totalPnL.get();
        peakEquity.updateAndGet(peak -> Math.max(peak, currentEquity));
        
        // Check for stop-loss on individual positions
        checkStopLoss(trade);
        
        // Log risk metrics
        if (trade.tradeId % 100 == 0) {
            logRiskMetrics();
        }
    }
    
    /**
     * Check if position should be liquidated due to stop-loss
     */
    private void checkStopLoss(Trade trade) {
        // This is simplified - in practice you'd track entry prices per position
        // For now, we'll check if daily P&L exceeds stop-loss threshold
        double dailyLoss = -dailyPnL.get() / 10000.0;
        if (dailyLoss > stopLossPercent * 10000) { // Simplified calculation
            logger.warn("Stop-loss triggered: Daily loss ${}", String.format("%.2f", dailyLoss));
            // In a real system, this would trigger position liquidation
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
     * Get current risk metrics
     */
    public RiskMetrics getRiskMetrics() {
        return new RiskMetrics(
            positions,
            totalPnL.get() / 10000.0,
            dailyPnL.get() / 10000.0,
            calculateDrawdown(),
            emergencyStop,
            riskBreachReason
        );
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
        
        public RiskMetrics(ConcurrentHashMap<Integer, Long> positions, double totalPnL, 
                          double dailyPnL, double drawdownPercent, boolean emergencyStop, 
                          String emergencyReason) {
            this.positions = positions;
            this.totalPnL = totalPnL;
            this.dailyPnL = dailyPnL;
            this.drawdownPercent = drawdownPercent;
            this.emergencyStop = emergencyStop;
            this.emergencyReason = emergencyReason;
        }
    }
}
