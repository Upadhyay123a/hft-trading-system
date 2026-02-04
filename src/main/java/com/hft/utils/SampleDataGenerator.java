package com.hft.utils;

import com.hft.core.SymbolMapper;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;

/**
 * Generate sample market data for backtesting
 */
public class SampleDataGenerator {
    
    public static void generateData(String filename, int numTicks) throws Exception {
        System.out.println("Generating " + numTicks + " sample ticks to " + filename);
        
        Random random = new Random(42); // Fixed seed for reproducibility
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header
            writer.println("timestamp,symbolId,price,volume,side");
            
            // Generate realistic BTC price movement
            double basePrice = 50000.0; // Starting at $50,000
            long timestamp = System.currentTimeMillis() * 1_000_000; // Convert to nanos
            
            for (int i = 0; i < numTicks; i++) {
                // Random walk with drift
                double change = random.nextGaussian() * 10 + 0.05; // Small upward drift
                basePrice += change;
                
                // Keep price reasonable
                basePrice = Math.max(30000, Math.min(70000, basePrice));
                
                // Generate tick
                long price = (long)(basePrice * 10000); // Convert to internal format
                long volume = 100000 + random.nextInt(900000);
                byte side = (byte)(random.nextBoolean() ? 0 : 1);
                
                // Random time between ticks (1-100ms)
                timestamp += (1 + random.nextInt(100)) * 1_000_000;
                
                writer.printf("%d,%d,%d,%d,%d\n",
                    timestamp,
                    SymbolMapper.BTCUSDT,
                    price,
                    volume,
                    side
                );
                
                if (i % 10000 == 0 && i > 0) {
                    System.out.println("Generated " + i + " ticks...");
                }
            }
        }
        
        System.out.println("Data generation complete!");
    }
    
    public static void main(String[] args) {
        try {
            String filename = "data/sample_market_data.csv";
            generateData(filename, 100000); // Generate 100k ticks
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}