package com.aiplayer.engine;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

public class RiftAI {
    private static final Logger LOGGER = Logger.getLogger(RiftAI.class.getName());
    
    public static class RiftRoom { public final int roomId; public final int level; public RiftRoom(int r, int l) { roomId = r; level = l; } }
    
    public RiftRoom selectOptimalRoom(int playerLevel, RiftRoom[] available) {
        return Arrays.stream(available).min(Comparator.comparing(r -> Math.abs(r.level - playerLevel))).orElse(available[0]);
    }
    
    public String getTeleportCommand(String riftName) { return "teleport " + riftName; }
}
