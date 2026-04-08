package com.hft.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hft.core.SymbolMapper;

/**
 * Lightweight utility to fetch public historical klines from Binance
 * and write a CSV in the project's `data/` directory compatible with
 * the backtest sample format.
 */
public class DataFetcher {
    private static final Logger logger = LoggerFactory.getLogger(DataFetcher.class);

    /**
     * Fetch recent klines for given symbol and interval and write CSV.
     * This method uses the public Binance REST API and does not require API keys.
     * Returns the path written on success, or null on failure.
     */
    public static String fetchBinanceKlines(String symbol, String interval, int days, String outPath) {
        try {
            File outFile = new File(outPath);
            outFile.getParentFile().mkdirs();

            // Binance limits 1000 records per request; we'll page from (now - days) to now.
            long now = System.currentTimeMillis();
            long millisPerDay = 24L * 60 * 60 * 1000;
            long startTime = now - (long)days * millisPerDay;

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, StandardCharsets.UTF_8))) {
                // header matching sample_market_data.csv: timestamp,symbolId,price,volume,side
                writer.write("timestamp,symbolId,price,volume,side\n");

                long fetchStart = startTime;
                while (fetchStart < now) {
                    long fetchEnd = Math.min(fetchStart + 1000L * 60L * 60L * 24L, now);
                    String url = String.format("https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=1000", symbol, interval, fetchStart, fetchEnd);

                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(10_000);
                    conn.setReadTimeout(30_000);
                    conn.setRequestMethod("GET");

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        logger.warn("Binance returned HTTP {} for {} - aborting fetch", code, url);
                        break;
                    }

                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        String body = sb.toString();

                        // Response is a JSON array of arrays; parse minimally to extract openTime, close, volume
                        // We avoid heavy JSON libs to keep dependency-free: do simple parsing assuming standard structure
                        // Split by "],[" to get bars
                        String trimmed = body.trim();
                        if (trimmed.length() < 3) break;
                        // Remove outer brackets
                        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
                        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length()-1);

                        String[] bars = trimmed.split("\\],\\[");
                        for (String bar : bars) {
                            String clean = bar.replaceAll("\\[|\\]", "");
                            String[] parts = clean.split(",");
                            if (parts.length < 6) continue;
                            long openTime = Long.parseLong(parts[0].trim());
                            double close = Double.parseDouble(parts[4].trim());
                            double volume = Double.parseDouble(parts[5].trim());

                            // Convert to the project's integer price/volume format used in sample CSV
                            long priceInt = (long)Math.round(close * 10000.0);
                            long volumeInt = (long)Math.round(volume * 1_000_000.0);
                            int side = 0; // no side info in klines; set 0 by default

                            int symbolId = SymbolMapper.BTCUSDT;
                            writer.write(String.format("%d,%d,%d,%d,%d\n", openTime * 1000L, symbolId, priceInt, volumeInt, side));
                        }
                    }

                    // Advance fetchStart by 1000 intervals (approx). To be safe, increment by one day chunk.
                    fetchStart += 1000L * 60L * 60L * 24L;
                }
            }

            logger.info("Fetched Binance klines for {} -> {}", symbol, outPath);
            return outPath;
        } catch (Exception e) {
            logger.error("Failed to fetch Binance klines: {}", e.getMessage(), e);
            return null;
        }
    }
}
