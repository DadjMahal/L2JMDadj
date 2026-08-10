package com.aiplayer.phase0.death;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Graveyard / respawn point data for a hunting zone.
 */
public final class Graveyard {
    public final String name;
    public final int x;
    public final int y;
    public final int z;
    public final int levelMin;
    public final int levelMax;

    public Graveyard(String name, int x, int y, int z, int levelMin, int levelMax) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.levelMin = levelMin;
        this.levelMax = levelMax;
    }

    public double distanceTo(int tx, int ty) {
        return Math.sqrt(Math.pow(tx - x, 2) + Math.pow(ty - y, 2));
    }

    @Override
    public String toString() {
        return String.format("Graveyard[%s @ %d,%d,%d]", name, x, y, z);
    }
}
