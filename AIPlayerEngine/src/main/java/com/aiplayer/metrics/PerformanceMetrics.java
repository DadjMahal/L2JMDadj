package com.aiplayer.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Performance Metrics Tracker
 * Tracks actions/sec and decision latency for AI players
 */
public class PerformanceMetrics {
    private static final Logger LOGGER = Logger.getLogger(PerformanceMetrics.class.getName());
    
    // Singleton instance
    private static PerformanceMetrics instance;
    
    // Action counters
    private final AtomicLong totalActions = new AtomicLong(0);
    private final AtomicLong decisionCount = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private long sessionStartTime;
    
    // Per-player metrics
    private final AtomicLong playerActions = new AtomicLong(0);
    private final AtomicLong playerDecisionCount = new AtomicLong(0);
    private final AtomicLong playerTotalLatency = new AtomicLong(0);
    private long playerSessionStart;
    
    private PerformanceMetrics() {
        this.sessionStartTime = System.currentTimeMillis();
        this.playerSessionStart = sessionStartTime;
    }
    
    public static PerformanceMetrics getInstance() {
        if (instance == null) {
            synchronized (PerformanceMetrics.class) {
                if (instance == null) {
                    instance = new PerformanceMetrics();
                }
            }
        }
        return instance;
    }
    
    /** Record an action with optional latency */
    public void recordAction(String player, long latencyNanos) {
        totalActions.incrementAndGet();
        decisionCount.incrementAndGet();
        totalLatencyNanos.addAndGet(latencyNanos);
        
        playerActions.incrementAndGet();
        playerDecisionCount.incrementAndGet();
        playerTotalLatency.addAndGet(latencyNanos);
        
        LOGGER.info("[PERFORMANCE] [" + player + "] ACTION: latency=" + (latencyNanos / 1_000_000) + "ms");
    }
    
    /** Record a simple action without latency (e.g., idle, movement) */
    public void recordAction(String player) {
        recordAction(player, 0);
    }
    
    /** Get current actions per second */
    public double getActionsPerSecond() {
        long elapsedMs = System.currentTimeMillis() - sessionStartTime;
        if (elapsedMs == 0) return 0;
        return (totalActions.get() * 1000.0) / elapsedMs;
    }
    
    /** Get average decision latency in milliseconds */
    public double getAverageDecisionLatency() {
        long decisions = decisionCount.get();
        if (decisions == 0) return 0;
        return totalLatencyNanos.get() / (1_000_000.0 * decisions);
    }
    
    /** Get player-specific actions per second */
    public double getPlayerActionsPerSecond(String player) {
        long elapsedMs = System.currentTimeMillis() - playerSessionStart;
        if (elapsedMs == 0) return 0;
        return (playerActions.get() * 1000.0) / elapsedMs;
    }
    
    /** Get player-specific average latency */
    public double getPlayerAverageLatency(String player) {
        long decisions = playerDecisionCount.get();
        if (decisions == 0) return 0;
        return playerTotalLatency.get() / (1_000_000.0 * decisions);
    }
    
    /** Log metrics summary */
    public void logMetricsSummary(String playerName) {
        long totalDecisions = decisionCount.get();
        double avgLatency = getAverageDecisionLatency();
        double actionsPerSec = getActionsPerSecond();
        
        LOGGER.info("[METRICS>Summary] [" + playerName + "] total_actions=" + totalActions.get() + 
                   " actions/sec=" + String.format("%.2f", actionsPerSec) +
                   " avg_latency=" + String.format("%.2f", avgLatency) + "ms decisions=" + totalDecisions);
    }
    
    public long getTotalActions() { return totalActions.get(); }
    public long getTotalDecisions() { return decisionCount.get(); }
    public long getSessionStart() { return sessionStartTime; }
    
    /** Reset all metrics */
    public void reset() {
        totalActions.set(0);
        decisionCount.set(0);
        totalLatencyNanos.set(0);
        playerActions.set(0);
        playerDecisionCount.set(0);
        playerTotalLatency.set(0);
        sessionStartTime = System.currentTimeMillis();
        playerSessionStart = sessionStartTime;
    }
}