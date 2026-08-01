package com.aiplayer.economy;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Net Worth Optimizer - Tasks 94, 95, 96
 *
 *  - Task 94: Tax optimization (minimize adena loss on trades)
 *  - Task 95: International trade (cross-zone / cross-region trade planning)
 *  - Task 96: Currency exchange (multi-currency conversion logic)
 *
 * In Lineage 2, certain towns/tax rates and raid drops affect profit.
 * Our AI accounts for taxes (Task 94), plans long-distance trade routes
 * between villa/manor regions (Task 95), and manages valued items as
 * de-facto currency (Task 96).
 */
public class NetWorthOptimizer {
    private static final Logger LOGGER = Logger.getLogger(NetWorthOptimizer.class.getName());
    private static final NetWorthOptimizer INSTANCE = new NetWorthOptimizer();

    private NetWorthOptimizer() {
        LOGGER.info("[NetWorthOptimizer] Wealth optimizer initialized");
    }

    public static NetWorthOptimizer getInstance() {
        return INSTANCE;
    }

    /**
     * Tax optimization (Task 94): compute after-tax profit.
     * L2 has variable tax rates per town/castle owner.
     */
    public long afterTaxProfit(long grossProfit, double taxRate) {
        double tax = Math.max(0.0, taxRate) * grossProfit;
        long net = (long) (grossProfit - tax);
        LOGGER.fine("[Tax] Gross=" + grossProfit + ", taxRate=" + taxRate
                + ", tax=" + (long) tax + ", net=" + net);
        return net;
    }

    /**
     * Tax optimization: choose the town with the best (lowest) tax rate
     * for selling. Returns town name.
     */
    public String bestSellTown(Map<String, Double> townTaxRates, Map<String, Long> townSellPrices) {
        String bestTown = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String town : townTaxRates.keySet()) {
            long price = townSellPrices.getOrDefault(town, 0L);
            double taxRate = townTaxRates.getOrDefault(town, 0.0);
            double netWorth = price * (1.0 - taxRate);
            if (netWorth > bestScore) {
                bestScore = netWorth;
                bestTown = town;
            }
        }
        return bestTown;
    }

    /**
     * International / cross-region trade (Task 95): decide whether a
     * long trade route is worth the travel time + gatekeeper cost.
     */
    public boolean isWorthLongTrade(long itemProfit, long travelCost, long timeMinutes) {
        // Profit must exceed travel cost and be worth the time investment
        long timeValue = timeMinutes * 10; // 10 adena per minute of time value
        return itemProfit > (travelCost + timeValue);
    }

    /**
     * Currency exchange (Task 96): value of an item measured in adena.
     * Some items (Blood Crystals, Ancient Adena) act as secondary currency.
     */
    public long currencyValue(long quantity, long itemValueInAdena) {
        return quantity * itemValueInAdena;
    }

    /**
     * Convert between item-based wealth and raw adena (Task 96).
     */
    public long toAdena(long itemCount, long itemValue, double conversionFee) {
        double gross = itemCount * itemValue;
        return (long) (gross * (1.0 - conversionFee));
    }

    /**
     * Net worth calculator - sum of adena + valued items.
     */
    public long calculateNetWorth(long adena, Map<String, Long> itemValuesInAdena) {
        long total = adena;
        for (long value : itemValuesInAdena.values()) {
            total += value;
        }
        return total;
    }
}
