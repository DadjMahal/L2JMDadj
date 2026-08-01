package com.aiplayer.economy;

import java.util.*;
import java.util.logging.Logger;

/**
 * Economic Intelligence - Tasks 91, 92, 93
 *
 *  - Task 91: Arbitrage detection (buy cheap in one town, sell high in another)
 *  - Task 92: Risk assessment (evaluate trade risks before acting)
 *  - Task 93: Portfolio management (balance inventory and wealth)
 *
 * Like a real Lineage 2 trader who knows that Iron Ore is cheap in
 * Talkin village but valuable in Giran, our AI detects these
 * opportunities and manages its assets intelligently.
 */
public class EconomicEngine {
    private static final Logger LOGGER = Logger.getLogger(EconomicEngine.class.getName());
    private static final EconomicEngine INSTANCE = new EconomicEngine();

    /** An arbitrage opportunity found between two towns. */
    public static class ArbitrageOpportunity {
        public final String itemId;
        public final String buyTown;
        public final String sellTown;
        public final long buyPrice;
        public final long sellPrice;
        public final long profitPerUnit;

        public ArbitrageOpportunity(String itemId, String buyTown, String sellTown,
                                    long buyPrice, long sellPrice) {
            this.itemId = itemId;
            this.buyTown = buyTown;
            this.sellTown = sellTown;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.profitPerUnit = sellPrice - buyPrice;
        }
    }

    private final Random random = new Random();

    private EconomicEngine() {
        LOGGER.info("[EconomicEngine] Economic intelligence initialized");
    }

    public static EconomicEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Arbitrage detection (Task 91): find items with price differences
     * between towns that exceed the teleport/travel cost.
     */
    public ArbitrageOpportunity scanArbitrage(String itemId, Map<String, Long> buyPrices,
                                              Map<String, Long> sellPrices, long travelCost) {
        String cheapestBuyTown = null;
        long lowestBuy = Long.MAX_VALUE;
        for (Map.Entry<String, Long> e : buyPrices.entrySet()) {
            if (e.getValue() < lowestBuy) {
                lowestBuy = e.getValue();
                cheapestBuyTown = e.getKey();
            }
        }

        String bestSellTown = null;
        long highestSell = Long.MIN_VALUE;
        for (Map.Entry<String, Long> e : sellPrices.entrySet()) {
            if (e.getValue() > highestSell) {
                highestSell = e.getValue();
                bestSellTown = e.getKey();
            }
        }

        if (cheapestBuyTown == null || bestSellTown == null) return null;

        long profit = highestSell - lowestBuy - travelCost;
        if (profit > 0) {
            LOGGER.info("[Arbitrage] " + itemId + ": buy @" + cheapestBuyTown
                    + " (" + lowestBuy + "), sell @" + bestSellTown
                    + " (" + highestSell + ") => profit " + profit);
            return new ArbitrageOpportunity(itemId, cheapestBuyTown, bestSellTown, lowestBuy, highestSell);
        }
        return null;
    }

    /**
     * Risk assessment (Task 92): evaluate the risk of a trade.
     * Returns a risk level 0.0 (safe) to 1.0 (very risky).
     */
    public double assessTradeRisk(long capital, long investment, double expectedProfitMargin) {
        double risk = 0.0;
        // More of your capital at risk = more risk
        if (capital > 0) {
            risk += 0.5 * (investment / (double) capital);
        }
        // Higher expected returns usually mean higher risk
        risk += 0.3 * expectedProfitMargin;
        // Clamp to [0, 1]
        return Math.max(0.0, Math.min(1.0, risk));
    }

    /** Risk label helper. */
    public String riskLabel(double risk) {
        if (risk < 0.3) return "LOW";
        if (risk < 0.6) return "MODERATE";
        return "HIGH";
    }

    /**
     * Portfolio management (Task 93): decide whether to diversify.
     * If the AI holds too much of one item, it should sell some and buy others.
     */
    public int suggestedDiversifyAmount(Map<String, Integer> inventory, String focusItem, int maxSlot) {
        int focusCount = inventory.getOrDefault(focusItem, 0);
        int total = inventory.values().stream().mapToInt(Integer::intValue).sum();
        int otherCount = total - focusCount;
        // If focus item dominates the inventory, suggests selling the excess
        if (total > 0 && (focusCount / (double) total) > 0.7) {
            int excess = focusCount - total / 3;
            return Math.max(0, Math.min(excess, maxSlot - total / inventory.size()));
        }
        return 0;
    }
}
