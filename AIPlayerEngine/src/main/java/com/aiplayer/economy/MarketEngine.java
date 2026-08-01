package com.aiplayer.economy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Market Engine - Tasks 88, 89, 90
 *
 * Economic intelligence for AI players:
 *  - Task 88: Economic modeling (supply/demand simulation)
 *  - Task 89: Market simulation (predict price trends)
 *  - Task 90: Pricing algorithms (dynamic pricing decisions)
 *
 * In Lineage 2, prices vary between towns. A smart merchant AI knows
 * to buy where items are cheap and sell where they are expensive.
 */
public class MarketEngine {
    private static final Logger LOGGER = Logger.getLogger(MarketEngine.class.getName());
    private static final MarketEngine INSTANCE = new MarketEngine();

    /** Price observation for an item in a town. */
    public static class PriceObservation {
        public final String itemId;
        public final String town;
        public final long buyPrice;   // merchant sell price to us
        public final long sellPrice;  // merchant buy price from us
        public final long timestamp;

        public PriceObservation(String itemId, String town, long buyPrice, long sellPrice) {
            this.itemId = itemId;
            this.town = town;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.timestamp = System.currentTimeMillis();
        }

        /** Profit margin if we buy here and sell elsewhere (gross). */
        public double marginIfBase() {
            return buyPrice == 0 ? 0 : (sellPrice / (double) buyPrice) - 1.0;
        }
    }

    private final Map<String, List<PriceObservation>> priceHistory = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private MarketEngine() {
        LOGGER.info("[MarketEngine] Economic modeling initialized");
    }

    public static MarketEngine getInstance() {
        return INSTANCE;
    }

    /** Record an observed price for an item in a town. */
    public void recordPrice(String itemId, String town, long buyPrice, long sellPrice) {
        PriceObservation obs = new PriceObservation(itemId, town, buyPrice, sellPrice);
        priceHistory.computeIfAbsent(itemId, k -> Collections.synchronizedList(new ArrayList<>())).add(obs);
    }

    /**
     * Economic modeling (Task 88): predict the trend of an item's price.
     * Uses simple moving-average trend detection.
     */
    public Trend predictTrend(String itemId) {
        List<PriceObservation> history = priceHistory.get(itemId);
        if (history == null || history.size() < 2) {
            return Trend.STABLE;
        }
        long first = history.get(0).sellPrice;
        long last = history.get(history.size() - 1).sellPrice;
        double delta = (last - first) / (double) Math.max(1, first);
        if (delta > 0.1) return Trend.RISING;
        if (delta < -0.1) return Trend.FALLING;
        return Trend.STABLE;
    }

    public enum Trend { RISING, FALLING, STABLE }

    /**
     * Market simulation (Task 89): simulate where the best price is.
     * Returns the town with the best sell price for an item.
     */
    public String findBestSellTown(String itemId) {
        List<PriceObservation> history = priceHistory.get(itemId);
        if (history == null || history.isEmpty()) return null;
        return history.stream().max(Comparator.comparingLong(o -> o.sellPrice)).map(o -> o.town).orElse(null);
    }

    /**
     * Pricing algorithm (Task 90): decide whether to buy an item at a price.
     * Buys if the price is below the recent average (potential arbitrage).
     */
    public boolean shouldBuy(String itemId, long price) {
        List<PriceObservation> history = priceHistory.get(itemId);
        if (history == null || history.isEmpty()) return true; // no data, take a chance
        long avg = history.stream().mapToLong(o -> o.buyPrice).sum() / history.size();
        return price <= avg * 0.95; // buy 5% below average
    }

    /**
     * Pricing algorithm: decide the optimal sell price based on market trend.
     * Sell higher if trend is rising.
     */
    public long optimalSellPrice(String itemId, long basePrice) {
        Trend trend = predictTrend(itemId);
        switch (trend) {
            case RISING:  return (long)(basePrice * 1.15);
            case FALLING: return (long)(basePrice * 0.90);
            default:      return (long)(basePrice * 1.0);
        }
    }

    /** Number of tracked items across all towns. */
    public int trackedItemCount() {
        return priceHistory.size();
    }
}
