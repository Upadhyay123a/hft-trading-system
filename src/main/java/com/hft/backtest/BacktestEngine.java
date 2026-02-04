package com.hft.backtest;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import com.hft.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Backtesting engine for strategy evaluation
 */
public class BacktestEngine {
    private static final Logger logger = LoggerFactory.getLogger(BacktestEngine.class);
    
    private final Strategy strategy;
    private final String dataFile;
    private final OrderBook orderBook;
    
    private final List<Double> pnlHistory = new ArrayList<>();
    private final List<Long> positionHistory = new ArrayList<>();
    
    private long ticksProcessed = 0;
    private long tradesExecuted = 0;
    private double maxDrawdown = 0.0;
    private double peakPnL = 0.0;
    
    public BacktestEngine(Strategy strategy, String dataFile, int symbolId) {
        this.strategy = strategy;
        this.dataFile = dataFile;
        this.orderBook = new OrderBook(symbolId);
    }
    
    /**
     * Run backtest
     */
    public BacktestResult run() {
        logger.info("Starting backtest with strategy: {}", strategy.getName());
        logger.info("Data file: {}", dataFile);
        
        strategy.initialize();
        long startTime = System.currentTimeMillis();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            reader.readLine(); // Skip header
            
            while ((line = reader.readLine()) != null) {
                Tick tick = parseTick(line);
                if (tick != null) {
                    processTick(tick);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error running backtest", e);
            return null;
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Calculate metrics
        BacktestResult result = new BacktestResult();
        result.strategyName = strategy.getName();
        result.totalPnL = strategy.getPnL();
        result.ticksProcessed = ticksProcessed;
        result.tradesExecuted = tradesExecuted;
        result.maxDrawdown = maxDrawdown;
        result.duration = duration;
        result.pnlHistory = new ArrayList<>(pnlHistory);
        
        // Calculate Sharpe ratio (simplified)
        if (pnlHistory.size() > 1) {
            result.sharpeRatio = calculateSharpeRatio();
        }
        
        logger.info("Backtest completed in {}ms", duration);
        return result;
    }
    
    /**
     * Process single tick in backtest
     */
    private void processTick(Tick tick) {
        ticksProcessed++;
        
        // Generate orders from strategy
        List<Order> orders = strategy.onTick(tick, orderBook);
        
        // Execute orders
        for (Order order : orders) {
            List<Trade> trades = orderBook.addOrder(order);
            
            for (Trade trade : trades) {
                tradesExecuted++;
                strategy.onTrade(trade);
            }
        }
        
        // Record P&L
        if (ticksProcessed % 100 == 0) {
            double currentPnL = strategy.getPnL();
            pnlHistory.add(currentPnL);
            
            // Track drawdown
            if (currentPnL > peakPnL) {
                peakPnL = currentPnL;
            } else {
                double drawdown = peakPnL - currentPnL;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }
        }
    }
    
    /**
     * Parse tick from CSV line
     * Format: timestamp,symbol,price,volume,side
     */
    private Tick parseTick(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length < 5) return null;
            
            Tick tick = new Tick();
            tick.timestamp = Long.parseLong(parts[0]);
            tick.symbolId = Integer.parseInt(parts[1]);
            tick.setPrice(Double.parseDouble(parts[2]));
            tick.volume = Long.parseLong(parts[3]);
            tick.side = Byte.parseByte(parts[4]);
            
            return tick;
        } catch (Exception e) {
            logger.warn("Failed to parse tick: {}", line);
            return null;
        }
    }
    
    /**
     * Calculate Sharpe ratio
     */
    private double calculateSharpeRatio() {
        if (pnlHistory.size() < 2) return 0.0;
        
        // Calculate returns
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < pnlHistory.size(); i++) {
            double ret = pnlHistory.get(i) - pnlHistory.get(i - 1);
            returns.add(ret);
        }
        
        // Mean return
        double meanReturn = returns.stream().mapToDouble(d -> d).average().orElse(0.0);
        
        // Standard deviation
        double variance = returns.stream()
            .mapToDouble(d -> Math.pow(d - meanReturn, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        if (stdDev == 0) return 0.0;
        
        // Sharpe = mean / stddev (annualized)
        return (meanReturn / stdDev) * Math.sqrt(252); // Assume 252 trading days
    }
}