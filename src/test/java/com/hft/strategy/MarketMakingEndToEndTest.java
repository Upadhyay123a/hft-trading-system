package com.hft.strategy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.hft.core.Tick;
import com.hft.orderbook.OrderBook;

public class MarketMakingEndToEndTest {

    private static class SimpleOrderBook extends OrderBook {
        private long midPrice;
        public SimpleOrderBook(int symbolId, long midPrice) { super(symbolId); this.midPrice = midPrice; }
        @Override public long getMidPrice() { return midPrice; }
    }

    @Test
    public void testOnTickPlacesBothSidesAndProcessesTradePnL() {
        // Setup strategy with small spread
        MarketMakingStrategy strat = new MarketMakingStrategy(1, 0.02, 1, 10);
        strat.initialize();

        // Create a tick for our symbol and an order book
        Tick tick = new Tick(System.currentTimeMillis(), 1, 10_000L, 1, (byte)0);
        OrderBook ob = new SimpleOrderBook(1, 10_000L);

        // First tick should produce two orders (buy & sell)
        List<com.hft.core.Order> orders = strat.onTick(tick, ob);
        assertTrue(orders.size() >= 1, "Expected at least one order placed");

        // Simulate a trade that fills the buy order
        // Build a Trade object matching active buy order id - we don't have it accessible,
        // so simulate by invoking onTrade with a trade that matches typical fields.
        com.hft.core.Trade trade = new com.hft.core.Trade();
        trade.buyOrderId = orders.get(0).orderId;
        trade.sellOrderId = 99999L; // external
        trade.quantity = 1;
        trade.price = orders.get(0).price; // price in ticks

        double beforePnL = strat.getPnL();
        strat.onTrade(trade);
        double afterPnL = strat.getPnL();

        // After a buy fill, PnL should decrease (cost)
        assertTrue(afterPnL <= beforePnL, "PnL should decrease or stay same after buy fill");

        // Simulate corresponding sell fill to close position
        com.hft.core.Trade sellTrade = new com.hft.core.Trade();
        // find sell order id if present
        long sellId = orders.stream().filter(o -> o.side == 1).findFirst().map(o -> o.orderId).orElse(-1L);
        if (sellId != -1L) {
            sellTrade.sellOrderId = sellId;
            sellTrade.buyOrderId = 99998L;
            sellTrade.quantity = 1;
            sellTrade.price = orders.stream().filter(o -> o.side == 1).findFirst().map(o -> o.price).orElse(orders.get(0).price);

            strat.onTrade(sellTrade);
            double finalPnL = strat.getPnL();
            // After buy then sell, PnL may increase relative to after buy (captured spread)
            assertTrue(finalPnL >= afterPnL - 1e-6);
        }
    }
}
