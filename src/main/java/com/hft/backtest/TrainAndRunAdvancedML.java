package com.hft.backtest;

import com.hft.core.SymbolMapper;
import com.hft.strategy.AdvancedMLStrategy;

public class TrainAndRunAdvancedML {
    public static void main(String[] args) throws Exception {
        String dataFile = "data/sample_market_data.csv";
        java.io.File f = new java.io.File(dataFile);
        if (!f.exists()) {
            System.out.println("Sample data missing — generating...");
            com.hft.utils.SampleDataGenerator.generateData(dataFile, 100000);
        }

        AdvancedMLStrategy adv = new AdvancedMLStrategy(SymbolMapper.BTCUSDT, 0.02, 100, 1000);

        System.out.println("Bootstrapping training for Advanced ML models...");
        try {
            adv.trainModels();
            System.out.println("Training completed (or attempted).");
        } catch (Exception e) {
            System.err.println("Training failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Running Advanced ML backtest (post-training)...");
        try {
            BacktestResult r = new BacktestEngine(adv, dataFile, SymbolMapper.BTCUSDT).run();
            System.out.println("Backtest finished: duration=" + r.duration + ", ticks=" + r.ticksProcessed + ", trades=" + r.tradesExecuted + ", pnl=" + r.totalPnL);
        } catch (Exception e) {
            System.err.println("Advanced ML backtest failed after training: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
