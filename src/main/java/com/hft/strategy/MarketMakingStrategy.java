package com.hft.strategy;

import com.hft.core.Order;
import com.hft.core.Tick;
import com.hft.core.Trade;
import com.hft.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Market Making Strategy
 * Places buy and sell orders around mid-price to capture spread
 */
public class MarketMakingStrategy implements Strategy {
    private static final Logger logger = LoggerFactory.getLogger(MarketMakingStrategy.class);

    private final int symbolId;
    private final long spreadTicks;     // Spread in price ticks (e.g., 10 = 0.001)
    private final int orderSize;        // Order quantity
    private final long maxPosition;     // Maximum position size

    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private long currentPosition = 0;   // Positive = long, negative = short
    private double totalPnL = 0.0;
    private long lastQuoteTime = 0;
    private final long quoteIntervalNanos = 100_000_000; // 100ms between quotes

    // Active orders
    private Long activeBuyOrderId  = null;
    private Long activeSellOrderId = null;

    // FIX 2: Track when each active order was placed so we can expire stale ones.
    // If a resting order is never filled (e.g. price moved away, no counterparty),
    // activeBuyOrderId/activeSellOrderId would stay set forever and block new quotes.
    private long activeBuyOrderTime  = 0;
    private long activeSellOrderTime = 0;
    private static final long STALE_ORDER_NANOS = 5_000_000_000L; // 5 seconds

    public MarketMakingStrategy(int symbolId, double spreadPercent, int orderSize, long maxPosition) {
        this.symbolId    = symbolId;
        this.spreadTicks = (long)(spreadPercent * 10000); // Convert percent to ticks
        this.orderSize   = orderSize;
        this.maxPosition = maxPosition;
    }

    @Override
    public void initialize() {
        logger.info("Initialized Market Making Strategy for symbol {}", symbolId);
        logger.info("Spread: {}%, Order Size: {}, Max Position: {}",
            spreadTicks / 100.0, orderSize, maxPosition);
    }

    @Override
    public List<Order> onTick(Tick tick, OrderBook orderBook) {
        List<Order> orders = new ArrayList<>();

        // Only process ticks for our symbol
        if (tick.symbolId != symbolId) {
            return orders;
        }

        long now = System.nanoTime();

        // FIX 2: Expire stale active orders so quoting is never blocked permanently.
        // An order is stale if it was placed more than STALE_ORDER_NANOS ago with no fill.
        if (activeBuyOrderId != null && (now - activeBuyOrderTime) > STALE_ORDER_NANOS) {
            logger.debug("MarketMaking: Expiring stale BUY order id={}", activeBuyOrderId);
            activeBuyOrderId = null;
        }
        if (activeSellOrderId != null && (now - activeSellOrderTime) > STALE_ORDER_NANOS) {
            logger.debug("MarketMaking: Expiring stale SELL order id={}", activeSellOrderId);
            activeSellOrderId = null;
        }

        // Rate limit quoting
        if (now - lastQuoteTime < quoteIntervalNanos) {
            return orders;
        }
        lastQuoteTime = now;

        // Get current market state
        long midPrice = 0;
        if (orderBook != null && orderBook.getMidPrice() > 0) {
            midPrice = orderBook.getMidPrice();
        } else if (tick.price > 0) {
            // Correct fallback: use last trade price when order book is empty
            midPrice = tick.price;
            logger.debug("Using tick price as mid price: {}", tick.price);
        }

        if (midPrice == 0) {
            return orders; // No market data yet
        }

        // Calculate quote prices
        long bidPrice = midPrice - spreadTicks / 2;
        long askPrice = midPrice + spreadTicks / 2;

        // Check position limits
        boolean canBuy  = currentPosition < maxPosition;
        boolean canSell = currentPosition > -maxPosition;

        logger.debug("MarketMaking: midPrice={}, bid={}, ask={}, canBuy={}, canSell={}, activeBuy={}, activeSell={}",
                    midPrice, bidPrice, askPrice, canBuy, canSell, activeBuyOrderId, activeSellOrderId);

        // Place buy order
        if (canBuy && activeBuyOrderId == null) {
            Order buyOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                bidPrice,
                orderSize,
                (byte) 0, // Buy
                (byte) 0  // Limit
            );
            orders.add(buyOrder);
            activeBuyOrderId  = buyOrder.orderId;
            activeBuyOrderTime = now; // FIX 2: record placement time
            logger.info("MarketMaking: Placed BUY order - id={}, price={}, qty={}",
                       buyOrder.orderId, bidPrice, orderSize);
        } else {
            logger.debug("MarketMaking: Skipping BUY - canBuy={}, activeBuyId={}", canBuy, activeBuyOrderId);
        }

        // Place sell order
        if (canSell && activeSellOrderId == null) {
            Order sellOrder = new Order(
                orderIdGenerator.getAndIncrement(),
                symbolId,
                askPrice,
                orderSize,
                (byte) 1, // Sell
                (byte) 0  // Limit
            );
            orders.add(sellOrder);
            activeSellOrderId  = sellOrder.orderId;
            activeSellOrderTime = now; // FIX 2: record placement time
            logger.info("MarketMaking: Placed SELL order - id={}, price={}, qty={}",
                       sellOrder.orderId, askPrice, orderSize);
        } else {
            logger.debug("MarketMaking: Skipping SELL - canSell={}, activeSellId={}", canSell, activeSellOrderId);
        }

        logger.debug("MarketMaking: Returning {} orders", orders.size());
        return orders;
    }

    @Override
    public void onTrade(Trade trade) {
        // FIX 1: Capture whether this trade matches our active orders BEFORE nulling them.
        // Original code nulled activeBuyOrderId first, then checked (activeBuyOrderId != null)
        // — that check was always false, so totalPnL was NEVER updated. getPnL() returned 0 always.
        boolean wasOurBuy  = (activeBuyOrderId  != null && trade.buyOrderId  == activeBuyOrderId);
        boolean wasOurSell = (activeSellOrderId != null && trade.sellOrderId == activeSellOrderId);

        // Now safe to update position and clear active order slots
        if (wasOurBuy) {
            currentPosition += trade.quantity;
            activeBuyOrderId = null;
            logger.debug("Buy filled: qty={}, newPos={}", trade.quantity, currentPosition);
        }
        if (wasOurSell) {
            currentPosition -= trade.quantity;
            activeSellOrderId = null;
            logger.debug("Sell filled: qty={}, newPos={}", trade.quantity, currentPosition);
        }

        // Update P&L — now correctly reachable since wasOurBuy/wasOurSell are evaluated first
        double tradeValue = trade.getPriceAsDouble() * trade.quantity;

        if (wasOurBuy) {
            totalPnL -= tradeValue; // We paid for the position
            logger.debug("Buy trade PnL update: price={}, qty={}, cost={}, totalPnL={}",
                        trade.getPriceAsDouble(), trade.quantity, tradeValue, totalPnL);
        } else if (wasOurSell) {
            totalPnL += tradeValue; // We received cash for the position
            logger.debug("Sell trade PnL update: price={}, qty={}, revenue={}, totalPnL={}",
                        trade.getPriceAsDouble(), trade.quantity, tradeValue, totalPnL);
        }
    }

    @Override
    public String getName() {
        return "MarketMaking";
    }

    @Override
    public double getPnL() {
        return totalPnL;
    }

    @Override
    public void reset() {
        currentPosition    = 0;
        totalPnL           = 0.0;
        activeBuyOrderId   = null;
        activeSellOrderId  = null;
        activeBuyOrderTime  = 0;
        activeSellOrderTime = 0;
    }

    public long getCurrentPosition() {
        return currentPosition;
    }
}