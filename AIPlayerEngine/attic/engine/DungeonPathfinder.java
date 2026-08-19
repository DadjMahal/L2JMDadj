// package com.aiplayer.engine;
import java.util.logging.Logger;
public class DungeonPathfinder {
    private static final Logger LOGGER = Logger.getLogger(DungeonPathfinder.class.getName());
    private String[] waypoints;
    public DungeonPathfinder(String[] waypoints) { this.waypoints = waypoints; }
    public String[] getPath() { return waypoints; }
    public boolean isSafe(int x, int y) { return true; }
}
