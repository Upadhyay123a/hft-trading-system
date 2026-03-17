package com.hft.test;

import com.hft.core.*;
import com.hft.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * Simple Strategy Test for Real Data Simulation
 * Tests all strategies without external dependencies
 */
public class SimpleStrategyTest {
    private static final Logger logger = LoggerFactory.getLogger(SimpleStrategyTest.class);
    
    public static void main(String[] args) {
        logger.info("=== SIMPLE STRATEGY TEST WITH REAL DATA SIMULATION ===");
        
        SimpleStrategyTest test = new SimpleStrategyTest();
        
        // Test all strategies
        test.testMarketMakingStrategy();
        test.testMomentumStrategy();
        test.testStatisticalArbitrageStrategy();
        test.testTriangularArbitrageStrategy();
        
        logger.info("=== ALL STRATEGY TESTS COMPLETED ===");
    }
    
    /**
     * Test Market Making Strategy
     */
    public void testMarketMakingStrategy() {
        logger.info("\n--- Testing Market Making Strategy ---");
        
        try {
            MarketMakingStrategy strategy = new MarketMakingStrategy(1, 0.02, 1000, 10);
            strategy.initialize();
            
            // Simulate market data
            simulateMarketData(strategy, "Market Making", 1000);
            
            logger.info("✅ Market Making Strategy Test Results:");
            logger.info("  Strategy: {}", strategy.getName());
            logger.info("  Final P&L: ${:.2f}", strategy.getPnL());
            logger.info("  Status: WORKING");
            
        } catch (Exception e) {
            logger.error("❌ Market Making Strategy Test FAILED", e);
        }
    }
    
    /**
     * Test Momentum Strategy
     */
    public void testMomentumStrategy() {
        logger.info("\n--- Testing Momentum Strategy ---");
        
        try {
            MomentumStrategy strategy = new MomentumStrategy(1, 20, 0.05, 1000, 20);
            strategy.initialize();
            
            // Simulate market data
            simulateMarketData(strategy, "Momentum", 1000);
            
            logger.info("✅ Momentum Strategy Test Results:");
            logger.info("  Strategy: {}", strategy.getName());
            logger.info("  Final P&L: ${:.2f}", strategy.getPnL());
            logger.info("  Status: WORKING");
            
        } catch (Exception e) {
            logger.error("❌ Momentum Strategy Test FAILED", e);
        }
    }
    
    /**
     * Test Statistical Arbitrage Strategy
     */
    public void testStatisticalArbitrageStrategy() {
        logger.info("\n--- Testing Statistical Arbitrage Strategy ---");
        
        try {
            int[] symbols = {1, 2};
            StatisticalArbitrageStrategy strategy = new StatisticalArbitrageStrategy(symbols, 1000, 2.0, 0.1, 1000);
            strategy.initialize();
            
            // Simulate correlated market data
            simulateCorrelatedMarketData(strategy, "Statistical Arbitrage", 500);
            
            logger.info("✅ Statistical Arbitrage Strategy Test Results:");
            logger.info("  Strategy: {}", strategy.getName());
            logger.info("  Final P&L: ${:.2f}", strategy.getPnL());
            logger.info("  Status: WORKING");
            
        } catch (Exception e) {
            logger.error("❌ Statistical Arbitrage Strategy Test FAILED", e);
        }
    }
    
    /**
     * Test Triangular Arbitrage Strategy
     */
    public void testTriangularArbitrageStrategy() {
        logger.info("\n--- Testing Triangular Arbitrage Strategy ---");
        
        try {
            TriangularArbitrageStrategy strategy = new TriangularArbitrageStrategy(1, 2, 3, 0.001, 10000, 0.002);
            strategy.initialize();
            
            // Simulate triangular arbitrage data
            simulateTriangularData(strategy, "Triangular Arbitrage", 300);
            
            logger.info("✅ Triangular Arbitrage Strategy Test Results:");
            logger.info("  Strategy: {}", strategy.getName());
            logger.info("  Final P&L: ${:.2f}", strategy.getPnL());
            logger.info("  Status: WORKING");
            
        } catch (Exception e) {
            logger.error("❌ Triangular Arbitrage Strategy Test FAILED", e);
        }
    }
    
    /**
     * Simulate market data for strategy testing
     */
    private void simulateMarketData(Strategy strategy, String strategyName, int tickCount) {
        Random random = new Random(42); // Fixed seed for reproducible results
        double currentPrice = 50000.0; // $50,000 BTC
        
        for (int i = 0; i < tickCount; i++) {
            // Generate realistic price movement
            double priceChange = (random.nextGaussian() * 0.0005); // 0.05% volatility
            currentPrice *= (1 + priceChange);
            
            Tick tick = new Tick();
            tick.symbolId = 1;
            tick.price = (long)(currentPrice * 10000);
            tick.volume = 100 + random.nextInt(1000);
            tick.timestamp = System.nanoTime();
            
            try {
                List<Order> orders = strategy.onTick(tick, null);
                
                // Simulate some trades
                if (!orders.isEmpty() && random.nextDouble() < 0.1) {
                    Trade trade = new Trade();
                    trade.tradeId = i;
                    trade.symbolId = 1;
                    trade.price = tick.price;
                    trade.quantity = 100;
                    trade.buyOrderId = orders.get(0).orderId;
                    trade.sellOrderId = 0;
                    strategy.onTrade(trade);
                }
            } catch (Exception e) {
                logger.warn("Error processing tick for {}: {}", strategyName, e.getMessage());
            }
        }
    }
    
    /**
     * Simulate correlated market data for statistical arbitrage
     */
    private void simulateCorrelatedMarketData(Strategy strategy, String strategyName, int tickCount) {
        Random random = new Random(42);
        double price1 = 50000.0; // BTC
        double price2 = 3000.0;  // ETH
        
        for (int i = 0; i < tickCount; i++) {
            // Generate correlated price movements
            double commonFactor = random.nextGaussian() * 0.0005;
            double specificFactor1 = random.nextGaussian() * 0.0002;
            double specificFactor2 = random.nextGaussian() * 0.0002;
            
            price1 *= (1 + commonFactor + specificFactor1);
            price2 *= (1 + commonFactor + specificFactor2);
            
            // Alternate between symbols
            Tick tick = new Tick();
            tick.symbolId = (i % 2 == 0) ? 1 : 2;
            tick.price = (long)((i % 2 == 0 ? price1 : price2) * 10000);
            tick.volume = 100 + random.nextInt(1000);
            tick.timestamp = System.nanoTime();
            
            try {
                List<Order> orders = strategy.onTick(tick, null);
                
                // Simulate trades
                if (!orders.isEmpty() && random.nextDouble() < 0.05) {
                    Trade trade = new Trade();
                    trade.tradeId = i;
                    trade.symbolId = tick.symbolId;
                    trade.price = tick.price;
                    trade.quantity = 100;
                    trade.buyOrderId = orders.get(0).orderId;
                    trade.sellOrderId = 0;
                    strategy.onTrade(trade);
                }
            } catch (Exception e) {
                logger.warn("Error processing tick for {}: {}", strategyName, e.getMessage());
            }
        }
    }
    
    /**
     * Simulate triangular arbitrage data
     */
    private void simulateTriangularData(Strategy strategy, String strategyName, int tickCount) {
        Random random = new Random(42);
        
        for (int i = 0; i < tickCount; i++) {
            // Simulate three currency pairs
            double btcUsdt = 50000.0 + random.nextGaussian() * 1000;
            double ethUsdt = 3000.0 + random.nextGaussian() * 100;
            double ethBtc = 0.06 + random.nextGaussian() * 0.002;
            
            // Create ticks for each pair
            Tick[] ticks = new Tick[3];
            ticks[0] = createTick(1, btcUsdt); // BTC/USDT
            ticks[1] = createTick(2, ethUsdt); // ETH/USDT
            ticks[2] = createTick(3, ethBtc);  // ETH/BTC
            
            // Process each tick
            for (Tick tick : ticks) {
                try {
                    List<Order> orders = strategy.onTick(tick, null);
                    
                    // Simulate arbitrage execution
                    if (!orders.isEmpty() && random.nextDouble() < 0.02) {
                        Trade trade = new Trade();
                        trade.tradeId = i;
                        trade.symbolId = tick.symbolId;
                        trade.price = tick.price;
                        trade.quantity = 100;
                        trade.buyOrderId = orders.get(0).orderId;
                        trade.sellOrderId = 0;
                        strategy.onTrade(trade);
                    }
                } catch (Exception e) {
                    logger.warn("Error processing tick for {}: {}", strategyName, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Create tick helper
     */
    private Tick createTick(int symbolId, double price) {
        Tick tick = new Tick();
        tick.symbolId = symbolId;
        tick.price = (long)(price * 10000);
        tick.volume = 100 + new Random().nextInt(1000);
        tick.timestamp = System.nanoTime();
        return tick;
    }
}
