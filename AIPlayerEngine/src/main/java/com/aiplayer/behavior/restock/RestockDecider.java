package com.aiplayer.behavior.restock;

/**
 * MODE: COMPLETE. EB-06 — the pure RESTOCK INTENT decision module (behavior/restock) that feeds
 * {@code RestockPlanner}. Unlike the old binary "inventoryPct >= threshold" gate, this decides
 * WHY the bot should visit a vendor, from the real consumable counts the fleet loop reads:
 *
 * <ul>
 *   <li>soulshots low (or empty) → ammo shortage,</li>
 *   <li>HP potions low → sustain shortage,</li>
 *   <li>inventory full → storage/pack problem,</li>
 *   <li>combinations escalate to URGENT (both ammo AND full).</li>
 * </ul>
 *
 * <p>Pure and deterministic: no IO, no sockets, no threads. {@link #shortage(int, int)} gives the
 * exact top-off quantities the shop trip should buy, which {@code RestockPlanner} turns into
 * order line items.
 */
public final class RestockDecider
{
    private RestockDecider()
    {
    }

    /** Below this many soulshots in the bag a restock trip is worth it (mirrors SoulshotRestocker). */
    public static final int SOULSHOT_RESTOCK_AT = 500;
    /** Below this many HP potions a restock trip is worth it. */
    public static final int HP_POTION_RESTOCK_AT = 10;
    /** Inventory usage % at which the bag is considered full enough to warrant a trip. */
    public static final int INVENTORY_FULL_AT = 85;
    /** Restock aim for soulshots (was SoulshotRestocker.RESTOCK_TARGET). */
    public static final int SOULSHOT_TARGET = 2000;
    /** How many soulshots an URGENT trip buys toward (same 2k target). */
    public static final int URGENT_SHOT_TARGET = 2000;
    /** Potions buy to ~this many × the low mark. */
    public static final int POTION_TARGET_MULT = 2;

    /** Why the bot should restock. */
    public enum Reason
    {
        /** No shortage — keep farming. */
        NONE,
        /** Soul-shots are low (farming ammo). */
        SOULSHOTS,
        /** HP potions are low (sustain). */
        POTIONS,
        /** Inventory is too full to keep looting. */
        FULL,
        /** Full bag AND low ammo/potions — restock immediately. */
        URGENT
    }

    /** A decided restock request: the reason plus exact quantities still missing. */
    public static final class Verdict
    {
        public final Reason reason;
        /** Soulshot shortage to top back toward the target (>= 0; 0 when not the driver). */
        public final int soulshotShort;
        /** HP-potion shortage to top back toward the target (>= 0; 0 when not the driver). */
        public final int hpPotionShort;

        private Verdict(Reason reason, int soulshotShort, int hpPotionShort)
        {
            this.reason = reason;
            this.soulshotShort = soulshotShort;
            this.hpPotionShort = hpPotionShort;
        }

        public boolean shouldRestock()
        {
            return reason != Reason.NONE;
        }
    }

    /**
     * Decide whether / why this tick's inventory warrants a shop trip (default thresholds).
     *
     * @param soulshotCount current soulshot stack (or -1 when unknown → treated as NOT low)
     * @param hpPotionCount current HP potion stack (or -1 when unknown → treated as NOT low)
     * @param inventoryPct  inventory usage 0..100 (or -1 when unknown → treated as NOT full)
     */
    public static Verdict decide(int soulshotCount, int hpPotionCount, int inventoryPct)
    {
        return decide(soulshotCount, hpPotionCount, inventoryPct,
            SOULSHOT_RESTOCK_AT, HP_POTION_RESTOCK_AT, INVENTORY_FULL_AT);
    }

    /**
     * Core decision with explicit thresholds (tests + the decision ladder pass the profile's own
     * restock knobs so a MERCHANT personality restocks earlier).
     */
    public static Verdict decide(int soulshotCount, int hpPotionCount, int inventoryPct,
                                 int soulshotAt, int hpAt, int fullAt)
    {
        boolean lowShot = soulshotCount >= 0 && soulshotCount < soulshotAt;
        boolean lowPot = hpPotionCount >= 0 && hpPotionCount < hpAt;
        boolean full = inventoryPct >= 0 && inventoryPct >= fullAt;
        boolean ammo = lowShot || lowPot;

        if (full && ammo)
        {
            return new Verdict(Reason.URGENT,
                lowShot ? shortage(soulshotCount, URGENT_SHOT_TARGET) : 0,
                lowPot ? shortage(hpPotionCount, hpAt * POTION_TARGET_MULT) : 0);
        }
        if (full)
        {
            return new Verdict(Reason.FULL, 0, 0);
        }
        if (lowShot)
        {
            return new Verdict(Reason.SOULSHOTS, shortage(soulshotCount, SOULSHOT_TARGET), 0);
        }
        if (lowPot)
        {
            return new Verdict(Reason.POTIONS, 0, shortage(hpPotionCount, hpAt * POTION_TARGET_MULT));
        }
        return new Verdict(Reason.NONE, 0, 0);
    }

    /** How many units are still missing to reach {@code target} (never negative). */
    public static int shortage(int current, int target)
    {
        if (current <= 0)
        {
            return Math.max(0, target);
        }
        return Math.max(0, target - current);
    }
}