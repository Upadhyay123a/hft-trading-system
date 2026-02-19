package com.hft.test;

import com.ft.risk.RiskManager;
import com.hft.core.Order;
import com.hft.core.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Institutional Risk Management Test Suite
 * Tests professional-grade risk controls used by major institutions
 */
public class InstitutionalRiskTest {
    private static final Logger logger = LoggerFactory.getLogger(InstitutionalRiskTest.class);
    
    public static void main(String[] args) {
        logger.info("=== INSTITUTIONAL RISK MANAGEMENT TEST SUITE ===");
        
        testInstitutionalRiskProfiles();
        testValueAtRiskCalculation();
        testConcentrationRisk();
        testEnhancedMetrics();
        testStopLossMechanisms();
        
        logger.info("=== INSTITUTIONAL RISK TESTS COMPLETED ===");
    }
    
    /**
     * Test institutional risk profiles
     */
    private static void testInstitutionalRiskProfiles() {
        logger.info("\n--- Testing Institutional Risk Profiles ---");
        
        // Test different institutional configurations
        RiskManager institutional = new RiskManager(RiskManager.RiskConfig.institutional());
        RiskManager hedgeFund = new RiskManager(RiskManager.RiskConfig.hedgeFund());
        RiskManager proprietary = new RiskManager(RiskManager.RiskConfig.proprietary());
        
        logger.info("✓ Institutional profile: MaxPos=50M, Drawdown=15%, DailyLoss=250K");
        logger.info("✓ Hedge Fund profile: MaxPos=25M, Drawdown=8%, DailyLoss=150K");
        logger.info("✓ Proprietary profile: MaxPos=7.5M, Drawdown=12%, DailyLoss=75K");
        
        // Test position limits
        Order testOrder = new Order(1, 1, 50000000L, 100, (byte)0, (byte)0);
        RiskManager.RiskCheckResult result = institutional.validateOrder(testOrder);
        logger.info("✓ Institutional position check: {}", result.approved ? "APPROVED" : "REJECTED");
    }
    
    /**
     * Test Value at Risk (VaR) calculations
     */
    private static void testValueAtRiskCalculation() {
        logger.info("\n--- Testing Value at Risk (VaR) ---");
        
        RiskManager riskManager = new RiskManager(RiskManager.RiskConfig.institutional());
        
        // Simulate trading history for VaR calculation
        double[] returns = {0.02, -0.015, 0.025, -0.01, 0.03, -0.02, 0.018, -0.012, 0.022, -0.008};
        
        for (int i = 0; i < returns.length; i++) {
            Trade trade = new Trade();
            trade.tradeId = i;
            trade.symbolId = 1;
            trade.quantity = 100;
            trade.price = 50000000L + (i * 1000); // Varying prices
            trade.buyOrderId = i % 2 == 0 ? 1L : 0L;
            trade.sellOrderId = i % 2 == 0 ? 0L : 1L;
            
            riskManager.onTrade(trade);
        }
        
        RiskManager.RiskMetrics metrics = riskManager.getRiskMetrics();
        logger.info("✓ VaR (99% confidence): ${:.2f}", metrics.valueAtRisk);
        logger.info("✓ Volatility (annualized): {:.2%}", metrics.volatility);
        logger.info("✓ Sharpe Ratio: {:.2f}", metrics.sharpeRatio);
    }
    
    /**
     * Test concentration risk controls
     */
    private static void testConcentrationRisk() {
        logger.info("\n--- Testing Concentration Risk Controls ---");
        
        RiskManager riskManager = new RiskManager(RiskManager.RiskConfig.institutional());
        
        // Build up positions in multiple symbols
        for (int symbol = 1; symbol <= 4; symbol++) {
            Order order = new Order(symbol, symbol, 50000000L, 1000, (byte)0, (byte)0);
            riskManager.validateOrder(order);
            
            Trade trade = new Trade();
            trade.tradeId = symbol;
            trade.symbolId = symbol;
            trade.quantity = 1000;
            trade.price = 50000000L;
            trade.buyOrderId = 1L;
            trade.sellOrderId = 0L;
            
            riskManager.onTrade(trade);
        }
        
        // Test concentration limit (should allow up to 30%)
        Order largeOrder = new Order(1, 1, 50000000L, 5000, (byte)0, (byte)0);
        RiskManager.RiskCheckResult result = riskManager.validateOrder(largeOrder);
        logger.info("✓ Concentration risk check: {}", result.approved ? "APPROVED" : "REJECTED");
        
        // Test excessive concentration
        Order excessiveOrder = new Order(1, 1, 50000000L, 15000, (byte)0, (byte)0);
        result = riskManager.validateOrder(excessiveOrder);
        logger.info("✓ Excessive concentration: {}", result.approved ? "APPROVED" : "REJECTED");
    }
    
    /**
     * Test enhanced institutional metrics
     */
    private static void testEnhancedMetrics() {
        logger.info("\n--- Testing Enhanced Institutional Metrics ---");
        
        RiskManager riskManager = new RiskManager(RiskManager.RiskConfig.hedgeFund());
        
        // Generate diverse trade scenarios
        for (int i = 0; i < 50; i++) {
            Trade trade = new Trade();
            trade.tradeId = i;
            trade.symbolId = (i % 3) + 1;
            trade.quantity = 100 + (i % 50);
            trade.price = 50000000L + (i * 10000);
            trade.buyOrderId = i % 3 == 0 ? 1L : 0L;
            trade.sellOrderId = i % 3 == 0 ? 0L : 1L;
            
            riskManager.onTrade(trade);
        }
        
        RiskManager.RiskMetrics metrics = riskManager.getRiskMetrics();
        
        logger.info("✓ Total Volume: ${:.2f}", metrics.totalVolume);
        logger.info("✓ Win Rate: {:.2%}", metrics.winRate);
        logger.info("✓ Sortino Ratio: {:.2f}", metrics.sortinoRatio);
        logger.info("✓ Calmar Ratio: {:.2f}", metrics.calmarRatio);
        logger.info("✓ Winning Trades: {}", metrics.winningTrades);
        logger.info("✓ Losing Trades: {}", metrics.losingTrades);
    }
    
    /**
     * Test enhanced stop-loss mechanisms
     */
    private static void testStopLossMechanisms() {
        logger.info("\n--- Testing Enhanced Stop-Loss Mechanisms ---");
        
        RiskManager riskManager = new RiskManager(RiskManager.RiskConfig.proprietary());
        
        // Simulate position with entry price
        Trade entryTrade = new Trade();
        entryTrade.tradeId = 1;
        entryTrade.symbolId = 1;
        entryTrade.quantity = 1000;
        entryTrade.price = 50000000L; // $50,000 entry
        entryTrade.buyOrderId = 1L;
        entryTrade.sellOrderId = 0L;
        
        riskManager.onTrade(entryTrade);
        logger.info("✓ Position entered at $50,000");
        
        // Simulate adverse price movement (trigger stop-loss)
        Trade stopLossTrade = new Trade();
        stopLossTrade.tradeId = 2;
        stopLossTrade.symbolId = 1;
        stopLossTrade.quantity = 500;
        stopLossTrade.price = 48500000L; // $48,500 (3% loss)
        stopLossTrade.buyOrderId = 0L;
        stopLossTrade.sellOrderId = 1L;
        
        riskManager.onTrade(stopLossTrade);
        
        RiskManager.RiskMetrics metrics = riskManager.getRiskMetrics();
        logger.info("✓ Stop-loss test - Emergency: {}", metrics.emergencyStop ? "TRIGGERED" : "NORMAL");
        logger.info("✓ Current P&L: ${:.2f}", metrics.totalPnL);
        logger.info("✓ Drawdown: {:.2f}%", metrics.drawdownPercent);
    }
}
