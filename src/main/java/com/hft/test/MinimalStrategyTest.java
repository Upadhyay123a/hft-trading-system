package com.hft.test;

import com.hft.core.*;
import com.hft.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * Minimal Strategy Test - No external dependencies
 */
public class MinimalStrategyTest {
    private static final Logger logger = LoggerFactory.getLogger(MinimalStrategyTest.class);
    
    public static void main(String[] args) {
        logger.info("=== MINIMAL STRATEGY TEST - NO ENGINE ===");
        
        try {
            // Test Market Making Strategy only
            MarketMakingStrategy strategy = new MarketMakingStrategy(1, 0.02, 1000, 10);
            strategy.initialize();
            
            // Simulate market data
            simulateMarketData(strategy, 1000);
            
            logger.info("✅ Market Making Strategy Results:");
            logger.info("  Strategy: {}", strategy.getName());
            logger.info("  Final P&L: ${}", String.format("%.2f", strategy.getPnL()));
            logger.info("  Status: WORKING - No WebSocket errors!");
            
        } catch (Exception e) {
            logger.error("❌ Test FAILED", e);
        }
        
        logger.info("=== MINIMAL TEST COMPLETED ===");
    }
    
    private static void simulateMarketData(MarketMakingStrategy strategy, int tickCount) {
        Random random = new Random(42);
        double currentPrice = 50000.0;
        
        for (int i = 0; i < tickCount; i++) {
            double priceChange = (random.nextGaussian() * 0.0005);
            currentPrice *= (1 + priceChange);
            
            Tick tick = new Tick();
            tick.symbolId = 1;
            tick.price = (long)(currentPrice * 10000);
            tick.volume = 100 + random.nextInt(1000);
            tick.timestamp = System.nanoTime();
            
            List<Order> orders = strategy.onTick(tick, null);
            
            // Simulate trades (10% probability)
            if (!orders.isEmpty() && random.nextDouble() < 0.1) {
                Trade trade = new Trade();
                trade.tradeId = i;
                trade.symbolId = 1;
                trade.price = tick.price;
                trade.quantity = 100;
                trade.buyOrderId = orders.get(0).orderId;
                trade.sellOrderId = 0;
                strategy.onTrade(trade);
                
                if (i % 100 == 0) {
                    logger.info("Trade {} executed - P&L: ${}", i, String.format("%.2f", strategy.getPnL()));
                }
            }
        }
    }
}
