package com.hft.strategy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.hft.core.Tick;
import com.hft.orderbook.OrderBook;

public class MomentumEndToEndTest {
    private static class SimpleOrderBook extends OrderBook {
        private long midPrice;
        public SimpleOrderBook(int symbolId, long midPrice) { super(symbolId); this.midPrice = midPrice; }
        @Override public long getMidPrice() { return midPrice; }
    }

    @Test
    public void testMomentumGeneratesBuyOnUpwardTrend() throws InterruptedException {
        MomentumStrategy strat = new MomentumStrategy(1, 3, 0.1, 1, 10);
        strat.initialize();

        OrderBook ob = new SimpleOrderBook(1, 10000L);

        // Feed increasing prices to exceed threshold
        strat.onTick(new Tick(System.currentTimeMillis(), 1, 10000L, 1, (byte)0), ob);
        Thread.sleep(10);
        strat.onTick(new Tick(System.currentTimeMillis(), 1, 10010L, 1, (byte)0), ob);
        Thread.sleep(10);
        List<com.hft.core.Order> orders = strat.onTick(new Tick(System.currentTimeMillis(), 1, 10030L, 1, (byte)0), ob);

        assertNotNull(orders);
        assertTrue(orders.stream().anyMatch(o -> o.side == 0), "Expected a BUY order on upward momentum");

        // Simulate a trade fill for buy order
        com.hft.core.Trade trade = new com.hft.core.Trade();
        com.hft.core.Order buy = orders.stream().filter(o -> o.side == 0).findFirst().orElse(null);
        assertNotNull(buy);
        trade.buyOrderId = buy.orderId;
        trade.sellOrderId = 9999L;
        trade.quantity = buy.quantity;
        trade.price = buy.price;

        double beforePnL = strat.getPnL();
        strat.onTrade(trade);
        double afterPnL = strat.getPnL();
        assertTrue(afterPnL <= beforePnL);
    }
}
