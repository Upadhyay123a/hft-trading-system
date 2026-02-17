package com.hft.strategy;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Triangular Arbitrage Strategy for HFT
 * Detects and exploits price inefficiencies across three currency pairs
 * Example: BTC/USDT, ETH/USDT, ETH/BTC
 */
public class TriangularArbitrageStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(TriangularArbitrageStrategy.class);
    
    // Trading pairs for triangular arbitrage
    private final int basePair;      // BTC/USDT
    private final int quotePair;     // ETH/USDT  
    private final int crossPair;     // ETH/BTC
    
    // Strategy parameters
    private final double minProfitThreshold; // Minimum profit percentage
    private final int orderSize;              // Order size in base currency
    private final double maxSlippage;         // Maximum acceptable slippage
    
    // Market data storage
    private final ConcurrentHashMap<Integer, Double> lastPrices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> lastUpdateTime = new ConcurrentHashMap<>();
    
    // Performance tracking
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private double totalPnL = 0.0;
    private long opportunitiesDetected = 0;
    private long tradesExecuted = 0;
    
    // Active arbitrage positions
    private volatile ArbitragePosition activePosition = null;
    
    public TriangularArbitrageStrategy(int basePair, int quotePair, int crossPair,
                                     double minProfitThreshold, int orderSize, double maxSlippage) {
        this.basePair = basePair;
        this.quotePair = quotePair;
        this.crossPair = crossPair;
        this.minProfitThreshold = minProfitThreshold;
        this.orderSize = orderSize;
        this.maxSlippage = maxSlippage;
        
        logger.info("Triangular Arbitrage Strategy initialized:");
        logger.info("Base pair: {}, Quote pair: {}, Cross pair: {}", basePair, quotePair, crossPair);
        logger.info("Min profit threshold: {}%, Order size: {}, Max slippage: {}%", 
            minProfitThreshold * 100, orderSize, maxSlippage * 100);
    }
    
    @Override
    public void initialize() {
        logger.info("Initializing Triangular Arbitrage Strategy");
        // Initialize price tracking
        lastPrices.put(basePair, 0.0);
        lastPrices.put(quotePair, 0.0);
        lastPrices.put(crossPair, 0.0);
    }
    
    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Update price data
        updatePriceData(tick);
        
        // Check if we have complete market data
        if (!hasCompleteMarketData()) {
            return orders;
        }
        
        // Skip if we already have an active position
        if (activePosition != null && !activePosition.isComplete()) {
            return orders;
        }
        
        // Detect arbitrage opportunities
        ArbitrageOpportunity opportunity = detectArbitrageOpportunity();
        if (opportunity != null) {
            opportunitiesDetected++;
            logger.info("Arbitrage opportunity detected: {}% profit", 
                String.format("%.4f", opportunity.profitPercent * 100));
            
            // Execute arbitrage
            orders = executeArbitrage(opportunity, orderBook);
        }
        
        return orders;
    }
    
    /**
     * Update price data from incoming tick
     */
    private void updatePriceData(Tick tick) {
        lastPrices.put(tick.symbolId, tick.getPriceAsDouble());
        lastUpdateTime.put(tick.symbolId, System.currentTimeMillis());
    }
    
    /**
     * Check if we have recent prices for all three pairs
     */
    private boolean hasCompleteMarketData() {
        long now = System.currentTimeMillis();
        long staleThreshold = 5000; // 5 seconds
        
        for (int pair : Arrays.asList(basePair, quotePair, crossPair)) {
            Double price = lastPrices.get(pair);
            Long time = lastUpdateTime.get(pair);
            
            if (price == null || price <= 0 || time == null || (now - time) > staleThreshold) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Detect triangular arbitrage opportunities
     */
    private ArbitrageOpportunity detectArbitrageOpportunity() {
        double basePrice = lastPrices.get(basePair); // BTC/USDT
        double quotePrice = lastPrices.get(quotePair); // ETH/USDT
        double crossPrice = lastPrices.get(crossPair); // ETH/BTC
        
        // Calculate implied cross rate
        double impliedCrossRate = quotePrice / basePrice; // ETH/BTC implied
        
        // Calculate arbitrage profit for both directions
        
        // Direction 1: USDT -> BTC -> ETH -> USDT
        double profit1 = calculateArbitrageProfit(basePrice, crossPrice, quotePrice, true);
        
        // Direction 2: USDT -> ETH -> BTC -> USDT  
        double profit2 = calculateArbitrageProfit(basePrice, crossPrice, quotePrice, false);
        
        // Choose the more profitable direction
        if (profit1 > minProfitThreshold && profit1 > profit2) {
            return new ArbitrageOpportunity(ArbitrageDirection.USDT_TO_BTC_TO_ETH, profit1, 
                basePrice, crossPrice, quotePrice);
        } else if (profit2 > minProfitThreshold) {
            return new ArbitrageOpportunity(ArbitrageDirection.USDT_TO_ETH_TO_BTC, profit2, 
                basePrice, crossPrice, quotePrice);
        }
        
        return null;
    }
    
    /**
     * Calculate arbitrage profit for a specific direction
     */
    private double calculateArbitrageProfit(double basePrice, double crossPrice, double quotePrice, boolean direction1) {
        if (direction1) {
            // USDT -> BTC -> ETH -> USDT
            // Start with orderSize USDT
            double btc = orderSize / basePrice;           // Buy BTC
            double eth = btc / crossPrice;                // Sell BTC for ETH
            double usdtFinal = eth * quotePrice;          // Sell ETH for USDT
            return (usdtFinal - orderSize) / orderSize;   // Return percentage
        } else {
            // USDT -> ETH -> BTC -> USDT
            double eth = orderSize / quotePrice;          // Buy ETH
            double btc = eth * crossPrice;                // Sell ETH for BTC
            double usdtFinal = btc * basePrice;          // Sell BTC for USDT
            return (usdtFinal - orderSize) / orderSize;   // Return percentage
        }
    }
    
    /**
     * Execute arbitrage orders
     */
    private List<Order> executeArbitrage(ArbitrageOpportunity opportunity, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        if (opportunity.direction == ArbitrageDirection.USDT_TO_BTC_TO_ETH) {
            // Execute: USDT -> BTC -> ETH -> USDT
            orders.addAll(createOrdersForDirection1(opportunity, orderBook));
        } else {
            // Execute: USDT -> ETH -> BTC -> USDT
            orders.addAll(createOrdersForDirection2(opportunity, orderBook));
        }
        
        // Track active position
        activePosition = new ArbitragePosition(opportunity, orders);
        
        return orders;
    }
    
    /**
     * Create orders for direction 1: USDT -> BTC -> ETH -> USDT
     */
    private List<Order> createOrdersForDirection1(ArbitrageOpportunity opp, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Order 1: Buy BTC with USDT (market order)
        Order buyBTC = new Order(
            orderIdGenerator.getAndIncrement(),
            basePair,
            orderBook.getBestAsk(), // Buy at best ask
            orderSize,
            (byte)0, // Buy
            (byte)1  // Market order
        );
        orders.add(buyBTC);
        
        // Order 2: Sell BTC for ETH (market order)
        // Calculate amount: (orderSize / basePrice) ETH
        int btcAmount = (int)(orderSize / opp.basePrice);
        Order sellBTC = new Order(
            orderIdGenerator.getAndIncrement(),
            crossPair,
            orderBook.getBestBid(), // Sell at best bid
            btcAmount,
            (byte)1, // Sell
            (byte)1  // Market order
        );
        orders.add(sellBTC);
        
        // Order 3: Sell ETH for USDT (market order)
        // Calculate amount: btcAmount * crossPrice ETH
        int ethAmount = (int)(btcAmount * opp.crossPrice);
        Order sellETH = new Order(
            orderIdGenerator.getAndIncrement(),
            quotePair,
            orderBook.getBestBid(), // Sell at best bid
            ethAmount,
            (byte)1, // Sell
            (byte)1  // Market order
        );
        orders.add(sellETH);
        
        return orders;
    }
    
    /**
     * Create orders for direction 2: USDT -> ETH -> BTC -> USDT
     */
    private List<Order> createOrdersForDirection2(ArbitrageOpportunity opp, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();
        
        // Order 1: Buy ETH with USDT (market order)
        Order buyETH = new Order(
            orderIdGenerator.getAndIncrement(),
            quotePair,
            orderBook.getBestAsk(), // Buy at best ask
            orderSize,
            (byte)0, // Buy
            (byte)1  // Market order
        );
        orders.add(buyETH);
        
        // Order 2: Sell ETH for BTC (market order)
        int ethAmount = (int)(orderSize / opp.quotePrice);
        Order sellETH = new Order(
            orderIdGenerator.getAndIncrement(),
            crossPair,
            orderBook.getBestBid(), // Sell at best bid
            ethAmount,
            (byte)1, // Sell
            (byte)1  // Market order
        );
        orders.add(sellETH);
        
        // Order 3: Sell BTC for USDT (market order)
        int btcAmount = (int)(ethAmount * opp.crossPrice);
        Order sellBTC = new Order(
            orderIdGenerator.getAndIncrement(),
            basePair,
            orderBook.getBestBid(), // Sell at best bid
            btcAmount,
            (byte)1, // Sell
            (byte)1  // Market order
        );
        orders.add(sellBTC);
        
        return orders;
    }
    
    @Override
    public void onTrade(com.hft.core.Trade trade) {
        // Update active position
        if (activePosition != null) {
            activePosition.onTrade(trade);
            
            // Calculate P&L when position is complete
            if (activePosition.isComplete()) {
                double realizedPnL = activePosition.getRealizedPnL();
                totalPnL += realizedPnL;
                tradesExecuted++;
                
                logger.info("Arbitrage completed: P&L ${}, Total P&L ${}", 
                    String.format("%.4f", realizedPnL), 
                    String.format("%.4f", totalPnL));
                
                activePosition = null;
            }
        }
    }
    
    @Override
    public String getName() {
        return "TriangularArbitrage";
    }
    
    @Override
    public double getPnL() {
        return totalPnL;
    }
    
    @Override
    public void reset() {
        totalPnL = 0.0;
        opportunitiesDetected = 0;
        tradesExecuted = 0;
        activePosition = null;
        lastPrices.clear();
        lastUpdateTime.clear();
    }
    
    // Supporting classes
    public enum ArbitrageDirection {
        USDT_TO_BTC_TO_ETH,
        USDT_TO_ETH_TO_BTC
    }
    
    public static class ArbitrageOpportunity {
        public final ArbitrageDirection direction;
        public final double profitPercent;
        public final double basePrice;
        public final double crossPrice;
        public final double quotePrice;
        
        public ArbitrageOpportunity(ArbitrageDirection direction, double profitPercent,
                                  double basePrice, double crossPrice, double quotePrice) {
            this.direction = direction;
            this.profitPercent = profitPercent;
            this.basePrice = basePrice;
            this.crossPrice = crossPrice;
            this.quotePrice = quotePrice;
        }
    }
    
    public static class ArbitragePosition {
        private final ArbitrageOpportunity opportunity;
        private final List<Order> orders;
        private final Map<Long, Double> fills = new HashMap<>();
        private boolean complete = false;
        
        public ArbitragePosition(ArbitrageOpportunity opportunity, List<Order> orders) {
            this.opportunity = opportunity;
            this.orders = orders;
        }
        
        public void onTrade(com.hft.core.Trade trade) {
            fills.put(trade.tradeId, trade.getPriceAsDouble() * trade.quantity);
            
            // Check if all orders are filled (simplified)
            if (fills.size() >= orders.size()) {
                complete = true;
            }
        }
        
        public boolean isComplete() {
            return complete;
        }
        
        public double getRealizedPnL() {
            // Simplified P&L calculation
            return opportunity.profitPercent * 10000; // Convert to dollar amount
        }
    }
}
