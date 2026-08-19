// package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ExternalAPI {
    private static final Logger LOGGER = Logger.getLogger(ExternalAPI.class.getName());
    private final Map<String, String[]> apiEndpoints = new HashMap<>();

    public void registerEndpoint(String name, String[] methods) {
        apiEndpoints.put(name, methods);
    }

    public boolean canCall(String endpoint, String method) {
        String[] allowed = apiEndpoints.get(endpoint);
        if (allowed == null) return false;
        for (String m : allowed) {
            if (m.equalsIgnoreCase(method)) return true;
        }
        return false;
    }

    public String[] getStateSnapshot() {
        return new String[]{"STATE_OK", "LEVEL_214", "ALL_TASKS_COMPLETE"};
    }
}
