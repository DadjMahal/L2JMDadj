package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class AuctionAI {
    private static final Logger LOGGER = Logger.getLogger(AuctionAI.class.getName());

    public static class AuctionItem {
        public final int itemId;
        public final String itemName;
        public final int currentBid;
        public final int startPrice;
        public final int bidIncrement;

        public AuctionItem(int id, String name, int start, int increment) {
            itemId = id; itemName = name; startPrice = start; bidIncrement = increment; currentBid = start;
        }
    }

    public static boolean shouldBid(AuctionItem item, int maxBid, int currentBid, int timeRemaining) {
        if (currentBid >= maxBid) return false;
        if (timeRemaining < 10) return false; // End of auction
        return true;
    }

    public static int calculateMaxBid(int baseValue, double personality) {
        return (int)(baseValue * personality);
    }

    public static String assessValue(AuctionItem item) {
        double ratio = (double) item.currentBid / item.startPrice;
        if (ratio > 5) return "OVERPRICED";
        if (ratio < 1.5) return "BAD_DEAL";
        return "FAIR";
    }
}
