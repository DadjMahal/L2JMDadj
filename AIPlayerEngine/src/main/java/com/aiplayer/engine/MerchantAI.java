package com.aiplayer.engine;

import java.util.logging.Logger;

import com.aiplayer.protocol.PacketLogger;

/**
 * Merchant AI Module
 * Handles buying, selling, and trading behaviors for AI players
 * Integrates with L2JMobius trading, merchant, and market systems
 * Telemetry: PacketLogger tracks ItemList/CharInfo packets for inventory trading
 */
public class MerchantAI {
    private static final Logger LOGGER = Logger.getLogger(MerchantAI.class.getName());

    private final AIPlayer aiPlayer;
    private final MerchantConfig config;
    private PacketLogger packetLogger;

    public MerchantAI(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.config = MerchantConfig.getInstance();
        this.packetLogger = new PacketLogger(aiPlayer.getName());
    }

    /** Get the packet logger for telemetry. */
    public PacketLogger getPacketLogger() { return packetLogger; }

    /** Stream E (task 78): attach the LIVE reader's packet logger so trade decisions use real
     *  inventory/adena from parsed ItemList(0x1B) instead of an empty private buffer. */
    public void setPacketLogger(PacketLogger logger) {
        if (logger != null) this.packetLogger = logger;
    }

    /**
     * Main merchant decision method
     * Decides what action to take based on current inventory and market conditions
     */
    public MerchantDecision makeDecision() {
        // Check if we should be trading
        if (!config.isEnabled()) {
            return MerchantDecision.idle();
        }

        try {
            // Check inventory status
            int inventoryUsage = getInventoryUsagePercentage();
            int adena = getInventoryAdena();
            LOGGER.info("[TRADE-LOG] [" + aiPlayer.getName() + "] STATUS: inventory=" + inventoryUsage + "% adena=" + adena);

            // Decision logic
            if (inventoryUsage >= 90 && adena > 1000) {
                // Inventory almost full and have money - sell items
                return findItemToSell();
            }
            else if (inventoryUsage <= 30 && adena > config.getMinBuyAmount()) {
                // Low inventory and have enough adena - find items to buy
                return findItemToBuy();
            }
            else if (inventoryUsage >= 70 && adena < config.getMinSellAmount()) {
                // Pack full and low on adena - urgent sell
                return findItemToSell(true);
            }
            else if (adena < config.getMinEmergencyAmount()) {
                // Critical adena shortage - find any money item to sell
                return findEmergencySell();
            }

            // Check for nearby merchants
            MerchantNPC nearbyMerchant = findNearbyMerchant();
            if (nearbyMerchant != null) {
                return MerchantDecision.interact(nearbyMerchant);
            }

            // Visit profitable merchant
            return findProfitableMerchant();

        } catch (Exception e) {
            LOGGER.warning("Merchant AI error for " + aiPlayer.getName() + ": " + e.getMessage());
            return MerchantDecision.idle();
        }
    }

    private int getInventoryUsagePercentage() {
        // Stream E (task 78): real inventory usage from the attached (live) PacketLogger's parsed
        // ItemList(0x1B). Was `50 + Math.random()*30` mock — removed by Stream E.
        return packetLogger.getInventoryUsagePercent();
    }

    private int getInventoryAdena() {
        // Stream E (task 78): real adena (item id 57) parsed from ItemList(0x1B).
        // Was `10000 + Math.random()*50000` mock — removed by Stream E.
        return packetLogger.getAdena();
    }

    private MerchantDecision findItemToSell() {
        // Logic to find profitable items to sell
        // Would query: items with high sell price > buy price
        MerchantDecision decision = MerchantDecision.sellItem("COMMON_ITEM", 10, 5000);
        LOGGER.info("[TRADE-LOG] [" + aiPlayer.getName() + "] ITEM_SOLD: item=COMMON_ITEM count=10 price=5000");
        return decision;
    }

    private MerchantDecision findItemToBuy() {
        // Logic to find good buying opportunities
        // Would check: buy price < sell price at other merchants
        MerchantDecision decision = MerchantDecision.buyItem("BASIC_SUPPLY", 5, 1000);
        LOGGER.info("[TRADE-LOG] [" + aiPlayer.getName() + "] ITEM_BOUGHT: item=BASIC_SUPPLY count=5 price=1000");
        return decision;
    }

    private MerchantDecision findItemToSell(boolean emergency) {
        // Emergency mode - sell anything valuable
        MerchantDecision decision = MerchantDecision.sellItem("EMERGENCY_ITEM", 5, 3000);
        LOGGER.info("[TRADE-LOG] [" + aiPlayer.getName() + "] ITEM_SOLD(EMERGENCY): item=EMERGENCY_ITEM count=5 price=3000");
        return decision;
    }

    private MerchantDecision findEmergencySell() {
        // Critical situation - sell anything to get minimum adena
        MerchantDecision decision = MerchantDecision.emergencySell();
        LOGGER.info("[TRADE-LOG] [" + aiPlayer.getName() + "] EMERGENCY_SELL triggered");
        return decision;
    }

    private MerchantNPC findNearbyMerchant() {
        // Would search for nearby merchants using GeoEngine distance checks
        return null; // Placeholder
    }

    private MerchantDecision findProfitableMerchant() {
        // Find merchant with best prices
        // Would: calculate price differences, find optimal route
        return MerchantDecision.findMerchant("BEST_SELLER_NPC", 16600, 17000, 434);
    }

    /**
     * Track adena flow (economic impact) - logs all adena transactions
     */
    public void logAdenaFlow(String eventType, int oldAmount, int newAmount, String item, int quantity, int price) {
        int delta = newAmount - oldAmount;
        LOGGER.info("[ADENA_FLOW] [" + aiPlayer.getName() + "] " + eventType + " old=" + oldAmount + " new=" + newAmount + " delta=" + delta + " item=" + item + " qty=" + quantity + " price=" + price);
    }

    /**
     * Track price changes in the market
     */
    public void logPriceChange(String itemId, int oldPrice, int newPrice, String merchant) {
        int change = newPrice - oldPrice;
        String changeType = change >= 0 ? "INCREASE" : "DECREASE";
        LOGGER.info("[PRICE_CHANGE] [" + aiPlayer.getName() + "] " + changeType + " item=" + itemId + " old=" + oldPrice + " new=" + newPrice + " delta=" + change + " merchant=" + merchant);
    }

    /**
     * Economic impact summary for session
     */
    public void logEconomicSummary(int totalSpent, int totalEarned, int profitLost, int itemsTraded) {
        LOGGER.info("[ECONOMIC_SUMMARY] [" + aiPlayer.getName() + "] spent=" + totalSpent + " earned=" + totalEarned + " profit_loss=" + profitLost + " items=" + itemsTraded);
    }

    // --- Stream E (tasks 79, 86, 87): trade outcome feedback hooks ---
    // Mirror the Stream D pattern: before Stream E, trading had NO feedback into the economy /
    // emotion / reinforcement systems and MarketEngine was never fed real prices. The live
    // trade driver (TradeProbe B7 path) calls these after a real buy/sell.

    /** Record a real price observation (town, buy & sell prices) into MarketEngine for arbitrage. */
    public void recordPrice(String itemId, String town, long buyPrice, long sellPrice) {
        aiPlayer.getMarketEngine().recordPrice(itemId, town, buyPrice, sellPrice);
        LOGGER.info("[TRADE-LOG-MARKET] [" + aiPlayer.getName() + "] PRICE: item=" + itemId
                + " town=" + town + " buy=" + buyPrice + " sell=" + sellPrice
                + " bestSell=" + aiPlayer.getMarketEngine().findBestSellTown(itemId));
    }

    /** Called after a profitable trade: bump emotion + register a positive reinforcement signal. */
    public void onTradeProfit(String itemId, String action, long adenaProfit) {
        aiPlayer.getEmotions().onProfitableTrade();          // excitement + confidence up
        aiPlayer.getReinforcement().rewardTrade("market", action, adenaProfit); // learn it paid off
        LOGGER.info("[TRADE-LOG-RL] [" + aiPlayer.getName() + "] PROFIT: action=" + action
                + " item=" + itemId + " +" + adenaProfit + " adena, emotion="
                + aiPlayer.getEmotions().getCurrentEmotion());
    }

    /** Called after a losing trade: register a negative signal (no emotion hit — keep it subtle). */
    public void onTradeLoss(String itemId, String action, long adenaLoss) {
        aiPlayer.getReinforcement().rewardTrade("market", action, -adenaLoss);
        LOGGER.info("[TRADE-LOG-RL] [" + aiPlayer.getName() + "] LOSS: action=" + action
                + " item=" + itemId + " -" + adenaLoss + " adena");
    }

    public MerchantDecision findArbitrageOpportunity() {
        // Advanced feature: buy low at one merchant, sell high at another
        return MerchantDecision.arbitrage("ITEM_1", "BUY_MERCHANT_1", "SELL_MERCHANT_2");
    }

    /**
     * Restocking logic - buy supplies for crafting/farming
     */
    public MerchantDecision planRestock() {
        // Check what materials would be profitable to craft/sell
        return MerchantDecision.buyBulk(new String[]{"MATERIAL_A", "MATERIAL_B"}, 10);
    }
}