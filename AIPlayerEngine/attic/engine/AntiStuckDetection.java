// package com.aiplayer.engine;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.logging.Logger;
public class AntiStuckDetection {
    private static final Logger LOGGER = Logger.getLogger(AntiStuckDetection.class.getName());
    private Deque<String> pathHistory = new ArrayDeque<>();
    public void recordPosition(String pos) { pathHistory.addLast(pos); if (pathHistory.size() > 10) pathHistory.removeFirst(); }
    public boolean isStuck() { return new HashSet<>(pathHistory).size() == 1; }
    public String suggestUnstuck() { return "use_unstuck_item"; }
}
