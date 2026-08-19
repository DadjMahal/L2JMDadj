package com.aiplayer.monitor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.aiplayer.net.AIPlayer;
import com.aiplayer.core.AIPlayerManager;

/**
 * AI Player Monitoring Dashboard
 * Collects real-time stats on all AI players
 */
public class AIMonitorDashboard {
    private static final Logger LOGGER = Logger.getLogger(AIMonitorDashboard.class.getName());
    private static final AIMonitorDashboard INSTANCE = new AIMonitorDashboard();

    private final Map<Integer, PlayerStats> playerStats = new ConcurrentHashMap<>();
    private final AIPlayerManager manager;
    private volatile boolean monitoring = false;

    private AIMonitorDashboard() {
        this.manager = AIPlayerManager.getInstance();
    }

    public static AIMonitorDashboard getInstance() {
        return INSTANCE;
    }

    /**
     * Start monitoring all AI players
     */
    public void start() {
        if (monitoring) {
            LOGGER.warning("Monitor already running");
            return;
        }
        monitoring = true;
        LOGGER.info("AI Monitor Dashboard started");
    }

    /**
     * Stop monitoring
     */
    public void stop() {
        monitoring = false;
        LOGGER.info("AI Monitor Dashboard stopped");
    }

    /**
     * Update stats for an AI player
     */
    public void updatePlayerStats(AIPlayer player) {
        if (player == null) return;

        PlayerStats stats = playerStats.computeIfAbsent(player.getAccountId(),
            id -> new PlayerStats(player.getName()));

        stats.lastUpdate = System.currentTimeMillis();
        stats.level = player.getLevel();
        stats.state = player.getAIState();
        stats.connected = player.isConnected();
        stats.loggedIn = player.isLoggedIn();
    }

    /**
     * Get stats for all AI players
     */
    public Map<Integer, PlayerStats> getStats() {
        return playerStats;
    }

    /**
     * Get player by name
     */
    public PlayerStats getPlayerStats(String playerName) {
        return playerStats.values().stream()
            .filter(s -> s.name.equals(playerName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Generate a monitoring report
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== AI PLAYER MONITORING REPORT ===\n");
        report.append("Generated: ").append(new java.util.Date()).append("\n\n");

        int online = 0;
        int offline = 0;
        int totalLevel = 0;

        report.append(String.format("%-20s %-8s %-8s %-8s %-8s%n",
            "Player", "Level", "State", "Connected", "Online"));
        report.append("-".repeat(56)).append("\n");

        for (PlayerStats stats : playerStats.values()) {
            String status = stats.connected && stats.loggedIn ? "ONLINE" : "OFFLINE";
            if (stats.connected && stats.loggedIn) {
                online++;
            } else {
                offline++;
            }
            totalLevel += stats.level;

            report.append(String.format("%-20s %-8d %-8s %-8s %-8s%n",
                stats.name, stats.level, stats.state,
                stats.connected ? "YES" : "NO",
                status));
        }

        int total = playerStats.size();
        double avgLevel = total > 0 ? (double) totalLevel / total : 0;

        report.append("-".repeat(56)).append("\n");
        report.append(String.format("\nTotal Players: %d\n", total));
        report.append(String.format("Online: %d\n", online));
        report.append(String.format("Offline: %d\n", offline));
        report.append(String.format("Average Level: %.2f\n", avgLevel));

        return report.toString();
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    /**
     * Player statistics holder
     */
    public static class PlayerStats {
        public final String name;
        public volatile int level;
        public volatile String state;
        public volatile boolean connected;
        public volatile boolean loggedIn;
        public volatile long lastUpdate;

        public PlayerStats(String name) {
            this.name = name;
            this.level = 1;
            this.state = "OFFLINE";
            this.connected = false;
            this.loggedIn = false;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
}