package com.aiplayer.engine;
import java.util.logging.Logger;

public class TradePersonalityAI {
    private static final Logger LOGGER = Logger.getLogger(TradePersonalityAI.class.getName());

    public enum TradeStyle { FAIR, AGGRESSIVE, COMPENSATING, TRUSTING, SUSPICIOUS }

    public static class TradeDecision {
        public final boolean shouldTrade;
        public final double offerMultiplier;
        public final String reason;

        public TradeDecision(boolean trade, double mult, String r) {
            shouldTrade = trade; offerMultiplier = mult; reason = r;
        }
    }

    public static TradeDecision evaluateTrade(int offerValue, int askValue, TradeStyle style) {
        double ratio = (double) offerValue / askValue;
        switch (style) {
            case FAIR: return new TradeDecision(ratio > 0.8, 1.0, "Fair price");
            case AGGRESSIVE: return new TradeDecision(ratio > 0.5, 0.8, "Good deal");
            case COMPENSATING: return new TradeDecision(ratio > 1.2, 1.2, "Overpay for relationship");
            case SUSPICIOUS: return new TradeDecision(ratio > 1.5, 1.5, "Need good price");
            default: return new TradeDecision(ratio > 0.9, 1.0, "Standard");
        }
    }
}
