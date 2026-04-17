package com.hft;

import com.hft.core.SymbolMapper;
import com.hft.core.Tick;
import com.hft.core.integration.UltraHighPerformanceEngine;
import com.hft.exchange.BinanceConnector;
import com.hft.exchange.CsvDataConnector;
import com.ft.risk.RiskManager;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.MomentumStrategy;
import com.hft.strategy.StatisticalArbitrageStrategy;
import com.hft.strategy.TriangularArbitrageStrategy;
import com.hft.strategy.AIEnhancedStrategy;
import com.hft.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Main application entry point
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("=== HFT Trading System ===");
        logger.info("Starting up...");
        
        // SAFETY: Engine reference for shutdown hook
        final UltraHighPerformanceEngine[] engineRef = new UltraHighPerformanceEngine[1];
        
        // SAFETY: Add shutdown hook with proper thread interruption
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("SAFETY: Shutdown hook activated - forcing cleanup...");
            try {
                // Interrupt main thread if it's stuck
                Thread.currentThread().interrupt();
                
                // Force engine stop if running
                if (engineRef[0] != null && engineRef[0].isRunning()) {
                    logger.warn("SAFETY: Forcing engine shutdown via hook");
                    engineRef[0].stop();
                }
                
                // Give some time for cleanup
                Thread.sleep(2000);
                
            } catch (InterruptedException e) {
                logger.info("Shutdown hook interrupted");
            } catch (Exception e) {
                logger.error("Error in shutdown hook", e);
            }
        }, "Safety-Shutdown-Hook"));
        
        // Use CSV data for testing (more reliable than live Binance)
        logger.info("Using CSV data for testing...");
        CsvDataConnector connector = new CsvDataConnector("data/binance_BTCUSDT_1m_proper.csv");
        connector.connect();
        
        // Wait for connection
        int attempts = 0;
        while (!connector.isConnected() && attempts < 20) {
            try {
                Thread.sleep(500);
                attempts++;
            } catch (InterruptedException e) {
                break;
            }
        }
        
        if (!connector.isConnected()) {
            logger.error("Failed to connect to CSV data source");
            return;
        }
        
        logger.info("Connected successfully!");
        
        // Choose strategy
        Strategy strategy = chooseStrategy();
        
        // Choose engine type
        // Configure risk management
        RiskManager.RiskConfig riskConfig = RiskManager.RiskConfig.moderate();
        RiskManager riskManager = new RiskManager(riskConfig);
        
        // Create and start trading engine
        logger.info("Starting Ultra-High Performance Trading Engine...");
        logger.info("Binary Encoding + LMAX Disruptor + Aeron + FIX Protocol");
        
        UltraHighPerformanceEngine engine = new UltraHighPerformanceEngine(strategy, riskManager);
        
        // SAFETY: Store engine reference for shutdown hook
        engineRef[0] = engine;
        
        // Start the engine
        engine.start();
        
        // CRITICAL: Connect CSV data to the engine
        logger.info("Connecting CSV data stream to engine...");
        Thread dataThread = new Thread(() -> {
            try {
                while (engine.isRunning()) {
                    Tick tick = connector.getNextTick(); // Get tick from CSV
                    if (tick != null) {
                        // Send tick to engine for processing
                        engine.processTick(tick.timestamp, tick.symbolId, 
                                          tick.getPrice(), tick.volume, tick.side);
                    }
                }
            } catch (InterruptedException e) {
                logger.info("Data thread interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error in data thread", e);
            }
        }, "CSV-Data-Thread");
        dataThread.setDaemon(true);
        dataThread.start();
        
        // Wait for user input to stop
        logger.info("\n=== TRADING SYSTEM RUNNING ===");
        logger.info("System is processing live market data from CSV");
        logger.info("=====================================\n");
        
        Scanner scanner = new Scanner(System.in);
        
        // Check for user input with timeout and proper shutdown
        try {
            logger.info("System started. Press Ctrl+C to stop...");
            logger.info("Automatic safety shutdown in 60 seconds...");
            
            // SAFETY: Add automatic timeout to prevent infinite running
            long startTime = System.currentTimeMillis();
            long maxRunTime = 60000; // 1 minute max for safety
            long lastStatusTime = startTime;
            
            while (engine.isRunning() && (System.currentTimeMillis() - startTime) < maxRunTime) {
                Thread.sleep(1000); // Check every 1 second
                
                // SAFETY: Print status every 10 seconds to show system is responsive
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastStatusTime > 10000) {
                    long remainingTime = maxRunTime - (currentTime - startTime);
                    logger.info("System running... {} seconds remaining until auto-shutdown", 
                               remainingTime / 1000);
                    lastStatusTime = currentTime;
                }
            }
            
            // SAFETY: Force stop if timeout reached
            if (engine.isRunning()) {
                logger.warn("SAFETY TIMEOUT: Forcing shutdown after {} seconds", maxRunTime / 1000);
                engine.stop();
            }
            
        } catch (InterruptedException e) {
            logger.info("Interrupted by user, shutting down...");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error in main loop, forcing shutdown", e);
        }
        
        // Shutdown
        logger.info("\n=== SHUTTING DOWN ===");
        logger.info("Stopping ultra-high performance engine...");
        engine.stop();
        
        logger.info("=== Final Statistics ===");
        logger.info("Strategy: {}", strategy.getName());
        logger.info("Total P&L: ${}", String.format("%.2f", strategy.getPnL()));
        logger.info("Ticks Processed: {}", engine.getTicksProcessed());
        logger.info("Orders Processed: {}", engine.getOrdersProcessed());
        logger.info("Trades Executed: {}", engine.getTradesExecuted());
        logger.info("Messages via Aeron: {}", engine.getMessagesProcessed());
        logger.info("FIX Messages: {}", engine.getFixMessagesProcessed());
        logger.info("========================");
        logger.info("Shutdown complete. Goodbye!");
    }
    
    private static int chooseEngine() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\nChoose Trading Engine:");
        System.out.println("1. Standard Engine (single-threaded, simulation only)");
        System.out.println("2. High-Throughput Engine (multi-threaded, simulation only)");
        System.out.println("3. Real Trading Engine (actual exchange trading - requires API keys)");
        System.out.print("Enter choice (1-3): ");
        
        int choice = 1;
        try {
            choice = scanner.nextInt();
        } catch (Exception e) {
            // Default to standard engine
        }
        
        return choice;
    }
    
    private static Strategy chooseStrategy() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\nChoose Trading Strategy:");
        System.out.println("1. Market Making (provides liquidity, captures spread)");
        System.out.println("2. Momentum (follows price trends)");
        System.out.println("3. Triangular Arbitrage (exploits cross-currency inefficiencies)");
        System.out.println("4. Statistical Arbitrage (mean reversion, pairs trading)");
        System.out.println("5. AI-Enhanced (Gemini/Perplexity AI-powered trading)");
        System.out.print("Enter choice (1-5): ");
        
        int choice = 1;
        try {
            choice = scanner.nextInt();
        } catch (Exception e) {
            // Default to market making
        }
        
        if (choice == 2) {
            // Momentum strategy
            logger.info("Creating Momentum Strategy");
            return new MomentumStrategy(
                SymbolMapper.BTCUSDT,  // Trade BTC
                20,                     // Look back 20 ticks
                0.05,                   // 0.05% threshold
                1,                      // Order size (BTC units)
                10                      // Max position
            );
        } else if (choice == 3) {
            // Triangular arbitrage strategy
            logger.info("Creating Triangular Arbitrage Strategy");
            return new TriangularArbitrageStrategy(
                SymbolMapper.BTCUSDT,  // BTC/USDT (base pair)
                SymbolMapper.ETHUSDT,  // ETH/USDT (quote pair)
                2,                     // ETH/BTC (cross pair - need to register)
                0.001,                 // 0.1% minimum profit
                1000,                  // $1000 order size
                0.005                  // 0.5% max slippage
            );
        } else if (choice == 4) {
            // Statistical arbitrage strategy
            logger.info("Creating Statistical Arbitrage Strategy");
            int[] pairs = {SymbolMapper.BTCUSDT, SymbolMapper.ETHUSDT};
            return new StatisticalArbitrageStrategy(
                pairs,                  // BTC/USDT and ETH/USDT pair
                100,                   // 100 tick lookback period
                2.0,                   // 2.0 Z-score threshold
                0.001,                 // 0.1% minimum spread
                1                      // 1 unit order size
            );
        } else if (choice == 5) {
            // AI-Enhanced strategy
            logger.info("Creating AI-Enhanced Strategy");
            return new AIEnhancedStrategy(
                SymbolMapper.BTCUSDT,  // Trade BTC
                1,                      // Base order size
                10                      // Max position
            );
        } else {
            // Market making strategy (default)
            logger.info("Creating Market Making Strategy");
            return new MarketMakingStrategy(
                SymbolMapper.BTCUSDT,  // Trade BTC
                0.02,                   // 0.02% spread
                1,                      // Order size
                5                       // Max position
            );
        }
    }
}