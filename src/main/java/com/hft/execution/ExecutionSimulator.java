package com.hft.execution;

import com.hft.core.Order;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Execution Simulator for backtesting and paper trading
 * Simulates order execution with realistic market behavior
 */
public class ExecutionSimulator {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionSimulator.class);
    
    private final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private final AtomicLong tradeIdGenerator = new AtomicLong(1);
    private final Random random = new Random();
    
    // Simulation parameters
    private final double slippageBps;
    private final double fillProbability;
    private final boolean enablePartialFills;
    
    public ExecutionSimulator() {
        this(5.0, 0.95, true);
    }
    
    public ExecutionSimulator(double slippageBps, double fillProbability, boolean enablePartialFills) {
        this.slippageBps = slippageBps;
        this.fillProbability = fillProbability;
        this.enablePartialFills = enablePartialFills;
        logger.info("Execution Simulator initialized - Slippage: {} bps, Fill rate: {}%", 
            slippageBps, fillProbability * 100);
    }
    
    /**
     * Simulate order execution
     */
    public List<Trade> executeOrder(Order order, OrderBook orderBook) {
        List<Trade> trades = new ArrayList<>();
        
        try {
            // Check if order should be filled based on probability
            if (random.nextDouble() > fillProbability) {
                logger.debug("Order {} rejected by simulation", order.orderId);
                return trades;
            }
            
            // Calculate execution price with slippage
            double executionPrice = calculateExecutionPrice(order, orderBook);
            
            // Determine fill quantity
            long fillQuantity = calculateFillQuantity(order);
            
            if (fillQuantity > 0) {
                Trade trade = new Trade();
                trade.tradeId = tradeIdGenerator.getAndIncrement();
                trade.symbolId = order.symbolId;
                trade.price = (long)(executionPrice * 10000); // Convert to price*10000 format
                trade.quantity = (int)fillQuantity;
                trade.timestamp = System.nanoTime();
                trade.buyOrderId = order.side == 0 ? order.orderId : -1;
                trade.sellOrderId = order.side == 1 ? order.orderId : -1;
                
                trades.add(trade);
                
                logger.debug("Simulated execution: Order {} -> Trade {} @ {} for {} units", 
                    order.orderId, trade.tradeId, executionPrice, fillQuantity);
            }
            
        } catch (Exception e) {
            logger.error("Error simulating order execution", e);
        }
        
        return trades;
    }
    
    /**
     * Calculate execution price with slippage
     */
    private double calculateExecutionPrice(Order order, OrderBook orderBook) {
        double basePrice = orderBook.getMidPrice();
        
        if (order.side == 0) { // BUY
            // Buy orders experience positive slippage (higher price)
            double slippageAmount = basePrice * (slippageBps / 10000.0);
            return basePrice + slippageAmount + (random.nextGaussian() * slippageAmount * 0.1);
        } else {
            // Sell orders experience negative slippage (lower price)
            double slippageAmount = basePrice * (slippageBps / 10000.0);
            return basePrice - slippageAmount + (random.nextGaussian() * slippageAmount * 0.1);
        }
    }
    
    /**
     * Calculate fill quantity (supports partial fills)
     */
    private long calculateFillQuantity(Order order) {
        if (!enablePartialFills) {
            return order.quantity;
        }
        
        // Simulate partial fill probability
        double fillRatio = 0.5 + (random.nextDouble() * 0.5); // 50-100% fill
        return (long) (order.quantity * fillRatio);
    }
    
    /**
     * Get simulation statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("orders_simulated", orderIdGenerator.get() - 1);
        stats.put("trades_generated", tradeIdGenerator.get() - 1);
        stats.put("slippage_bps", slippageBps);
        stats.put("fill_probability", fillProbability);
        return stats;
    }
    
    /**
     * Reset simulator state
     */
    public void reset() {
        orderBooks.clear();
        orderIdGenerator.set(1);
        tradeIdGenerator.set(1);
        logger.info("Execution Simulator reset");
    }
}
