package com.aiplayer.engine;
import java.util.logging.Logger;
public class MovementOptimizer {
    private static final Logger LOGGER = Logger.getLogger(MovementOptimizer.class.getName());
    public static String getShortestPath(int startX, int startY, int endX, int endY) { return "path:" + startX + "," + startY + "->" + endX + "," + endY; }
    public static int getPathDistance(int sX, int sY, int eX, int eY) { return Math.abs(sX-eX) + Math.abs(sY-eY); }
}
