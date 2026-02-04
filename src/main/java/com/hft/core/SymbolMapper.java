package com.hft.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maps string symbols to integer IDs for fast comparison
 */
public class SymbolMapper {
    private static final ConcurrentHashMap<String, Integer> symbolToId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> idToSymbol = new ConcurrentHashMap<>();
    private static final AtomicInteger nextId = new AtomicInteger(1);
    
    // Pre-register common symbols
    public static final int BTCUSDT;
    public static final int ETHUSDT;
    public static final int BNBUSDT;
    
    static {
        BTCUSDT = register("BTCUSDT");
        ETHUSDT = register("ETHUSDT");
        BNBUSDT = register("BNBUSDT");
    }
    
    public static int register(String symbol) {
        return symbolToId.computeIfAbsent(symbol, s -> {
            int id = nextId.getAndIncrement();
            idToSymbol.put(id, s);
            return id;
        });
    }
    
    public static int getId(String symbol) {
        Integer id = symbolToId.get(symbol);
        if (id == null) {
            return register(symbol);
        }
        return id;
    }
    
    public static String getSymbol(int id) {
        return idToSymbol.get(id);
    }
    
    public static boolean exists(String symbol) {
        return symbolToId.containsKey(symbol);
    }
}