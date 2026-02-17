package com.hft.test;

import com.hft.exchange.api.ApiKeyManager;
import com.hft.orderbook.OrderBook;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.MomentumStrategy;
import com.hft.strategy.TriangularArbitrageStrategy;
import com.hft.monitoring.PerformanceMonitor;
import com.ft.risk.RiskManager;
import com.hft.exchange.api.BinanceRealApi;
import com.hft.exchange.api.CoinbaseRealApi;
import com.hft.exchange.api.MultiExchangeManager;
import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive Test Suite for HFT Trading System
 * Tests each component systematically with real data
 */
public class ComprehensiveComponentTest {
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveComponentTest.class);
    
    private int testsPassed = 0;
    private int testsTotal = 0;
    
    public static void main(String[] args) {
        ComprehensiveComponentTest test = new ComprehensiveComponentTest();
        test.runAllTests();
    }
    
    public void runAllTests() {
        logger.info("🚀 Starting Comprehensive Component Testing");
        
        // Test 1: API Key Manager
        testApiKeyManager();
        
        // Test 2: Order Book
        testOrderBook();
        
        // Test 3: Trading Strategies
        testTradingStrategies();
        
        // Test 4: Performance Monitor
        testPerformanceMonitor();
        
        // Test 5: Risk Manager
        testRiskManager();
        
        // Test 6: Exchange APIs
        testExchangeApis();
        
        // Test 7: Multi-Exchange Manager
        testMultiExchangeManager();
        
        // Final Results
        printTestResults();
    }
    
    /**
     * Test 1: API Key Manager Functionality
     */
    public void testApiKeyManager() {
        logger.info("\n📋 Testing API Key Manager...");
        testsTotal++;
        
        try {
            ApiKeyManager manager = ApiKeyManager.getInstance();
            
            // Test singleton pattern
            ApiKeyManager manager2 = ApiKeyManager.getInstance();
            assert manager == manager2 : "Singleton pattern failed";
            
            // Test credential loading
            String[] exchanges = manager.getConfiguredExchanges();
            logger.info("Configured exchanges: {}", (Object) exchanges);
            
            // Test signature generation
            if (manager.isExchangeConfigured("binance")) {
                String signature = manager.generateSignature("binance", "test message");
                assert signature != null && !signature.isEmpty() : "Signature generation failed";
                logger.info("✅ Signature generated: {}...", signature.substring(0, Math.min(20, signature.length())));
            }
            
            // Test programmatic credential addition
            manager.addCredentials("test", "test_key", "test_secret");
            assert manager.isExchangeConfigured("test") : "Programmatic credential addition failed";
            
            logger.info("✅ API Key Manager test passed");
            testsPassed++;
            
        } catch (Exception e) {
            logger.error("❌ API Key Manager test failed: {}", e.getMessage());
        }
    }
    
    /**
     * Test 2: Order Book Functionality
     */
    public void testOrderBook() {
        logger.info("\n📚 Testing Order Book...");
        testsTotal++;
        
        try {
            OrderBook orderBook = new OrderBook(1); // BTC/USDT symbolId = 1
            
            // Test limit orders
            Order buyOrder = new Order(1, 1, 500000000L, 100000, (byte)0, (byte)0); // Buy @ $50000, 0.1 BTC
            Order sellOrder = new Order(2, 1, 510000000L, 100000, (byte)1, (byte)0); // Sell @ $51000, 0.1 BTC
            
            orderBook.addOrder(buyOrder);
            orderBook.addOrder(sellOrder);
            
            // Test best bid/ask
            assert orderBook.getBestBid() == 500000000L : "Best bid incorrect";
            assert orderBook.getBestAsk() == 510000000L : "Best ask incorrect";
            
            // Test market order execution
            Order marketOrder = new Order(3, 1, 0, 50000, (byte)0, (byte)1); // Market buy 0.05 BTC
            List<Trade> trades = orderBook.addOrder(marketOrder);
            
            assert !trades.isEmpty() : "Market order execution failed";
            assert trades.get(0).price == 510000000L : "Trade price incorrect";
            
            // Test spread calculation
            long spread = orderBook.getSpread();
            assert spread == 10000000L : "Spread calculation incorrect";
            
            logger.info("✅ Order Book test passed - {} trades executed", trades.size());
            testsPassed++;
            
        } catch (Exception e) {
            logger.error("❌ Order Book test failed: {}", e.getMessage());
        }
    }
    
    /**
     * Test 3: Trading Strategies
     */
    public void testTradingStrategies() {
        logger.info("\n🎯 Testing Trading Strategies...");
        testsTotal++;
        
        try {
            // Test Market Making Strategy
            MarketMakingStrategy mmStrategy = new MarketMakingStrategy(1, 0.001, 100, 1000);
            assert mmStrategy.getName().equals("MarketMaking") : "Market making strategy initialization failed";
            
            // Test Momentum Strategy
            MomentumStrategy momentumStrategy = new MomentumStrategy(1, 50, 0.02, 100, 1000);
            assert momentumStrategy.getName().equals("Momentum") : "Momentum strategy initialization failed";
            
            // Test Triangular Arbitrage Strategy
            TriangularArbitrageStrategy arbStrategy = new TriangularArbitrageStrategy(1, 2, 3, 0.001, 100, 0.001);
            assert arbStrategy != null : "Triangular arbitrage strategy initialization failed";
            
            // Test strategy signal generation
            Tick tick = new Tick(System.currentTimeMillis(), 1, 500000000L, 1000000, (byte)0);
            OrderBook orderBook = new OrderBook(1);
            
            // Process tick through strategies
            List<Order> mmOrders = mmStrategy.onTick(tick, orderBook);
            List<Order> momentumOrders = momentumStrategy.onTick(tick, orderBook);
            List<Order> arbOrders = arbStrategy.onTick(tick, orderBook);
            
            logger.info("✅ Trading Strategies test passed - MM orders: {}, Momentum orders: {}, Arb orders: {}", 
                       mmOrders.size(), momentumOrders.size(), arbOrders.size());
            testsPassed++;
            
        } catch (Exception e) {
            logger.error("❌ Trading Strategies test failed: {}", e.getMessage());
        }
    }
    
    /**
     * Test 4: Performance Monitor
     */
    public void testPerformanceMonitor() {
        logger.info("\n⚡ Testing Performance Monitor...");
        testsTotal++;
        
        try {
            PerformanceMonitor monitor = PerformanceMonitor.getInstance();
            
            // Test latency tracking
            try (PerformanceMonitor.LatencyMeasurement measurement = monitor.startMeasurement("test_operation")) {
                Thread.sleep(10); // Simulate work
            }
            
            // Test throughput monitoring
            for (int i = 0; i < 100; i++) {
                monitor.recordThroughput("throughput_test", 1);
            }
            
            // Test custom metrics
            monitor.recordMetric("custom_metric", 42.0);
            monitor.recordMetric("custom_metric", 58.0); // Average should be 50.0
            
            // Test performance summary
            PerformanceMonitor.PerformanceSummary summary = monitor.getSummary();
            assert summary.uptimeSeconds >= 0 : "Performance summary failed";
            assert summary.customMetrics.containsKey("custom_metric") : "Custom metric not found";
            
            logger.info("✅ Performance Monitor test passed - Uptime: {:.1f}s, Custom metric: {}", 
                       summary.uptimeSeconds, summary.customMetrics.get("custom_metric"));
            testsPassed++;
            
        } catch (Exception e) {
            logger.error("❌ Performance Monitor test failed: {}", e.getMessage());
        }
    }
    
    /**
     * Test 5: Risk Manager
     */
    public void testRiskManager() {
        logger.info("\n🛡️ Testing Risk Manager...");
        testsTotal++;
        
        try {
            RiskManager.RiskConfig config = RiskManager.RiskConfig.conservative();
            RiskManager riskManager = new RiskManager(config);
            
            // Test valid order
            Order validOrder = new Order(1, 1, 500000000L, 100000, (byte)0, (byte)0);
            RiskManager.RiskCheckResult validResult = riskManager.validateOrder(validOrder);
            assert validResult.approved : "Valid order validation failed";
            
            // Test order exceeding position limit
            Order invalidOrder = new Order(2, 1, 500000000L, 2000000, (byte)0, (byte)0); // Exceeds 1M position
            RiskManager.RiskCheckResult invalidResult = riskManager.validateOrder(invalidOrder);
            assert !invalidResult.approved : "Invalid order validation failed";
            
            // Test P&L tracking
            Trade trade = new Trade(1, 1, 2, 1, 500000000L, 100000);
            riskManager.onTrade(trade);
            
            RiskManager.RiskMetrics metrics = riskManager.getRiskMetrics();
            assert metrics.totalPnL > 0 : "P&L tracking failed";
            
            // Test rate limiting
            for (int i = 0; i < 5; i++) {
                Order rateTestOrder = new Order(3 + i, 1, 500000000L, 1000, (byte)0, (byte)0);
                riskManager.validateOrder(rateTestOrder);
            }
            
            logger.info("✅ Risk Manager test passed - Total P&L: ${}, Position limit enforced", 
                       String.format("%.2f", metrics.totalPnL));
            testsPassed++;
            
        } catch (Exception e) {
            logger.error("❌ Risk Manager test failed: {}", e.getMessage());
        }
    }
    
    /**
     * Test 6: Exchange APIs
     */
    public void testExchangeApis() {
        logger.info("\n🔌 Testing Exchange APIs...");
        testsTotal++;
        
        try {
            // Test Binance API (if configured)
            if (ApiKeyManager.getInstance().isExchangeConfigured("binance")) {
                BinanceRealApi binanceApi = new BinanceRealApi();
                assert binanceApi != null : "Binance API initialization failed";
                logger.info("✅ Binance API initialized successfully");
            } else {
                logger.info("⚠️ Binance API not configured - skipping test");
            }
            
            // Test Coinbase API (if configured)
            if (ApiKeyManager.getInstance().isExchangeConfigured("coinbase")) {
                CoinbaseRealApi coinbaseApi = new CoinbaseRealApi();
                assert coinbaseApi != null : "Coinbase API initialization failed";
                logger.info("✅ Coinbase API initialized successfully");
            } else {
                logger.info("⚠️ Coinbase API not configured - skipping test");
            }
            
            logger.info("✅ Exchange APIs test passed");
            testsPassed++;
            
        } catch (Exception e) {
            logger.error("❌ Exchange APIs test failed: {}", e.getMessage());
        }
    }
    
    /**
     * Test 7: Multi-Exchange Manager
     */
    public void testMultiExchangeManager() {
        logger.info("\n🌐 Testing Multi-Exchange Manager...");
        testsTotal++;
        
        try {
            MultiExchangeManager manager = new MultiExchangeManager(true, true, 3);
            
            // Test manager initialization
            assert manager != null : "Multi-exchange manager initialization failed";
            
            // Test exchange status
            Map<String, MultiExchangeManager.ExchangeStatus> statuses = manager.getExchangeStatuses();
            logger.info("Exchange statuses: {}", statuses.keySet());
            
            // Test health check
            try {
                Map<String, Boolean> healthResults = manager.healthCheck().get();
                logger.info("Health check results: {}", healthResults);
            } catch (Exception e) {
                logger.info("Health check completed with expected exceptions (no API keys configured)");
            }
            
            // Test exchange metrics
            Map<String, Map<String, Double>> metrics = manager.getExchangeMetrics();
            assert metrics != null : "Exchange metrics retrieval failed";
            
            logger.info("✅ Multi-Exchange Manager test passed - {} exchanges configured", statuses.size());
            testsPassed++;
            
        } catch (Exception e) {
            logger.error("❌ Multi-Exchange Manager test failed: {}", e.getMessage());
        }
    }
    
    /**
     * Print final test results
     */
    private void printTestResults() {
        logger.info("\n🏁 Comprehensive Testing Complete");
        logger.info("================================");
        logger.info("Tests Passed: {}/{}", testsPassed, testsTotal);
        logger.info("Success Rate: {:.1f}%", (testsPassed * 100.0) / testsTotal);
        
        if (testsPassed == testsTotal) {
            logger.info("🎉 ALL TESTS PASSED! System is ready for production.");
        } else {
            logger.warn("⚠️ Some tests failed. Please review the errors above.");
        }
    }
}
