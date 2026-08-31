package com.hft.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hft.exchange.api.BinanceRealApi;

public class BinanceSmokeDemo {
    private static final Logger logger = LoggerFactory.getLogger(BinanceSmokeDemo.class);

    public static void main(String[] args) {
        logger.info("Starting Binance smoke demo (public market data) — running 30s...");

        BinanceRealApi api = new BinanceRealApi();

        try {
            api.connectMarketData(List.of("BTCUSDT")).join();

            // Wait for messages to arrive
            Thread.sleep(30_000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error during smoke demo", e);
        } finally {
            api.disconnect();
            logger.info("Binance smoke demo finished");
        }
    }
}
