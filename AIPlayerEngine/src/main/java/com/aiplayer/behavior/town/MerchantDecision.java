package com.aiplayer.behavior.town;

/**
 * Merchant Decision Result
 * Contains the decision about what merchant action to take
 */
public class MerchantDecision {
    public enum Action {
        IDLE,
        BUY_ITEM,
        SELL_ITEM,
        INTERACT_MERCHANT,
        FIND_MERCHANT,
        EMERGENCY_SELL,
        ARBITRAGE,
        BULK_BUY,
        RESTOCK
    }

    private final Action action;
    private final String itemId;
    private final int count;
    private final int price;
    private final MerchantNPC merchant;
    private final boolean shouldExecute;
    private final long timestamp;

    private MerchantDecision(Action action, String itemId, int count, int price,
                            MerchantNPC merchant, boolean shouldExecute) {
        this.action = action;
        this.itemId = itemId;
        this.count = count;
        this.price = price;
        this.merchant = merchant;
        this.shouldExecute = shouldExecute;
        this.timestamp = System.currentTimeMillis();
    }

    // Factory methods for different decisions
    public static MerchantDecision idle() {
        return new MerchantDecision(Action.IDLE, null, 0, 0, null, false);
    }

    public static MerchantDecision sellItem(String itemId, int count, int price) {
        return new MerchantDecision(Action.SELL_ITEM, itemId, count, price, null, true);
    }

    public static MerchantDecision buyItem(String itemId, int count, int price) {
        return new MerchantDecision(Action.BUY_ITEM, itemId, count, price, null, true);
    }

    public static MerchantDecision interact(MerchantNPC merchant) {
        return new MerchantDecision(Action.INTERACT_MERCHANT, null, 0, 0, merchant, true);
    }

    public static MerchantDecision findMerchant(String npcId, int x, int y, int z) {
        MerchantNPC merchant = new MerchantNPC(npcId, x, y, z);
        return new MerchantDecision(Action.FIND_MERCHANT, null, 0, 0, merchant, true);
    }

    public static MerchantDecision emergencySell() {
        return new MerchantDecision(Action.EMERGENCY_SELL, "ANY_VALUABLE", 10, 0, null, true);
    }

    public static MerchantDecision arbitrage(String itemId, String buyMerchant, String sellMerchant) {
        return new MerchantDecision(Action.ARBITRAGE, itemId, 0, 0, null, true);
    }

    public static MerchantDecision buyBulk(String[] items, int count) {
        return new MerchantDecision(Action.BULK_BUY, String.join(",", items), count, 0, null, true);
    }

    // Getters
    public Action getAction() { return action; }
    public String getItemId() { return itemId; }
    public int getCount() { return count; }
    public int getPrice() { return price; }
    public MerchantNPC getMerchant() { return merchant; }
    public boolean shouldExecute() { return shouldExecute; }
    public long getTimestamp() { return timestamp; }
}
