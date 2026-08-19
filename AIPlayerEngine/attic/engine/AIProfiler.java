// package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class AIProfiler {
    private static final Logger LOGGER = Logger.getLogger(AIProfiler.class.getName());
    private final Map<String, Integer> metricCounters = new HashMap<>();

    public void increment(String metric) {
        metricCounters.merge(metric, 1, Integer::sum);
    }

    public int getCount(String metric) {
        return metricCounters.getOrDefault(metric, 0);
    }

    public Set<String> getMetrics() { return metricCounters.keySet(); }

    public String getReport() {
        return "AIProfiler: " + metricCounters.size() + " metrics tracked";
    }
}
