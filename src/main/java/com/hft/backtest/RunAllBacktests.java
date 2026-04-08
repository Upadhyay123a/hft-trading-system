package com.hft.backtest;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.hft.core.SymbolMapper;
import com.hft.strategy.AIEnhancedStrategy;
import com.hft.strategy.AdvancedMLStrategy;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.MomentumStrategy;
import com.hft.strategy.StatisticalArbitrageStrategy;
import com.hft.strategy.Strategy;
import com.hft.strategy.TriangularArbitrageStrategy;

/**
 * Utility to run backtests for all available strategies and save a summary.
 */
public class RunAllBacktests {

    public static void main(String[] args) throws Exception {
        String dataFile = "data/sample_market_data.csv";

        // Ensure sample data exists; generate if missing
        java.io.File f = new java.io.File(dataFile);
        if (!f.exists()) {
            System.out.println("Sample data missing — generating...");
            com.hft.utils.SampleDataGenerator.generateData(dataFile, 100000);
        }

        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("strategy,duration_ms,ticks,trades,total_pnl,ticks_per_sec");

        // Market Making
        Strategy mm = new MarketMakingStrategy(SymbolMapper.BTCUSDT, 0.02, 1, 5);
        BacktestResult r1 = new BacktestEngine(mm, dataFile, SymbolMapper.BTCUSDT).run();
        summaryLines.add(String.format("MarketMaking,%d,%d,%d,%.2f,%.2f",
            r1.duration, r1.ticksProcessed, r1.tradesExecuted, r1.totalPnL,
            r1.ticksProcessed / (Math.max(1, r1.duration) / 1000.0)));

        // Momentum
        Strategy mom = new MomentumStrategy(SymbolMapper.BTCUSDT, 20, 0.05, 1, 10);
        BacktestResult r2 = new BacktestEngine(mom, dataFile, SymbolMapper.BTCUSDT).run();
        summaryLines.add(String.format("Momentum,%d,%d,%d,%.2f,%.2f",
            r2.duration, r2.ticksProcessed, r2.tradesExecuted, r2.totalPnL,
            r2.ticksProcessed / (Math.max(1, r2.duration) / 1000.0)));

        // Triangular Arbitrage
        int ethBtc = com.hft.core.SymbolMapper.getId("ETHBTC");
        Strategy tri = new TriangularArbitrageStrategy(com.hft.core.SymbolMapper.BTCUSDT, com.hft.core.SymbolMapper.ETHUSDT, ethBtc, 0.001, 1000, 0.005);
        BacktestResult r3 = new BacktestEngine(tri, dataFile, SymbolMapper.BTCUSDT).run();
        summaryLines.add(String.format("TriangularArbitrage,%d,%d,%d,%.2f,%.2f",
            r3.duration, r3.ticksProcessed, r3.tradesExecuted, r3.totalPnL,
            r3.ticksProcessed / (Math.max(1, r3.duration) / 1000.0)));

        // Statistical Arbitrage (pairs trading example)
        int[] statSymbols = new int[] { com.hft.core.SymbolMapper.BTCUSDT, com.hft.core.SymbolMapper.ETHUSDT };
        Strategy stat = new StatisticalArbitrageStrategy(statSymbols, 50, 2.0, 0.001, 100);
        BacktestResult r4 = new BacktestEngine(stat, dataFile, SymbolMapper.BTCUSDT).run();
        summaryLines.add(String.format("StatisticalArbitrage,%d,%d,%d,%.2f,%.2f",
            r4.duration, r4.ticksProcessed, r4.tradesExecuted, r4.totalPnL,
            r4.ticksProcessed / (Math.max(1, r4.duration) / 1000.0)));

        // AI Enhanced (uses mock AI when keys missing)
        Strategy ai = new AIEnhancedStrategy(SymbolMapper.BTCUSDT, 1, 10);
        BacktestResult r5 = new BacktestEngine(ai, dataFile, SymbolMapper.BTCUSDT).run();
        summaryLines.add(String.format("AIEnhanced,%d,%d,%d,%.2f,%.2f",
            r5.duration, r5.ticksProcessed, r5.tradesExecuted, r5.totalPnL,
            r5.ticksProcessed / (Math.max(1, r5.duration) / 1000.0)));

        // Advanced ML Strategy (will run inference; training skipped to keep runtime small)
        AdvancedMLStrategy adv = new AdvancedMLStrategy(SymbolMapper.BTCUSDT, 0.02, 100, 1000);
        BacktestResult r6 = new BacktestEngine(adv, dataFile, SymbolMapper.BTCUSDT).run();
        summaryLines.add(String.format("AdvancedML,%d,%d,%d,%.2f,%.2f",
            r6.duration, r6.ticksProcessed, r6.tradesExecuted, r6.totalPnL,
            r6.ticksProcessed / (Math.max(1, r6.duration) / 1000.0)));

        // Save summary
        try {
            java.io.File outDir = new java.io.File("logs");
            if (!outDir.exists()) outDir.mkdirs();
            java.io.File out = new java.io.File(outDir, "all_backtests_summary.csv");
            try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
                for (String line : summaryLines) pw.println(line);
            }
            System.out.println("All backtests completed. Summary saved to " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to write summary: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
