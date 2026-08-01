package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * Task 98: Safe-zone awareness - know where PvP is forbidden
 *
 * Tracks safe zones (towns, GvS areas, castle gates, etc.)
 * Used to prevent illegal PK and for movement planning
 */
public class SafeZoneManager {
    private static final Logger LOGGER = Logger.getLogger(SafeZoneManager.class.getName());
    
    // Safe zone types
    public enum ZoneType {
        TOWN, GVS_GATE, CASTLE_GATE, ESCORT_START, MONSTER_TRACK, OTHER
    }
    
    // Safe zone definition
    public static class SafeZone {
        public final int minX, minY, maxX, maxY;
        public final ZoneType type;
        public final boolean isPvPForbidden;
        
        public SafeZone(int minX, int minY, int maxX, int maxY, ZoneType type) {
            this.minX = minX; this.minY = minY;
            this.maxX = maxX; this.maxY = maxY;
            this.type = type;
            this.isPvPForbidden = (type == ZoneType.TOWN || type == ZoneType.GVS_GATE);
        }
        
        public boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
        
        @Override
        public String toString() {
            return "SafeZone{" + type + "=[" + minX + "," + minY + "-" + maxX + "," + maxY + "]}";
        }
    }
    
    private static SafeZone[] safeZones;
    
    static {
        initializeDefaultZones();
    }
    
    private static void initializeDefaultZones() {
        safeZones = new SafeZone[] {
            // Gludio town
            new SafeZone(-1850, -1750, -1550, -1450, ZoneType.TOWN),
            // Gludin town  
            new SafeZone(-8600, -20200, -8100, -19700, ZoneType.TOWN),
            // Giran town
            new SafeZone(-12300, -8200, -10200, -5900, ZoneType.TOWN),
            // Heine town
            new SafeZone(11600, -8300, 13300, -6400, ZoneType.TOWN),
            // Dion town
            new SafeZone(27500, -16700, 29700, -15100, ZoneType.TOWN),
            // GvS gates (example)
            new SafeZone(-14000, -13000, -12000, -11000, ZoneType.GVS_GATE),
            // Castle gates
            new SafeZone(14500, -17300, 15500, -16500, ZoneType.CASTLE_GATE)
        };
        LOGGER.info("SafeZoneManager: Initialized " + safeZones.length + " safe zones");
    }
    
    /**
     * Check if coordinates are in a safe zone
     */
    public static boolean isInSafeZone(int x, int y) {
        for (SafeZone zone : safeZones) {
            if (zone.contains(x, y)) return true;
        }
        return false;
    }
    
    /**
     * Get the safe zone at given coordinates, or null if not in safe zone
     */
    public static SafeZone getSafeZoneAt(int x, int y) {
        for (SafeZone zone : safeZones) {
            if (zone.contains(x, y)) return zone;
        }
        return null;
    }
    
    /**
     * Check if PvP is forbidden at given coordinates
     */
    public static boolean isPvPForbidden(int x, int y) {
        SafeZone zone = getSafeZoneAt(x, y);
        return zone != null && zone.isPvPForbidden;
    }
    
    /**
     * Add a new safe zone
     */
    public static void addSafeZone(SafeZone zone) {
        SafeZone[] newZones = new SafeZone[safeZones.length + 1];
        System.arraycopy(safeZones, 0, newZones, 0, safeZones.length);
        newZones[newZones.length - 1] = zone;
        safeZones = newZones;
        LOGGER.info("SafeZoneManager: Added " + zone);
    }
    
    // Demo test
    public static void main(String[] args) {
        System.out.println("=== Safe Zone Manager Demo ===");
        System.out.println("Giran center (-11000, -7000): " + getSafeZoneAt(-11000, -7000));
        System.out.println("Wilderness (50000, 50000): " + getSafeZoneAt(50000, 50000));
        System.out.println("PvP forbidden at town: " + isPvPForbidden(-11000, -7000));
        System.out.println("PvP forbidden in wild: " + isPvPForbidden(50000, 50000));
    }
}