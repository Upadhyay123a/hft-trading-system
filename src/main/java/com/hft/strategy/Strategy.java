package com.hft.strategy;

import java.util.List;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;

/**
 * Base interface for trading strategies
 */
public interface Strategy {
    
    /**
     * Initialize strategy
     */
    void initialize();
    
    /**
     * Process new market tick
     * @return list of orders to place
     */
    List<Order> onTick(Tick tick, OrderBook orderBook);
    
    /**
     * Process trade execution
     */
    void onTrade(Trade trade);
    
    /**
     * Get strategy name
     */
    String getName();
    
    /**
     * Get current P&L
     */
    double getPnL();
    
    /**
     * Reset strategy state
     */
    void reset();

    /**
     * Optional lifecycle shutdown hook for strategies to clean up resources (executors, external clients).
     * Default no-op to avoid forcing all implementations to change.
     */
    default void shutdown() {}
}