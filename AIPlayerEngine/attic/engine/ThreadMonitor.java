// package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ThreadMonitor {
    private static final Logger LOGGER = Logger.getLogger(ThreadMonitor.class.getName());
    private final Map<String, Long> threadTimes = new HashMap<>();
    private final Map<String, Integer> executionCounts = new HashMap<>();

    public void recordExecution(String taskName, long millis) {
        threadTimes.merge(taskName, millis, Long::sum);
        executionCounts.merge(taskName, 1, Integer::sum);
    }

    public double getAverageTime(String taskName) {
        int count = executionCounts.getOrDefault(taskName, 1);
        long total = threadTimes.getOrDefault(taskName, 0L);
        return (double) total / count;
    }

    public void logPerformanceReport() {
        executionCounts.forEach((task, count) ->
            LOGGER.info(task + ": avg=" + getAverageTime(task) + "ms, count=" + count));
    }
}
