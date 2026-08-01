package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class CastleManagement {
    private static final Logger LOGGER = Logger.getLogger(CastleManagement.class.getName());
    
    public enum DoorState { OPEN, CLOSED, LOCKED, DAMAGED }
    public enum TeleportState { ACTIVE, INACTIVE, REPAIRING }
    
    public static class DoorControl {
        public final int doorId;
        public DoorState state;
        public long lastRepair;
        
        public DoorControl(int id) { this.doorId = id; this.state = DoorState.OPEN; this.lastRepair = System.currentTimeMillis(); }
        public boolean needsRepair() { return state == DoorState.DAMAGED || (System.currentTimeMillis() - lastRepair) > 3600000; }
        public void repair() { state = DoorState.OPEN; lastRepair = System.currentTimeMillis(); }
    }
    
    public static class TeleporterControl {
        public final int teleporterId;
        public TeleportState state;
        public int usageCount;
        
        public TeleporterControl(int id) { this.teleporterId = id; this.state = TeleportState.ACTIVE; this.usageCount = 0; }
        public void use() { if (state == TeleportState.ACTIVE) usageCount++; }
        public void deactivate() { state = TeleportState.INACTIVE; }
    }
    
    public static boolean shouldOpenDoor(DoorControl door, boolean isOwner, boolean isSiegeActive) {
        if (!isOwner) return false;
        if (door.state == DoorState.OPEN) return false;
        return true;
    }
    
    public static boolean canTeleport(String location, TeleportState state) {
        return state == TeleportState.ACTIVE;
    }
}
