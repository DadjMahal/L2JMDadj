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
    private final PacketLogger packetLogger;
    
    public MerchantAI(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.config = MerchantConfig.getInstance();
        this.packetLogger = new PacketLogger(aiPlayer.getName());
    }

    /** Get the packet logger for telemetry. */
    public PacketLogger getPacketLogger() { return packetLogger; }
    
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
        // TODO: REQUIRES PROTOCOL IMPLEMENTATION - Prompt 1
        // Currently returns mock data because AIPlayer.getProtocol() has no packet parsing
        // Need: ItemList packet (opcode 0x06 from ClientPackets.java) with inventory items
        // Once protocol parses ItemList, can get:
        //   aiPlayer.getProtocol().getInventorySlotCount() / inventory.getMaxPackableSlots()
        return 50 + (int)(Math.random() * 30); // Mock inventory usage - NOT YET TESTED
    }
    
    private int getInventoryAdena() {
        // TODO: REQUIRES PROTOCOL IMPLEMENTATION - Prompt 1
        // Currently returns mock data because AIPlayer.getProtocol() has no packet parsing
        // Need: ItemList packet (opcode 0x06 from ClientPackets.java) including adena (item ID 57)
        // Once protocol parses ItemList, can get actual adena:
        //   aiPlayer.getProtocol().getInventoryItemQuantity(57)
        return 10000 + (int)(Math.random() * 50000); // Mock adena - NOT YET TESTED
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