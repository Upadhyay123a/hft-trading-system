package com.hft;

import com.hft.core.integration.UltraHighPerformanceEngine;
import com.hft.strategy.MarketMakingStrategy;
import com.ft.risk.RiskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Ultra-High Performance HFT Trading System
 * Demonstrates binary encoding + LMAX Disruptor + Aeron + FIX integration
 */
public class MainUltraHighPerformance {
    private static final Logger logger = LoggerFactory.getLogger(MainUltraHighPerformance.class);
    
    public static void main(String[] args) {
        logger.info("=== Ultra-High Performance HFT Trading System ===");
        logger.info("Binary Encoding + LMAX Disruptor + Aeron + FIX Protocol");
        logger.info("==================================================");
        
        try {
            // Create strategy and risk manager
            MarketMakingStrategy strategy = new MarketMakingStrategy(1, 0.02, 1, 5);
            RiskManager.RiskConfig riskConfig = new RiskManager.RiskConfig(
                10,        // maxPositionSize
                0.10,      // maxDrawdownPercent (10%)
                0.05,      // stopLossPercent (5%)
                50000.0,   // maxDailyLoss ($50,000)
                50         // maxOrdersPerSecond
            );
            RiskManager riskManager = new RiskManager(riskConfig);
            
            // Create ultra-high performance engine
            UltraHighPerformanceEngine engine = new UltraHighPerformanceEngine(strategy, riskManager);
            
            // Start the engine
            engine.start();
            
            // Simulate market data
            simulateMarketData(engine);
            
            // Keep running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered");
                engine.stop();
            }));
            
            // Wait for user input to stop
            System.out.println("\nPress ENTER to stop the engine...");
            System.in.read();
            
            // Stop the engine
            engine.stop();
            
        } catch (Exception e) {
            logger.error("Error in ultra-high performance engine", e);
        }
        
        logger.info("Ultra-High Performance HFT Trading System stopped");
    }
    
    /**
     * Simulate market data for testing
     */
    private static void simulateMarketData(UltraHighPerformanceEngine engine) {
        Thread dataSimulator = new Thread(() -> {
            long tickCount = 0;
            long startTime = System.currentTimeMillis();
            
            while (engine.isRunning()) {
                try {
                    // Simulate tick data
                    long timestamp = System.nanoTime();
                    int symbolId = 1; // BTC/USDT
                    long price = 500000000L + (long)(Math.random() * 1000000L); // $50000 +/- $0.10
                    long volume = 1000000L + (long)(Math.random() * 500000L); // Base volume
                    byte side = (byte)(Math.random() < 0.5 ? 0 : 1); // Buy/Sell
                    
                    // Process tick
                    engine.processTick(timestamp, symbolId, price, volume, side);
                    
                    tickCount++;
                    
                    // Print progress every 1000 ticks
                    if (tickCount % 1000 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double tps = tickCount / (elapsed / 1000.0);
                        logger.info("Simulated {} ticks ({} tps)", tickCount, String.format("%.0f", tps));
                    }
                    
                    // Control simulation speed
                    Thread.sleep(1); // 1ms between ticks (1000 tps max)
                    
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    logger.error("Error simulating market data", e);
                }
            }
            
            logger.info("Market data simulation stopped. Total ticks: {}", tickCount);
        });
        
        dataSimulator.setDaemon(true);
        dataSimulator.start();
        
        logger.info("Market data simulation started");
    }
}
