package com.hft.backtest;

import java.util.List;

/**
 * Backtest results and metrics
 */
public class BacktestResult {
    public String strategyName;
    public double totalPnL;
    public long ticksProcessed;
    public long tradesExecuted;
    public double maxDrawdown;
    public double sharpeRatio;
    public long duration; // milliseconds
    public List<Double> pnlHistory;
    
    /**
     * Print formatted results
     */
    public void print() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║         BACKTEST RESULTS                   ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("Strategy:           %s\n", strategyName);
        System.out.printf("Duration:           %d ms (%.2f seconds)\n", duration, duration / 1000.0);
        System.out.println("────────────────────────────────────────────");
        System.out.printf("Ticks Processed:    %,d\n", ticksProcessed);
        System.out.printf("Trades Executed:    %,d\n", tradesExecuted);
        System.out.printf("Ticks per Second:   %.2f\n", ticksProcessed * 1000.0 / duration);
        System.out.println("────────────────────────────────────────────");
        System.out.printf("Total P&L:          $%.2f\n", totalPnL);
        System.out.printf("Max Drawdown:       $%.2f\n", maxDrawdown);
        System.out.printf("Sharpe Ratio:       %.3f\n", sharpeRatio);
        System.out.println("────────────────────────────────────────────");
        
        if (pnlHistory != null && pnlHistory.size() > 1) {
            System.out.println("\nP&L Progression (sampled):");
            int step = Math.max(1, pnlHistory.size() / 10);
            for (int i = 0; i < pnlHistory.size(); i += step) {
                System.out.printf("  %5d: $%.2f\n", i, pnlHistory.get(i));
            }
        }
        System.out.println();
    }
}