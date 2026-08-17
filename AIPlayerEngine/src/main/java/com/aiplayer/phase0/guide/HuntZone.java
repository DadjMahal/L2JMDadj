package com.aiplayer.phase0.guide;

/**
 * MODE: COMPLETE. A hunting / spot zone for the guide map.
 *
 * <p>Level band, an in-world anchor (matching the server town zone system) and the nearest town
 * used for teleport-leg planning. Data mirrors the existing {@code ZoneRecommender} catalogue and
 * the datapack town zones.
 */
public final class HuntZone
{
    public final String name;
    public final int minLevel;
    public final int maxLevel;
    public final int avgMobLevel;
    public final int x;
    public final int y;
    public final int z;
    public final int radius;
    public final String nearestTown;

    public HuntZone(String name, int minLevel, int maxLevel, int avgMobLevel, int x, int y, int z, int radius, String nearestTown)
    {
        this.name = name;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.avgMobLevel = avgMobLevel;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.nearestTown = nearestTown;
    }

    public boolean inBand(int level)
    {
        return level >= minLevel && level <= maxLevel;
    }

    @Override
    public String toString()
    {
        return name + " [" + minLevel + "-" + maxLevel + "] @ (" + x + "," + y + "," + z + ") via " + nearestTown;
    }
}
