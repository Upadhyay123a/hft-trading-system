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

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/**
 * Fetches historical BTCUSDT 1-minute klines from Binance public API and saves to CSV.
 * No API key required; uses free public endpoints.
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
        StringBuilder allKlinesBuffer = new StringBuilder();
        allKlinesBuffer.append("[\n");
        long currentStartTime = startTime;
        int batchCount = 0;
        boolean firstBatch = true;
        
        while (currentStartTime < endTime) {
            System.out.println("Fetching batch " + (++batchCount) + " starting from " + new java.util.Date(currentStartTime));
            
            JsonArray batch = fetchKlines(currentStartTime, endTime);
            if (batch.size() == 0) {
                System.out.println("No more data available.");
                break;
            }
            
            if (!firstBatch) {
                allKlinesBuffer.append(",\n");
            }
            allKlinesBuffer.append(batch.toString());
            firstBatch = false;
            
            // Move to next batch start time (last kline time + 1 ms)
            long lastKlineTime = batch.get(batch.size() - 1).getAsJsonArray().get(0).getAsLong();
            currentStartTime = lastKlineTime + 1;
            
            // Be respectful to the API
            Thread.sleep(500);
        }
        
        allKlinesBuffer.append("\n]");
        JsonArray allKlines = JsonParser.parseString(allKlinesBuffer.toString()).getAsJsonArray();
        System.out.println("Total klines fetched: " + allKlines.size());
        
        // Save to CSV
        saveToCSV(allKlines);
        System.out.println("Data saved to: " + OUTPUT_CSV);
    }
    
    private static JsonArray fetchKlines(long startTime, long endTime) throws Exception {
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
            throw new IOException("API request failed with status " + status);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return JsonParser.parseString(response.toString()).getAsJsonArray();
    }
    
    private static void saveToCSV(JsonArray klines) throws IOException {
        Files.createDirectories(Paths.get("data"));
        
        try (FileWriter fw = new FileWriter(OUTPUT_CSV); BufferedWriter bw = new BufferedWriter(fw)) {
            // Write header
            bw.write("timestamp,open,high,low,close,volume\n");
            
            for (int i = 0; i < klines.size(); i++) {
                JsonArray kline = klines.get(i).getAsJsonArray();
                
                long timestamp = kline.get(0).getAsLong();
                String open = kline.get(1).getAsString();
                String high = kline.get(2).getAsString();
                String low = kline.get(3).getAsString();
                String close = kline.get(4).getAsString();
                String volume = kline.get(7).getAsString(); // Quote asset volume
                
                bw.write(String.format("%d,%s,%s,%s,%s,%s\n", timestamp, open, high, low, close, volume));
            }
        }
    }
}
