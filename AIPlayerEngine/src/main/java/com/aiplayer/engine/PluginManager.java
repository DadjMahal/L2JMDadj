package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class PluginManager {
    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());
    private final List<String> loadedPlugins = new ArrayList<>();
    
    public void loadPlugin(String pluginName) {
        loadedPlugins.add(pluginName);
        LOGGER.info("Plugin loaded: " + pluginName);
    }
    
    public boolean isPluginActive(String pluginName) {
        return loadedPlugins.contains(pluginName);
    }
    
    public String[] getActivePlugins() {
        return loadedPlugins.toArray(new String[0]);
    }
}
