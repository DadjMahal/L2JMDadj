package com.aiplayer.monitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

/**
 * AI Player Log Collector
 * Collects and stores AI player activity logs to disk
 */
public class AILogCollector {
    private static final Logger LOGGER = Logger.getLogger(AILogCollector.class.getName());

    private final String logDir;
    private final SimpleDateFormat dateFormatter;

    public AILogCollector(String logDir) {
        this.logDir = logDir;
        this.dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        ensureLogDirExists();
    }

    private void ensureLogDirExists() {
        File dir = new File(logDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Log an AI player action
     */
    public void logAction(String playerName, String action, String details) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logLine = String.format("[%s] [%s] Action: %s | Details: %s%n",
            timestamp, playerName, action, details);
        appendLog("actions.log", logLine);
        LOGGER.fine(logLine.trim());
    }

    /**
     * Log AI player state changes
     */
    public void logState(String playerName, String oldState, String newState) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logLine = String.format("[%s] [%s] State Change: %s -> %s%n",
            timestamp, playerName, oldState, newState);
        appendLog("states.log", logLine);
    }

    /**
     * Log AI player errors
     */
    public void logError(String playerName, String error) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logLine = String.format("[%s] [%s] ERROR: %s%n",
            timestamp, playerName, error);
        appendLog("errors.log", logLine);
        LOGGER.severe("[" + playerName + "] " + error);
    }

    /**
     * Create a session report
     */
    public void createSessionReport(String playerName, String report) {
        String timestamp = dateFormatter.format(new Date());
        String filename = timestamp + "-" + playerName + "-session.txt";
        try {
            File file = new File(logDir, filename);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write("=== AI PLAYER SESSION REPORT ===\n");
                writer.write("Player: " + playerName + "\n");
                writer.write("Time: " + timestamp + "\n");
                writer.write("-----------------------------------\n");
                writer.write(report);
                writer.write("\n===================================\n");
            }
            LOGGER.info("Session report created: " + filename);
        } catch (IOException e) {
            LOGGER.warning("Failed to create session report: " + e.getMessage());
        }
    }

    private void appendLog(String logFile, String logLine) {
        try {
            File file = new File(logDir, logFile);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(logLine);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to write to log: " + logFile + " - " + e.getMessage());
        }
    }

    public SimpleDateFormat getDateFormatter() {
        return dateFormatter;
    }

    public String getLogDir() {
        return logDir;
    }
}