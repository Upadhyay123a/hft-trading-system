package com.hft.exchange;

import org.junit.jupiter.api.Test;

import com.hft.exchange.api.BinanceRealApi;

public class BinancePublicSmokeTest {

    @Test
    public void smokeConnectsToPublicMarketData() throws Exception {
        BinanceRealApi api = new BinanceRealApi();
        try {
            api.connectMarketData(java.util.List.of("BTCUSDT")).join();
            // Allow some time for messages to arrive
            Thread.sleep(10_000);
        } finally {
            api.disconnect();
        }
    }
}
