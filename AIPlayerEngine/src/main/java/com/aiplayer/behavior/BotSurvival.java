package com.aiplayer.behavior;

/**
 * MODE: COMPLETE. EB-01 extraction — the per-tick survival/supply DECISIONS that used to live
 * inline in {@code core/BotSession} (FLEE/RETREAT direction, potion sipping, death/regen/
 * overwhelm guards). Pure logic: no IO, no packets, no threads — it answers "should I drink /
 * should I hold / where do I flee", and BotSession (the socket/lifecycle owner) executes the
 * decision through already-proven wiring + combat frames.
 */
import com.aiplayer.core.BotSnapshot;

public final class BotSurvival
{
    private BotSurvival()
    {
    }

    /** HP potion (item 1061) threshold and cooldown (were private statics in BotSession). */
    public static final int HP_POTION_ID = 1061;
    public static final long HP_POTION_COOLDOWN_MS = 20_000L;
    public static final double HP_POTION_USE_FRAC = 0.45;

    /** The per-tick survival-guard decision: should the bot hold/flee instead of re-engaging? */
    public static final class Guard
    {
        public final boolean active;
        public final String reason;

        private Guard(boolean active, String reason)
        {
            this.active = active;
            this.reason = reason;
        }
    }

    /**
     * S5-T10/S6-T03/T06/T07 survival guard — mirrors the inline boolean in BotSession:
     * death-loop hold, low-HP regen hold, or overwhelm cap (too many hostiles + low HP).
     */
    public static Guard survivalGuard(BotSnapshot s, long nowMs, long deathGuardUntilMs,
                                      long regenHoldUntilMs, int mobs, int surroundCap)
    {
        if (nowMs < deathGuardUntilMs)
        {
            return new Guard(true, "survival guard (death-loop hold)");
        }
        double frac = hpFrac(s);
        if (nowMs < regenHoldUntilMs && frac < 0.60)
        {
            return new Guard(true, "survival guard (regen hold)");
        }
        if (mobs > surroundCap && frac < 0.70)
        {
            return new Guard(true, "survival guard (overwhelm)");
        }
        return new Guard(false, "");
    }

    /** Current HP fraction (1.0 when unknown) — same formula FleetConfig used internally. */
    private static double hpFrac(BotSnapshot s)
    {
        return s.hpMax > 0 ? (double) s.hpCurrent / s.hpMax : 1.0;
    }

    /** Is an HP potion sufficiently low-HP and off-cooldown to drink right now? */
    public static boolean shouldSipPotion(BotSnapshot s, long lastUseMs, long nowMs)
    {
        return s.hpMax > 0
            && (double) s.hpCurrent / s.hpMax < HP_POTION_USE_FRAC
            && nowMs - lastUseMs > HP_POTION_COOLDOWN_MS;
    }

    /**
     * FLEE/RETREAT decision: the destination of a single break-away hop from (x,y,z) away from
     * the nearest hostile, doubling the displacement so the bot actually leaves melee range.
     * Pure math — mirrors the inline computation that was in BotSession's FLEE/RETREAT case.
     */
    public static final class FleeHop
    {
        public final int x, y, z;

        private FleeHop(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static FleeHop fleeHop(int x, int y, int z, int hostileX, int hostileY, int hostileZ)
    {
        return new FleeHop(x + (x - hostileX) * 2, y + (y - hostileY) * 2, z);
    }

    /** Find a stocked HP potion in the inventory records; null when none (or unknown). */
    public static Item findPotion(java.util.List<? extends Item> records)
    {
        if (records == null)
        {
            return null;
        }
        for (Item it : records)
        {
            if (it != null && it.getItemId() == HP_POTION_ID && it.getCount() > 0)
            {
                return it;
            }
        }
        return null;
    }

    /** Minimal inventory-view interface so this decision layer has no protocol dependency. */
    public interface Item
    {
        int getItemId();

        long getCount();

        int getObjectId();
    }
}