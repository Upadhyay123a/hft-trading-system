package com.hft.strategy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import com.hft.core.Tick;
import com.hft.orderbook.OrderBook;

public class StatArbEndToEndTest {
    private static class SimpleOrderBook extends OrderBook {
        private long midPrice;
        public SimpleOrderBook(int symbolId, long midPrice) { super(symbolId); this.midPrice = midPrice; }
        @Override public long getMidPrice() { return midPrice; }
    }

    @Test
    public void testStatArbBuildsModelSafely() {
        int[] symbols = {1, 2};
        StatisticalArbitrageStrategy strat = new StatisticalArbitrageStrategy(symbols, 3, 1.0, 0.0, 1);
        strat.initialize();

        OrderBook ob1 = new SimpleOrderBook(1, 10000L);
        OrderBook ob2 = new SimpleOrderBook(2, 20000L);

        // Feed 3 ticks per symbol
        for (int i = 0; i < 3; i++) {
            strat.onTick(new Tick(System.currentTimeMillis(), 1, 10000L + i*10, 1, (byte)0), ob1);
            strat.onTick(new Tick(System.currentTimeMillis(), 2, 20000L + i*20, 1, (byte)0), ob2);
        }

        // Now call onTick again and ensure it doesn't throw and returns orders (may be empty)
        List<com.hft.core.Order> orders = strat.onTick(new Tick(System.currentTimeMillis(), 1, 10030L, 1, (byte)0), ob1);
        assertNotNull(orders);
    }
}
