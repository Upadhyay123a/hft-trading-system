package com.hft.exchange;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hft.core.SymbolMapper;
import com.hft.core.Tick;

/**
 * CSV Data Connector for testing with historical data
 */
public class CsvDataConnector {
    private static final Logger logger = LoggerFactory.getLogger(CsvDataConnector.class);
    
    private final BlockingQueue<Tick> tickQueue = new LinkedBlockingQueue<>(1000);
    private final String csvFile;
    private volatile boolean connected = false;
    private volatile boolean running = false;
    private Thread dataThread;
    
    public CsvDataConnector(String csvFile) {
        this.csvFile = csvFile;
    }
    
    /**
     * Connect and start streaming data
     */
    public void connect() {
        try {
            logger.info("Connecting to CSV data source: {}", csvFile);
            
            // Register symbols
            SymbolMapper.register("btcusdt");
            
            connected = true;
            running = true;
            
            // Start data streaming thread
            dataThread = new Thread(this::streamData, "CSV-Data-Thread");
            dataThread.setDaemon(true);
            dataThread.start();
            
            logger.info("CSV data connector started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to connect to CSV data", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Stream data from CSV file
     */
    private void streamData() {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            int lineCount = 0;
            
            // Skip header if exists
            reader.readLine();
            
            while (running && (line = reader.readLine()) != null) {
                try {
                    Tick tick = parseCsvLine(line);
                    if (tick != null) {
                        if (!tickQueue.offer(tick)) {
                            // Queue full, remove oldest
                            tickQueue.poll();
                            tickQueue.offer(tick);
                        }
                        
                        // Debug: Log first few ticks
                        if (lineCount < 5) {
                            logger.info("CSV Tick {}: price={}, volume={}", 
                                       lineCount, tick.getPrice(), tick.volume);
                        }
                        
                        lineCount++;
                        
                        // Simulate real-time timing (1 tick per 100ms)
                        Thread.sleep(100);
                        
                    }
                } catch (Exception e) {
                    logger.error("Error parsing line {}: {}", lineCount, line, e);
                }
            }
            
            logger.info("CSV data streaming completed. {} ticks processed.", lineCount);
            
        } catch (Exception e) {
            logger.error("Error streaming CSV data", e);
        }
    }
    
    /**
     * Parse CSV line into Tick
     */
    private Tick parseCsvLine(String line) {
        try {
            // Expected CSV format: timestamp,open,high,low,close,volume
            String[] parts = line.split(",");
            if (parts.length >= 6) {
                Tick tick = new Tick();
                tick.timestamp = System.currentTimeMillis() * 1_000_000; // Current time in nanos
                tick.symbolId = SymbolMapper.getId("btcusdt");
                tick.setPrice(Double.parseDouble(parts[4])); // Use close price
                tick.volume = (long)(Double.parseDouble(parts[5]) * 100000); // Scale volume
                tick.side = 0; // Default side
                
                return tick;
            }
        } catch (Exception e) {
            logger.error("Error parsing CSV line: {}", line, e);
        }
        return null;
    }
    
    /**
     * Get next tick
     */
    public Tick getNextTick() throws InterruptedException {
        return tickQueue.poll(100, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected && running;
    }
    
    /**
     * Disconnect
     */
    public void disconnect() {
        running = false;
        connected = false;
        
        if (dataThread != null) {
            dataThread.interrupt();
        }
        
        logger.info("CSV data connector disconnected");
    }
    
    /**
     * Get queue size
     */
    public int getQueueSize() {
        return tickQueue.size();
    }
}
