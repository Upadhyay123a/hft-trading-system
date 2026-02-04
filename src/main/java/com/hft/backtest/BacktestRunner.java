package com.hft.backtest;

import com.hft.core.SymbolMapper;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.MomentumStrategy;
import com.hft.strategy.Strategy;
import com.hft.utils.SampleDataGenerator;

import java.io.File;
import java.util.Scanner;

/**
 * Main entry point for backtesting
 */
public class BacktestRunner {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║    HFT BACKTESTING SYSTEM                  ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        String dataFile = "data/sample_market_data.csv";
        
        // Check if data file exists, generate if not
        if (!new File(dataFile).exists()) {
            System.out.println("Sample data not found. Generating...\n");
            try {
                SampleDataGenerator.generateData(dataFile, 100000);
            } catch (Exception e) {
                System.err.println("Failed to generate data: " + e.getMessage());
                return;
            }
        }
        
        // Choose strategy
        Scanner scanner = new Scanner(System.in);
        System.out.println("Select Strategy to Backtest:");
        System.out.println("1. Market Making");
        System.out.println("2. Momentum");
        System.out.print("Enter choice (1 or 2): ");
        
        int choice = 1;
        try {
            choice = scanner.nextInt();
        } catch (Exception e) {
            // Default to 1
        }
        
        Strategy strategy;
        if (choice == 2) {
            strategy = new MomentumStrategy(
                SymbolMapper.BTCUSDT,
                20,      // Lookback
                0.05,    // Threshold
                1,       // Order size
                10       // Max position
            );
        } else {
            strategy = new MarketMakingStrategy(
                SymbolMapper.BTCUSDT,
                0.02,    // Spread
                1,       // Order size
                5        // Max position
            );
        }
        
        // Run backtest
        System.out.println("\nRunning backtest...\n");
        BacktestEngine engine = new BacktestEngine(strategy, dataFile, SymbolMapper.BTCUSDT);
        BacktestResult result = engine.run();
        
        // Display results
        if (result != null) {
            result.print();
        } else {
            System.err.println("Backtest failed!");
        }
    }
}