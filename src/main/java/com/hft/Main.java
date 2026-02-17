package com.hft;

import com.hft.core.SymbolMapper;
import com.hft.core.TradingEngine;
import com.hft.core.HighThroughputEngine;
import com.hft.exchange.BinanceConnector;
import com.hft.risk.RiskManager;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.MomentumStrategy;
import com.hft.strategy.StatisticalArbitrageStrategy;
import com.hft.strategy.TriangularArbitrageStrategy;
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
        
        // Configure symbols to trade
        List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT");
        
        // Create exchange connector
        logger.info("Connecting to Binance...");
        BinanceConnector connector = new BinanceConnector(symbols);
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
            logger.error("Failed to connect to Binance");
            return;
        }
        
        logger.info("Connected successfully!");
        
        // Choose strategy
        Strategy strategy = chooseStrategy();
        
        // Choose engine type
        boolean useHighThroughput = chooseEngineType();
        
        // Configure risk management
        RiskManager.RiskConfig riskConfig = RiskManager.RiskConfig.moderate();
        RiskManager riskManager = new RiskManager(riskConfig);
        
        // Create and start trading engine
        if (useHighThroughput) {
            HighThroughputEngine.EngineConfig engineConfig = HighThroughputEngine.EngineConfig.highThroughput();
            HighThroughputEngine engine = new HighThroughputEngine(connector, strategy, riskManager, engineConfig);
            
            logger.info("Starting high-throughput trading engine...");
            engine.start();
            
            // Wait for user input to stop
            logger.info("\nPress ENTER to stop trading...\n");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            
            // Shutdown
            logger.info("Shutting down...");
            engine.stop();
            
            logger.info("=== Final Statistics ===");
            logger.info("Strategy: {}", strategy.getName());
            logger.info("Total P&L: ${}", String.format("%.2f", strategy.getPnL()));
            logger.info("Ticks Processed: {}", engine.getTicksProcessed());
            logger.info("Trades Executed: {}", engine.getTradesExecuted());
            logger.info("Orders Submitted: {}", engine.getOrdersSubmitted());
            logger.info("Orders Rejected: {}", engine.getOrdersRejected());
        } else {
            TradingEngine engine = new TradingEngine(connector, strategy, riskManager);
            
            logger.info("Starting standard trading engine...");
            engine.start();
            
            // Wait for user input to stop
            logger.info("\nPress ENTER to stop trading...\n");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            
            // Shutdown
            logger.info("Shutting down...");
            engine.stop();
            
            logger.info("=== Final Statistics ===");
            logger.info("Strategy: {}", strategy.getName());
            logger.info("Total P&L: ${}", String.format("%.2f", strategy.getPnL()));
            logger.info("Ticks Processed: {}", engine.getTicksProcessed());
            logger.info("Trades Executed: {}", engine.getTradesExecuted());
        }
        logger.info("========================");
        logger.info("Shutdown complete. Goodbye!");
    }
    
    private static boolean chooseEngineType() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\nChoose Trading Engine:");
        System.out.println("1. Standard Engine (single-threaded, low resource usage)");
        System.out.println("2. High-Throughput Engine (multi-threaded, maximum performance)");
        System.out.print("Enter choice (1-2): ");
        
        int choice = 1;
        try {
            choice = scanner.nextInt();
        } catch (Exception e) {
            // Default to standard engine
        }
        
        return choice == 2;
    }
    
    private static Strategy chooseStrategy() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\nChoose Trading Strategy:");
        System.out.println("1. Market Making (provides liquidity, captures spread)");
        System.out.println("2. Momentum (follows price trends)");
        System.out.println("3. Triangular Arbitrage (exploits cross-currency inefficiencies)");
        System.out.println("4. Statistical Arbitrage (mean reversion, pairs trading)");
        System.out.print("Enter choice (1-4): ");
        
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