package com.hft.core;

import com.hft.exchange.BinanceConnector;
import com.hft.execution.ExecutionSimulator;
import com.hft.monitoring.PerformanceMonitor;
import com.hft.orderbook.OrderBook;
import com.ft.risk.RiskManager;
import com.hft.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main trading engine - coordinates market data, order book, and strategies
 */
public class TradingEngine {
    private static final Logger logger = LoggerFactory.getLogger(TradingEngine.class);
    
    private final BinanceConnector connector;
    private final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Strategy strategy;
    private final RiskManager riskManager;
    private final PerformanceMonitor performanceMonitor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private long ticksProcessed = 0;
    private long tradesExecuted = 0;
    private long startTime = 0;
    
    public TradingEngine(BinanceConnector connector, Strategy strategy, RiskManager riskManager) {
        this.connector = connector;
        this.strategy = strategy;
        this.riskManager = riskManager;
        this.performanceMonitor = PerformanceMonitor.getInstance();
    }
    
    /**
     * Start the trading engine
     */
    public void start() {
        logger.info("Starting Trading Engine with strategy: {}", strategy.getName());
        
        running.set(true);
        startTime = System.currentTimeMillis();
        strategy.initialize();
        
        // Main processing loop
        Thread processingThread = new Thread(() -> {
            while (running.get()) {
                try {
                    // Get next tick from exchange
                    Tick tick = connector.getNextTick();
                    if (tick == null) continue;
                    
                    processTick(tick);
                    
                } catch (InterruptedException e) {
                    logger.info("Processing interrupted");
                    break;
                } catch (Exception e) {
                    logger.error("Error processing tick", e);
                }
            }
        }, "TradingEngine-Processor");
        
        processingThread.start();
        
        // Statistics thread
        Thread statsThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(5000); // Print stats every 5 seconds
                    printStatistics();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "TradingEngine-Stats");
        
        statsThread.start();
    }
    
    /**
     * Process a single tick
     */
    private void processTick(Tick tick) {
        try (PerformanceMonitor.LatencyMeasurement measurement = 
             performanceMonitor.startMeasurement("tick_processing")) {
            
            ticksProcessed++;
            performanceMonitor.recordThroughput("ticks", 1);
            
            // Get or create order book for this symbol
            OrderBook orderBook = orderBooks.computeIfAbsent(
                tick.symbolId, 
                id -> new OrderBook(id)
            );
            
            // Let strategy generate orders
            List<Order> orders = strategy.onTick(tick, orderBook);
            performanceMonitor.recordMetric("orders_generated", orders.size());
            
            // Execute orders with risk checks
            for (Order order : orders) {
                // Pre-trade risk validation
                RiskManager.RiskCheckResult riskResult = riskManager.validateOrder(order);
                if (!riskResult.approved) {
                    logger.warn("Order rejected by risk manager: {}", riskResult.reason);
                    performanceMonitor.incrementCounter("orders_rejected_risk");
                    continue;
                }
                
                List<Trade> trades = orderBook.addOrder(order);
                
                // Notify strategy of trades
                for (Trade trade : trades) {
                    tradesExecuted++;
                    strategy.onTrade(trade);
                    riskManager.onTrade(trade); // Update risk metrics
                    performanceMonitor.recordThroughput("trades", 1);
                    
                    if (tradesExecuted % 10 == 0) {
                        logger.debug("Trade: {}", trade);
                    }
                }
            }
            
            // Periodically print order book
            if (ticksProcessed % 1000 == 0) {
                orderBook.printBook(5);
            }
        }
    }
    
    /**
     * Print statistics
     */
    private void printStatistics() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        double ticksPerSecond = ticksProcessed / (double)uptime;
        
        // Get risk metrics
        RiskManager.RiskMetrics riskMetrics = riskManager.getRiskMetrics();
        
        logger.info("=== Trading Engine Statistics ===");
        logger.info("Uptime: {}s", uptime);
        logger.info("Ticks processed: {} ({} tps)", ticksProcessed, String.format("%.2f", ticksPerSecond));
        logger.info("Trades executed: {}", tradesExecuted);
        logger.info("Strategy P&L: ${}", String.format("%.2f", strategy.getPnL()));
        logger.info("Total P&L: ${}", String.format("%.2f", riskMetrics.totalPnL));
        logger.info("Daily P&L: ${}", String.format("%.2f", riskMetrics.dailyPnL));
        logger.info("Drawdown: {}%", String.format("%.2f", riskMetrics.drawdownPercent));
        logger.info("Active positions: {}", riskMetrics.positions.size());
        logger.info("Queue size: {}", connector.getQueueSize());
        if (riskMetrics.emergencyStop) {
            logger.error("EMERGENCY STOP ACTIVE: {}", riskMetrics.emergencyReason);
        }
        logger.info("================================");
    }
    
    /**
     * Stop the engine
     */
    public void stop() {
        logger.info("Stopping Trading Engine");
        running.set(false);
        connector.disconnect();
        printStatistics();
    }
    
    /**
     * Get order book for symbol
     */
    public OrderBook getOrderBook(int symbolId) {
        return orderBooks.get(symbolId);
    }
    
    public long getTicksProcessed() {
        return ticksProcessed;
    }
    
    public long getTradesExecuted() {
        return tradesExecuted;
    }
}