package com.aiplayer.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aiplayer.knowledge.GearGuide;
import com.aiplayer.knowledge.PlayerRace;
import com.aiplayer.knowledge.QuestNode;
import com.aiplayer.knowledge.RaceGuide;

/**
 * MODE: COMPLETE. Pure, static, thread-safe helper that turns an over-full inventory into a
 * concrete shop trip: which town vendor landmark to walk to (a real in-world coordinate) and
 * what to buy once there. No IO, no sockets, no shared mutable state — deterministic for a fixed
 * input, so the decision ladder can call it identically every tick.
 *
 * <p>The caller (BotPlayController RESTOCK branch) gates on {@code inventoryPct}; this helper
 * only decides the destination and the buy list. Consumable ids are documented Interlude ids
 * (1835 Soulshot, 1061 Healing Potion). GK-8 replaced the old hardcoded gear-upgrade placeholder
 * with a real recommendation: the caller asks {@link GearGuide} (items+shops+chains knowledge)
 * for the bot's next weapon and passes the {@link GearGuide.GearPick} in; null simply adds no
 * gear order.
 */
public final class RestockPlanner
{
    /** The standard Interlude Soulshot (itemId 1835) — the everyday farming ammo. */
    private static final int SOULSHOT_ITEM_ID = 1835;
    /** Healing Potion (itemId 1061) — cheap HP sustain for leveling. */
    private static final int HP_POTION_ITEM_ID = 1061;

    private static final int SOULSHOT_BASE_QTY = 200;
    private static final int SOULSHOT_PER_LEVEL = 10;
    private static final int SOULSHOT_MAX_QTY = 1000;
    private static final int HP_BASE_QTY = 10;
    private static final int HP_PER_LEVEL = 1;
    private static final int HP_MAX_QTY = 50;

    private RestockPlanner()
    {
    }

    /** One line-item to buy: a stable item id, the quantity, and a human label. */
    public static final class BuyOrder
    {
        public final int itemId;
        public final int qty;
        public final String label;

        public BuyOrder(int itemId, int qty, String label)
        {
            this.itemId = itemId;
            this.qty = Math.max(1, qty);
            this.label = label != null ? label : "item" + itemId;
        }
    }

    /** A decided shop trip: the vendor landmark to walk to and the ordered buy list. */
    public static final class RestockPlan
    {
        public final int vendorX;
        public final int vendorY;
        public final int vendorZ;
        public final List<BuyOrder> orders;

        public RestockPlan(int vendorX, int vendorY, int vendorZ, List<BuyOrder> orders)
        {
            this.vendorX = vendorX;
            this.vendorY = vendorY;
            this.vendorZ = vendorZ;
            this.orders = orders != null
                ? Collections.unmodifiableList(new ArrayList<>(orders))
                : Collections.emptyList();
        }
    }

    /**
     * Decide a sensible shop trip for a bot. {@code inventoryPct} is respected here only as a
     * documented input (the caller already gates on it); the destination comes from
     * {@link RaceGuide#idleAnchor} and the buy list is scaled to level and (optionally) coins.
     */
    public static RestockPlan plan(int level, int inventoryPct, int coins, PlayerRace race)
    {
        return plan(level, inventoryPct, coins, race, true);
    }

    /** S7-T05: same plan but class-aware — fighters restock MORE HP potions than mystics. */
    public static RestockPlan plan(int level, int inventoryPct, int coins, PlayerRace race, boolean isFighter)
    {
        return plan(level, inventoryPct, coins, race, isFighter, 0, 0);
    }

    /**
     * EB-06: shortage-aware plan — the caller (the ladder via {@code RestockDecider}) decides the
     * intent AND the missing quantities; this builds the actual order line-items to top off.
     * {@code soulshotShort} / {@code hpShort} are the quantities still missing (RestockDecider.shortage).
     * When both are 0, exactly the historical base plan is returned so old behaviour is unchanged.
     */
    public static RestockPlan plan(int level, int inventoryPct, int coins, PlayerRace race,
                                   boolean isFighter, int soulshotShort, int hpShort)
    {
        return plan(level, inventoryPct, coins, race, isFighter, soulshotShort, hpShort, null);
    }

    /**
     * GK-8: gear-aware plan — same as the shortage-aware plan, plus an optional weapon
     * recommendation from {@link GearGuide} (already budget-checked by the recommender).
     * A null pick adds no gear order; the pick's id/label flow straight into the buy list.
     */
    public static RestockPlan plan(int level, int inventoryPct, int coins, PlayerRace race,
                                   boolean isFighter, int soulshotShort, int hpShort,
                                   GearGuide.GearPick gear)
    {
        PlayerRace r = race != null ? race : PlayerRace.HUMAN;
        QuestNode anchor = RaceGuide.idleAnchor(r, level);

        int ss = soulshotShort > 0
            ? soulshotShort
            : Math.min(SOULSHOT_MAX_QTY, SOULSHOT_BASE_QTY + level * SOULSHOT_PER_LEVEL);
        int hp = hpShort > 0
            ? hpShort
            : potionsFor(level, coins, isFighter);

        List<BuyOrder> orders = new ArrayList<>();
        orders.add(new BuyOrder(SOULSHOT_ITEM_ID, ss, "soulshots"));
        orders.add(new BuyOrder(HP_POTION_ITEM_ID, hp, "hp potions"));
        if (gear != null)
        {
            orders.add(new BuyOrder(gear.itemId, 1,
                "gear upgrade: " + gear.name + " (" + gear.grade + "-grade " + gear.weaponType + ")"));
        }
        return new RestockPlan(anchor.x, anchor.y, anchor.z, orders);
    }

    /** S7-T05: class-aware HP-pot restock qty — fighters (melee, HP-squishy) restock more than mystics. */
    public static int potionsFor(int level, int coins, boolean isFighter)
    {
        int base = isFighter ? HP_BASE_QTY : Math.max(1, HP_BASE_QTY - 2);
        int perLevel = isFighter ? HP_PER_LEVEL : Math.max(1, HP_PER_LEVEL - 1);
        return Math.min(HP_MAX_QTY, base + level * perLevel);
    }
}
