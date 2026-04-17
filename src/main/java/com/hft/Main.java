package com.hft;
 
import com.hft.core.SymbolMapper;
import com.hft.core.Tick;
import com.hft.core.integration.UltraHighPerformanceEngine;
import com.hft.exchange.BinanceConnector;
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
 
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
 
    public static void main(String[] args) {
        logger.info("=== HFT Trading System ===");
        logger.info("Starting up...");
 
        final UltraHighPerformanceEngine[] engineRef = new UltraHighPerformanceEngine[1];
 
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("SAFETY: Shutdown hook activated - forcing cleanup...");
            try {
                Thread.currentThread().interrupt();
 
                if (engineRef[0] != null && engineRef[0].isRunning()) {
                    logger.warn("SAFETY: Forcing engine shutdown via hook");
                    engineRef[0].stop();
                }
 
                Thread.sleep(2000);
 
            } catch (InterruptedException e) {
                logger.info("Shutdown hook interrupted");
            } catch (Exception e) {
                logger.error("Error in shutdown hook", e);
            }
        }, "Safety-Shutdown-Hook"));
 
        List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT");
 
        logger.info("Connecting to Binance...");
        BinanceConnector connector = new BinanceConnector(symbols);
        connector.connect();
 
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
            logger.error("Failed to connect to Binance");
            return;
        }
 
        logger.info("Connected successfully!");
 
        Strategy strategy = chooseStrategy();
 
        RiskManager.RiskConfig riskConfig = RiskManager.RiskConfig.moderate();
        RiskManager riskManager = new RiskManager(riskConfig);
 
        logger.info("Starting Ultra-High Performance Trading Engine...");
        logger.info("Binary Encoding + LMAX Disruptor + Aeron + FIX Protocol");
 
        UltraHighPerformanceEngine engine = new UltraHighPerformanceEngine(strategy, riskManager);
        engineRef[0] = engine;
 
        engine.start();
 
        logger.info("Connecting Binance data stream to engine...");
        Thread dataThread = new Thread(() -> {
            try {
                while (engine.isRunning()) {
                    Tick tick = connector.getNextTick();
                    if (tick != null) {
                        engine.processTick(tick.timestamp, tick.symbolId, 
                                          tick.price, tick.volume, tick.side);
                    }
                }
            } catch (InterruptedException e) {
                logger.info("Data thread interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error in data thread", e);
            }
        }, "Binance-Data-Thread");
        dataThread.setDaemon(true);
        dataThread.start();
 
        logger.info("\n=== TRADING SYSTEM RUNNING ===");
        logger.info("System is processing live market data from Binance");
        logger.info("=====================================\n");
 
        try {
            logger.info("System started. Press Ctrl+C to stop...");
            logger.info("Automatic safety shutdown in 5 minutes (300 seconds)...");
 
            long startTime = System.currentTimeMillis();
            long maxRunTime = 300000; // 5 minutes for more trading opportunities
            long lastStatusTime = startTime;
 
            while (engine.isRunning() && (System.currentTimeMillis() - startTime) < maxRunTime) {
                Thread.sleep(1000);
 
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastStatusTime > 10000) {
                    long remainingTime = maxRunTime - (currentTime - startTime);
                    logger.info("System running... {} seconds remaining until auto-shutdown", 
                               remainingTime / 1000);
                    lastStatusTime = currentTime;
                }
            }
 
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
 
    private static Strategy chooseStrategy() {
        try (Scanner scanner = new Scanner(System.in)) {
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
 
            switch (choice) {
                case 2:
                    logger.info("Creating Momentum Strategy");
                    return new MomentumStrategy(
                        SymbolMapper.BTCUSDT, 20, 0.05, 1, 10
                    );
 
                case 3:
                    logger.info("Creating Triangular Arbitrage Strategy");
                    return new TriangularArbitrageStrategy(
                        SymbolMapper.BTCUSDT, SymbolMapper.ETHUSDT, 2, 0.001, 1000, 0.005
                    );
 
                case 4:
                    logger.info("Creating Statistical Arbitrage Strategy");
                    int[] pairs = {SymbolMapper.BTCUSDT, SymbolMapper.ETHUSDT};
                    return new StatisticalArbitrageStrategy(
                        pairs, 30, 1.2, 0.0005, 1
                    );
 
                case 5:
                    logger.info("Creating AI-Enhanced Strategy");
                    return new AIEnhancedStrategy(
                        SymbolMapper.BTCUSDT, 1, 10
                    );
 
                default:
                    logger.info("Creating Market Making Strategy");
                    return new MarketMakingStrategy(
                        SymbolMapper.BTCUSDT, 0.02, 1, 5
                    );
            }
        }
    }
}