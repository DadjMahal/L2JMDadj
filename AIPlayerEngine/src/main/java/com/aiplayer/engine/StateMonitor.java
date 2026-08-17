package com.aiplayer.engine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class StateMonitor {
    private static final Logger LOGGER = Logger.getLogger(StateMonitor.class.getName());
    private final Map<String, Object> state = new HashMap<>();
    private final List<String> changes = new ArrayList<>();

    public void update(String key, Object value) {
        Object old = state.put(key, value);
        changes.add("UPDATE " + key + ": " + old + " -> " + value);
    }

    public Object get(String key) { return state.get(key); }
    public List<String> getChanges() { return new ArrayList<>(changes); }
    public void clearChanges() { changes.clear(); }

    public String getHealthReport() {
        return "STATE: " + state.size() + " keys, " + changes.size() + " changes";
    }
}
