package com.hft.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * High-Performance Monitoring System for HFT Trading
 * Tracks latency, throughput, memory usage, and custom metrics
 */
public class PerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);
    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    
    // JVM monitoring
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    
    // Performance metrics
    private final ConcurrentHashMap<String, Metric> metrics = new ConcurrentHashMap<>();
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final DoubleAdder totalLatency = new DoubleAdder();
    private final AtomicLong startTime = new AtomicLong(System.nanoTime());
    
    // Latency tracking
    private final ConcurrentHashMap<String, LatencyTracker> latencyTrackers = new ConcurrentHashMap<>();
    
    // Throughput tracking
    private final ConcurrentHashMap<String, ThroughputTracker> throughputTrackers = new ConcurrentHashMap<>();
    
    private PerformanceMonitor() {
        // Start monitoring thread
        Thread monitoringThread = new Thread(this::monitoringLoop, "PerformanceMonitor");
        monitoringThread.setDaemon(true);
        monitoringThread.start();
        
        logger.info("Performance Monitor initialized");
    }
    
    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Record operation latency
     */
    public void recordLatency(String operation, long latencyNanos) {
        totalOperations.incrementAndGet();
        totalLatency.add(latencyNanos);
        
        latencyTrackers.computeIfAbsent(operation, k -> new LatencyTracker())
                      .record(latencyNanos);
    }
    
    /**
     * Record custom metric
     */
    public void recordMetric(String name, double value) {
        metrics.computeIfAbsent(name, k -> new Metric(name)).add(value);
    }
    
    /**
     * Increment counter
     */
    public void incrementCounter(String name) {
        recordMetric(name, 1);
    }
    
    /**
     * Record throughput event
     */
    public void recordThroughput(String operation, int count) {
        throughputTrackers.computeIfAbsent(operation, k -> new ThroughputTracker())
                         .record(count);
    }
    
    /**
     * Start latency measurement
     */
    public LatencyMeasurement startMeasurement(String operation) {
        return new LatencyMeasurement(operation, System.nanoTime());
    }
    
    /**
     * Get performance summary
     */
    public PerformanceSummary getSummary() {
        long uptimeNanos = System.nanoTime() - startTime.get();
        double uptimeSeconds = uptimeNanos / 1_000_000_000.0;
        
        long opsPerSecond = uptimeSeconds > 0 ? (long)(totalOperations.get() / uptimeSeconds) : 0;
        double avgLatencyMs = totalOperations.get() > 0 ? 
            (totalLatency.sum() / totalOperations.get() / 1_000_000.0) : 0.0;
        
        // Memory usage
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        double memoryUsagePercent = maxMemory > 0 ? (double)usedMemory / maxMemory * 100 : 0.0;
        
        // Thread count
        int threadCount = threadBean.getThreadCount();
        
        return new PerformanceSummary(
            uptimeSeconds, opsPerSecond, avgLatencyMs, memoryUsagePercent, 
            threadCount, getLatencyStats(), getThroughputStats(), getCustomMetrics()
        );
    }
    
    /**
     * Print performance report
     */
    public void printReport() {
        PerformanceSummary summary = getSummary();
        
        logger.info("=== Performance Report ===");
        logger.info("Uptime: {:.1f}s", summary.uptimeSeconds);
        logger.info("Operations/sec: {}", summary.operationsPerSecond);
        logger.info("Avg Latency: {:.3f}ms", summary.avgLatencyMs);
        logger.info("Memory Usage: {:.1f}%", summary.memoryUsagePercent);
        logger.info("Thread Count: {}", summary.threadCount);
        
        logger.info("--- Latency by Operation ---");
        summary.latencyStats.forEach((op, stats) -> {
            logger.info("{}: count={}, avg={:.3f}ms, p50={:.3f}ms, p95={:.3f}ms, p99={:.3f}ms",
                op, stats.count, stats.avgMs, stats.p50Ms, stats.p95Ms, stats.p99Ms);
        });
        
        logger.info("--- Throughput by Operation ---");
        summary.throughputStats.forEach((op, stats) -> {
            logger.info("{}: {}/sec (avg over 60s)", op, stats.eventsPerSecond);
        });
        
        logger.info("--- Custom Metrics ---");
        summary.customMetrics.forEach((name, value) -> {
            logger.info("{}: {}", name, value);
        });
        
        logger.info("========================");
    }
    
    /**
     * Reset all metrics
     */
    public void reset() {
        metrics.clear();
        latencyTrackers.clear();
        throughputTrackers.clear();
        totalOperations.set(0);
        totalLatency.reset();
        startTime.set(System.nanoTime());
        
        logger.info("Performance metrics reset");
    }
    
    private void monitoringLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(30000); // Report every 30 seconds
                printReport();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in monitoring loop", e);
            }
        }
    }
    
    private ConcurrentHashMap<String, LatencyStats> getLatencyStats() {
        ConcurrentHashMap<String, LatencyStats> stats = new ConcurrentHashMap<>();
        latencyTrackers.forEach((op, tracker) -> {
            stats.put(op, tracker.getStats());
        });
        return stats;
    }
    
    private ConcurrentHashMap<String, ThroughputStats> getThroughputStats() {
        ConcurrentHashMap<String, ThroughputStats> stats = new ConcurrentHashMap<>();
        throughputTrackers.forEach((op, tracker) -> {
            stats.put(op, tracker.getStats());
        });
        return stats;
    }
    
    private ConcurrentHashMap<String, Double> getCustomMetrics() {
        ConcurrentHashMap<String, Double> custom = new ConcurrentHashMap<>();
        metrics.forEach((name, metric) -> {
            custom.put(name, metric.getValue());
        });
        return custom;
    }
    
    // Supporting classes
    public static class LatencyMeasurement implements AutoCloseable {
        private final String operation;
        private final long startTimeNanos;
        
        public LatencyMeasurement(String operation, long startTimeNanos) {
            this.operation = operation;
            this.startTimeNanos = startTimeNanos;
        }
        
        @Override
        public void close() {
            long latency = System.nanoTime() - startTimeNanos;
            getInstance().recordLatency(operation, latency);
        }
    }
    
    private static class Metric {
        private final String name;
        private final DoubleAdder value = new DoubleAdder();
        private final AtomicLong count = new AtomicLong(0);
        
        public Metric(String name) {
            this.name = name;
        }
        
        public void add(double val) {
            value.add(val);
            count.incrementAndGet();
        }
        
        public double getValue() {
            long c = count.get();
            return c > 0 ? value.sum() / c : 0.0;
        }
    }
    
    private static class LatencyTracker {
        private final AtomicLong count = new AtomicLong(0);
        private final DoubleAdder sum = new DoubleAdder();
        private volatile long[] samples = new long[10000]; // Circular buffer
        private volatile int index = 0;
        
        public synchronized void record(long latencyNanos) {
            count.incrementAndGet();
            sum.add(latencyNanos);
            
            samples[index] = latencyNanos;
            index = (index + 1) % samples.length;
        }
        
        public LatencyStats getStats() {
            long c = count.get();
            if (c == 0) {
                return new LatencyStats(0, 0, 0, 0, 0);
            }
            
            double avgMs = sum.sum() / c / 1_000_000.0;
            
            // Calculate percentiles from samples
            int sampleCount = Math.min((int)c, samples.length);
            long[] sortedSamples = new long[sampleCount];
            System.arraycopy(samples, 0, sortedSamples, 0, sampleCount);
            java.util.Arrays.sort(sortedSamples);
            
            double p50Ms = sortedSamples[sampleCount * 50 / 100] / 1_000_000.0;
            double p95Ms = sortedSamples[sampleCount * 95 / 100] / 1_000_000.0;
            double p99Ms = sortedSamples[sampleCount * 99 / 100] / 1_000_000.0;
            
            return new LatencyStats(c, avgMs, p50Ms, p95Ms, p99Ms);
        }
    }
    
    private static class ThroughputTracker {
        private final long[] timeBuckets = new long[60]; // 60 seconds of data
        private final long[] countBuckets = new long[60];
        private volatile int index = 0;
        private volatile long lastUpdateTime = System.currentTimeMillis() / 1000;
        
        public synchronized void record(int count) {
            long currentTime = System.currentTimeMillis() / 1000;
            
            // Advance buckets if needed
            while (currentTime > lastUpdateTime) {
                index = (index + 1) % 60;
                timeBuckets[index] = currentTime;
                countBuckets[index] = 0;
                lastUpdateTime++;
            }
            
            countBuckets[index] += count;
        }
        
        public ThroughputStats getStats() {
            long totalCount = 0;
            long oldestTime = Long.MAX_VALUE;
            
            for (int i = 0; i < 60; i++) {
                totalCount += countBuckets[i];
                if (timeBuckets[i] > 0 && timeBuckets[i] < oldestTime) {
                    oldestTime = timeBuckets[i];
                }
            }
            
            long timeWindow = System.currentTimeMillis() / 1000 - oldestTime;
            double eventsPerSecond = timeWindow > 0 ? (double)totalCount / timeWindow : 0.0;
            
            return new ThroughputStats(eventsPerSecond);
        }
    }
    
    // Data classes
    public static class PerformanceSummary {
        public final double uptimeSeconds;
        public final long operationsPerSecond;
        public final double avgLatencyMs;
        public final double memoryUsagePercent;
        public final int threadCount;
        public final ConcurrentHashMap<String, LatencyStats> latencyStats;
        public final ConcurrentHashMap<String, ThroughputStats> throughputStats;
        public final ConcurrentHashMap<String, Double> customMetrics;
        
        public PerformanceSummary(double uptimeSeconds, long operationsPerSecond, double avgLatencyMs,
                                double memoryUsagePercent, int threadCount,
                                ConcurrentHashMap<String, LatencyStats> latencyStats,
                                ConcurrentHashMap<String, ThroughputStats> throughputStats,
                                ConcurrentHashMap<String, Double> customMetrics) {
            this.uptimeSeconds = uptimeSeconds;
            this.operationsPerSecond = operationsPerSecond;
            this.avgLatencyMs = avgLatencyMs;
            this.memoryUsagePercent = memoryUsagePercent;
            this.threadCount = threadCount;
            this.latencyStats = latencyStats;
            this.throughputStats = throughputStats;
            this.customMetrics = customMetrics;
        }
    }
    
    public static class LatencyStats {
        public final long count;
        public final double avgMs;
        public final double p50Ms;
        public final double p95Ms;
        public final double p99Ms;
        
        public LatencyStats(long count, double avgMs, double p50Ms, double p95Ms, double p99Ms) {
            this.count = count;
            this.avgMs = avgMs;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
        }
    }
    
    public static class ThroughputStats {
        public final double eventsPerSecond;
        
        public ThroughputStats(double eventsPerSecond) {
            this.eventsPerSecond = eventsPerSecond;
        }
    }
}
