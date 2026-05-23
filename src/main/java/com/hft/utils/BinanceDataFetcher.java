package com.hft.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches historical BTCUSDT 1-minute klines from Binance public API and saves to CSV.
 * No external JSON library required; uses regex parsing.
 */
public class BinanceDataFetcher {
    private static final String API_URL = "https://api.binance.com/api/v3/klines";
    private static final String SYMBOL = "BTCUSDT";
    private static final String INTERVAL = "1m";
    private static final int BATCH_SIZE = 1000;
    private static final String OUTPUT_CSV = "data/binance_BTCUSDT_1m_30d.csv";

    public static void main(String[] args) throws Exception {
        System.out.println("Fetching Binance BTCUSDT 1-minute klines...");
        
        // Calculate time range: last 30 days
        long endTime = System.currentTimeMillis();
        long startTime = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli();
        
        System.out.println("Start time: " + new java.util.Date(startTime));
        System.out.println("End time: " + new java.util.Date(endTime));
        
        // Fetch all klines
        List<String[]> allKlines = new ArrayList<>();
        long currentStartTime = startTime;
        int batchCount = 0;
        
        while (currentStartTime < endTime) {
            System.out.println("Fetching batch " + (++batchCount) + " starting from " + new java.util.Date(currentStartTime));
            
            List<String[]> batch = fetchKlines(currentStartTime, endTime);
            if (batch.isEmpty()) {
                System.out.println("No more data available.");
                break;
            }
            
            allKlines.addAll(batch);
            
            // Move to next batch start time (last kline time + 1 ms)
            long lastKlineTime = Long.parseLong(batch.get(batch.size() - 1)[0]);
            currentStartTime = lastKlineTime + 1;
            
            System.out.println("Batch " + batchCount + " complete. Fetched " + batch.size() + " klines. Total: " + allKlines.size());
            
            // Be respectful to the API
            Thread.sleep(500);
        }
        
        System.out.println("Total klines fetched: " + allKlines.size());
        
        // Save to CSV
        saveToCSV(allKlines);
        System.out.println("Data saved to: " + OUTPUT_CSV);
    }
    
    private static List<String[]> fetchKlines(long startTime, long endTime) throws Exception {
        String urlStr = String.format(
            "%s?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
            API_URL, SYMBOL, INTERVAL, startTime, endTime, BATCH_SIZE
        );
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (HFT-Trading-System)");
        
        int status = conn.getResponseCode();
        if (status != 200) {
            System.err.println("API error: " + status);
            return new ArrayList<>();
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return parseKlinesJSON(response.toString());
    }
    
    /**
     * Parse JSON array of klines using regex (no external JSON library).
     * Each kline is: [timestamp, open, high, low, close, volume, ...]
     */
    private static List<String[]> parseKlinesJSON(String json) {
        List<String[]> klines = new ArrayList<>();
        
        // Match each kline array: [num, "str", "str", "str", "str", "str", ...]
        Pattern klinePattern = Pattern.compile("\\[(\\d+),\"([^\"]+)\",\"([^\"]+)\",\"([^\"]+)\",\"([^\"]+)\",\"([^\"]+)\"");
        Matcher matcher = klinePattern.matcher(json);
        
        while (matcher.find()) {
            String timestamp = matcher.group(1);
            String open = matcher.group(2);
            String high = matcher.group(3);
            String low = matcher.group(4);
            String close = matcher.group(5);
            String volume = matcher.group(6);
            
            klines.add(new String[]{timestamp, open, high, low, close, volume});
        }
        
        return klines;
    }
    
    private static void saveToCSV(List<String[]> klines) throws IOException {
        Files.createDirectories(Paths.get("data"));
        
        try (FileWriter fw = new FileWriter(OUTPUT_CSV); BufferedWriter bw = new BufferedWriter(fw)) {
            // Write header
            bw.write("timestamp,open,high,low,close,volume\n");
            
            for (String[] kline : klines) {
                bw.write(String.join(",", kline) + "\n");
            }
        }
    }
}
