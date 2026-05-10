package com.hft.simulation;

import com.hft.core.Order;
import com.hft.core Tick;
import com.hft.core.integration.UltraHighPerformanceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Market Simulator - generates counter-party orders to fill limit orders
 * Creates realistic market microstructure with random market orders
 */
public class MarketSimulator {
    private static final Logger logger = LoggerFactory.getLogger(MarketSimulator.class);
    
    private final UltraHighPerformanceEngine engine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong orderIdGenerator = new AtomicLong(10000); // Start from 10000 to avoid conflicts
    
    // Simulation parameters
    private final double marketOrderProbability; // Probability of market order per tick
    private final int minMarketOrderSize;
    private final int maxMarketOrderSize;
    private final int[] symbolIds;
    
    // Random generators
    private final Random random = new Random();
    
    public MarketSimulator(UltraHighPerformanceEngine engine, double marketOrderProbability, 
                          int minMarketOrderSize, int maxMarketOrderSize, int[] symbolIds) {
        this.engine = engine;
        this.marketOrderProbability = marketOrderProbability;
        this.minMarketOrderSize = minMarketOrderSize;
        this.maxMarketOrderSize = maxMarketOrderSize;
        this.symbolIds = symbolIds;
    }
    
    /**
     * Start the market simulator thread
     */
    public void start() {
        running.set(true);
        Thread simulatorThread = new Thread(this::simulationLoop, "MarketSimulator");
        simulatorThread.setDaemon(true);
        simulatorThread.start();
        logger.info("Market Simulator started - market order probability: {}%", marketOrderProbability * 100);
    }
    
    /**
     * Stop the market simulator
     */
    public void stop() {
        running.set(false);
        logger.info("Market Simulator stopped");
    }
    
    /**
     * Main simulation loop - generates random market orders
     */
    private void simulationLoop() {
        while (running.get() && engine.isRunning()) {
            try {
                // Sleep for random interval (50-200ms) to simulate market activity
                Thread.sleep(50 + random.nextInt(150));
                
                // Generate market orders with configured probability
                if (random.nextDouble() < marketOrderProbability) {
                    generateMarketOrder();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in market simulation loop", e);
            }
        }
    }
    
    /**
     * Generate a random market order
     */
    private void generateMarketOrder() {
        // Pick random symbol
        int symbolId = symbolIds[random.nextInt(symbolIds.length)];
        
        // Random order size
        int quantity = minMarketOrderSize + random.nextInt(maxMarketOrderSize - minMarketOrderSize + 1);
        
        // Random side (50/50 buy/sell)
        byte side = (byte) (random.nextBoolean() ? 0 : 1); // 0=Buy, 1=Sell
        
        // Market orders use price=0 to indicate market price
        long price = 0;
        
        // Create market order
        Order marketOrder = new Order(
            orderIdGenerator.getAndIncrement(),
            symbolId,
            price, // Market orders have price=0
            quantity,
            side,
            (byte) 1 // Market order type
        );
        
        // Set timestamp
        marketOrder.timestamp = System.nanoTime();
        
        // Submit to engine for processing
        engine.processExternalOrderUpdate(marketOrder);
        
        logger.debug("MarketSimulator: Generated {} market order - id={}, symbol={}, qty={}",
                    side == 0 ? "BUY" : "SELL", marketOrder.orderId, symbolId, quantity);
    }
    
    /**
     * Generate a market order in response to a tick (for more realistic simulation)
     */
    public void onTick(Tick tick) {
        if (!running.get()) return;
        
        // 10% chance to generate a market order based on tick activity
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            // Generate market order that's likely to interact with current spread
            int quantity = minMarketOrderSize + random.nextInt(maxMarketOrderSize - minMarketOrderSize + 1);
            
            // Bias towards the side that would interact with the tick
            byte side = (tick.side == 0) ? (byte) 1 : (byte) 0; // Opposite of tick side
            
            Order marketOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                tick.symbolId,
                0, // Market price
                quantity,
                side,
                (byte) 1 // Market order
            );
            
            marketOrder.timestamp = tick.timestamp;
            engine.processExternalOrderUpdate(marketOrder);
            
            logger.debug("MarketSimulator: Tick-driven {} market order - id={}, symbol={}, qty={}",
                        side == 0 ? "BUY" : "SELL", marketOrder.orderId, tick.symbolId, quantity);
        }
    }
    
    /**
     * Get simulator statistics
     */
    public long getOrdersGenerated() {
        return orderIdGenerator.get() - 10000; // Subtract starting value
    }
}
