package com.hft;

import com.hft.core.SymbolMapper;
import com.hft.core.Tick;
import com.hft.core.integration.UltraHighPerformanceEngine;
import com.hft.exchange.BinanceConnector;
import com.ft.risk.RiskManager;
import com.hft.strategy.MarketMakingStrategy;
import com.hft.strategy.MomentumStrategy;
import com.hft.strategy.StatisticalArbitrageStrategy;
import com.hft.strategy.TriangularArbitrageStrategy;
import com.hft.strategy.AIEnhancedStrategy;
import com.hft.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // ─── Shared shutdown flag ───────────────────────────────────────────────
    // Volatile AtomicBoolean so every thread sees the update immediately.
    private static final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    // Latch so the main thread can block cheaply and wake up the instant
    // the shutdown hook fires.
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    // Hold references so the hook can reach them without statics inside lambdas.
    private static volatile UltraHighPerformanceEngine engine;
    private static volatile BinanceConnector connector;
    private static volatile Thread dataThread;

    public static void main(String[] args) {
        logger.info("=== HFT Trading System ===");
        logger.info("Starting up...");

        // ─── Shutdown hook ───────────────────────────────────────────────────
        // Rules:
        //  • Never call System.exit() inside a shutdown hook – it deadlocks.
        //  • Do NOT interrupt the hook thread itself – interrupt worker threads.
        //  • Keep it fast: signal + join with timeout, then return.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received (Ctrl+C / SIGTERM).");

            // 1. Signal all loops to stop.
            shutdownRequested.set(true);
            shutdownLatch.countDown(); // wake main thread immediately

            // 2. Interrupt data thread so it doesn't block on connector.getNextTick().
            Thread dt = dataThread;
            if (dt != null) {
                dt.interrupt();
            }

            // 3. Stop the trading engine (should be non-blocking / timeout-guarded inside).
            UltraHighPerformanceEngine eng = engine;
            if (eng != null && eng.isRunning()) {
                logger.info("Stopping engine...");
                eng.stop();
            }

            // 4. Stop the exchange connector (closes WebSocket / sockets).
            BinanceConnector conn = connector;
            if (conn != null) {
                logger.info("Disconnecting Binance connector...");
                conn.disconnect(); // make sure BinanceConnector has this method
            }

            // 5. Wait briefly for data thread to finish.
            Thread dt2 = dataThread;
            if (dt2 != null && dt2.isAlive()) {
                try {
                    dt2.join(3_000); // max 3 s
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            logger.info("Shutdown complete.");
            // Do NOT call System.exit() here – the JVM exits naturally after all
            // shutdown hooks finish and no non-daemon threads are running.
        }, "Shutdown-Hook"));

        // ─── Connect to Binance ──────────────────────────────────────────────
        List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT");
        logger.info("Connecting to Binance...");
        connector = new BinanceConnector(symbols);
        connector.connect();

        int attempts = 0;
        while (!connector.isConnected() && attempts < 20) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            attempts++;
        }

        if (!connector.isConnected()) {
            logger.error("Failed to connect to Binance after {} attempts.", attempts);
            return;
        }
        logger.info("Connected successfully!");

        // ─── Strategy & risk ────────────────────────────────────────────────
        Strategy strategy = chooseStrategy();
        RiskManager.RiskConfig riskConfig = RiskManager.RiskConfig.moderate();
        RiskManager riskManager = new RiskManager(riskConfig);

        // ─── Engine ─────────────────────────────────────────────────────────
        logger.info("Starting Ultra-High Performance Trading Engine...");
        engine = new UltraHighPerformanceEngine(strategy, riskManager);
        engine.start();

        // ─── Data feed thread ────────────────────────────────────────────────
        // Mark as daemon=true so it cannot prevent JVM exit on its own.
        dataThread = new Thread(() -> {
            logger.info("Data thread started.");
            try {
                while (!shutdownRequested.get() && engine.isRunning()) {
                    Tick tick = connector.getNextTick(); // must respect interrupts!
                    if (tick != null) {
                        engine.processTick(
                            tick.timestamp, tick.symbolId,
                            tick.price,     tick.volume, tick.side
                        );
                    }
                }
            } catch (InterruptedException e) {
                // Normal path on Ctrl+C – just exit cleanly.
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Unexpected error in data thread", e);
            }
            logger.info("Data thread exiting.");
        }, "Binance-Data-Thread");
        dataThread.setDaemon(true);
        dataThread.start();

        // ─── Main loop ───────────────────────────────────────────────────────
        logger.info("System running. Press Ctrl+C to stop (auto-shutdown in 5 min).");

        long startTime   = System.currentTimeMillis();
        long maxRunMs    = 300_000L; // 5 minutes
        long lastLogTime = startTime;

        try {
            // Block on latch instead of sleeping – wakes instantly on Ctrl+C.
            while (!shutdownRequested.get() && engine.isRunning()) {
                long elapsed   = System.currentTimeMillis() - startTime;
                long remaining = maxRunMs - elapsed;

                if (remaining <= 0) {
                    logger.warn("Auto-shutdown: 5-minute limit reached.");
                    break;
                }

                // Log status every 10 s, then go back to sleep.
                long sleepMs = Math.min(remaining, 10_000L);
                boolean signalled = shutdownLatch.await(sleepMs, TimeUnit.MILLISECONDS);
                if (signalled) break; // Ctrl+C woke us up

                long now = System.currentTimeMillis();
                if (now - lastLogTime >= 10_000L) {
                    logger.info("Running... {} s remaining.", (maxRunMs - (now - startTime)) / 1000);
                    lastLogTime = now;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ─── Graceful teardown (also reached on auto-timeout) ────────────────
        // The shutdown hook handles the Ctrl+C path; this handles the timeout path.
        if (!shutdownRequested.get()) {
            shutdownRequested.set(true);

            Thread dt = dataThread;
            if (dt != null) dt.interrupt();

            if (engine.isRunning()) engine.stop();
            connector.disconnect();
        }

        // Wait for data thread to finish before printing final stats.
        try {
            if (dataThread != null) dataThread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ─── Final statistics ────────────────────────────────────────────────
        logger.info("=== Final Statistics ===");
        logger.info("Strategy      : {}", strategy.getName());
        logger.info("Total P&L     : ${}", String.format("%.2f", strategy.getPnL()));
        logger.info("Ticks         : {}", engine.getTicksProcessed());
        logger.info("Orders        : {}", engine.getOrdersProcessed());
        logger.info("Trades        : {}", engine.getTradesExecuted());
        logger.info("Aeron msgs    : {}", engine.getMessagesProcessed());
        logger.info("FIX msgs      : {}", engine.getFixMessagesProcessed());
        logger.info("========================");
        logger.info("Goodbye!");
    }

    // ─── Strategy selection ──────────────────────────────────────────────────
    private static Strategy chooseStrategy() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("\nChoose Trading Strategy:");
            System.out.println("1. Market Making  – provides liquidity, captures spread");
            System.out.println("2. Momentum       – follows price trends");
            System.out.println("3. Triangular Arb – exploits cross-currency inefficiencies");
            System.out.println("4. Stat Arb       – mean reversion / pairs trading");
            System.out.println("5. AI-Enhanced    – Gemini/Perplexity AI-powered trading");
            System.out.print("Enter choice (1-5): ");

            int choice = 1;
            try { choice = scanner.nextInt(); } catch (Exception ignored) {}

            switch (choice) {
                case 2:
                    logger.info("Strategy: Momentum");
                    return new MomentumStrategy(SymbolMapper.BTCUSDT, 20, 0.05, 1, 10);
                case 3:
                    logger.info("Strategy: Triangular Arbitrage");
                    return new TriangularArbitrageStrategy(
                        SymbolMapper.BTCUSDT, SymbolMapper.ETHUSDT, 2, 0.001, 1000, 0.005);
                case 4:
                    logger.info("Strategy: Statistical Arbitrage");
                    int[] pairs = {SymbolMapper.BTCUSDT, SymbolMapper.ETHUSDT};
                    return new StatisticalArbitrageStrategy(pairs, 30, 1.2, 0.0005, 1);
                case 5:
                    logger.info("Strategy: AI-Enhanced");
                    return new AIEnhancedStrategy(SymbolMapper.BTCUSDT, 1, 10);
                default:
                    logger.info("Strategy: Market Making");
                    return new MarketMakingStrategy(SymbolMapper.BTCUSDT, 0.02, 1, 5);
            }
        }
    }
}