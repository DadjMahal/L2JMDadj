package com.aiplayer.engine;
import java.util.logging.Logger;
import java.lang.management.ManagementFactory;

public class AIDiagnostics {
    private static final Logger LOGGER = Logger.getLogger(AIDiagnostics.class.getName());

    public static int getMemoryUsageMB() {
        Runtime r = Runtime.getRuntime();
        return (int) ((r.totalMemory() - r.freeMemory()) / 1048576);
    }

    public static double getCpuLoad() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage() / Runtime.getRuntime().availableProcessors();
    }

    public static boolean isOverloaded() {
        return getMemoryUsageMB() > 500 || getCpuLoad() > 0.8;
    }
}
