package com.aiplayer.phase0.play;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aiplayer.phase0.guide.PlayerRace;
import com.aiplayer.phase0.guide.QuestNode;
import com.aiplayer.phase0.guide.RaceGuide;

/**
 * MODE: COMPLETE. Pure, static, thread-safe helper that turns an over-full inventory into a
 * concrete shop trip: which town vendor landmark to walk to (a real in-world coordinate) and
 * what to buy once there. No IO, no sockets, no shared mutable state — deterministic for a fixed
 * input, so the decision ladder can call it identically every tick.
 *
 * <p>The caller (BotPlayController RESTOCK branch) gates on {@code inventoryPct}; this helper
 * only decides the destination and the buy list.
 *
 * <p>Buy item ids are documented Interlude item ids (1835 Soulshot, 1061 Healing Potion) and a
 * stable placeholder for a first gear-upgrade weapon; a gear upgrade order is added only once the
 * bot is past the first class-change level and rich enough to afford it.
 */
public final class RestockPlanner
{
    /** The standard Interlude Soulshot (itemId 1835) — the everyday farming ammo. */
    private static final int SOULSHOT_ITEM_ID = 1835;
    /** Healing Potion (itemId 1061) — cheap HP sustain for leveling. */
    private static final int HP_POTION_ITEM_ID = 1061;
    /** Stable placeholder id for a first weapon upgrade (document the real id where you swap it in). */
    private static final int GEAR_UPGRADE_ITEM_ID = 2375;

    /** Level at/above which a gear upgrade order may appear (just past first class change, Lv19+). */
    private static final int GEAR_UPGRADE_MIN_LEVEL = 20;
    /** Coin threshold to consider the gear upgrade affordable. */
    private static final int GEAR_UPGRADE_MIN_COINS = 50000;

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
        PlayerRace r = race != null ? race : PlayerRace.HUMAN;
        QuestNode anchor = RaceGuide.idleAnchor(r, level);

        int ss = Math.min(SOULSHOT_MAX_QTY, SOULSHOT_BASE_QTY + level * SOULSHOT_PER_LEVEL);
        int hp = potionsFor(level, coins, isFighter);

        List<BuyOrder> orders = new ArrayList<>();
        orders.add(new BuyOrder(SOULSHOT_ITEM_ID, ss, "soulshots"));
        orders.add(new BuyOrder(HP_POTION_ITEM_ID, hp, "hp potions"));
        if (level >= GEAR_UPGRADE_MIN_LEVEL && coins >= GEAR_UPGRADE_MIN_COINS)
        {
            orders.add(new BuyOrder(GEAR_UPGRADE_ITEM_ID, 1, "gear upgrade"));
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
