package com.hft.utils;

/**
 * Simple runner to fetch Binance klines via DataFetcher.
 */
public class RunDataFetcher {
    public static void main(String[] args) {
        String out = "data/binance_BTCUSDT_1m_30d.csv";
        System.out.println("Fetching Binance BTCUSDT 1m klines (30 days) to " + out);
        String res = DataFetcher.fetchBinanceKlines("BTCUSDT", "1m", 30, out);
        if (res != null) System.out.println("Wrote: " + res);
        else System.err.println("Fetch failed or no network available");
    }
}
