package com.aiplayer.learning;

import java.util.logging.Logger;

/**
 * Emotional State Engine - Task 73
 *
 * Tracks the emotional state of an AI player, making behavior feel
 * HUMAN-LIKE. Real players get frustrated, excited, bored, confident -
 * our AI players should too.
 *
 * Emotions affect decision-making:
 *  - FRUSTRATED: more likely to abandon quests, take breaks
 *  - EXCITED:    more likely to engage combat, explore aggressively
 *  - BORED:      more likely to seek social interaction or try new areas
 *  - CONFIDENT:  more likely to take on tougher challenges
 *  - CAUTIOUS:   more likely to play safe, stock up on potions
 *
 * Emotions shift based on game events (deaths, level-ups, big loot, etc.)
 */
public class EmotionalState {
    private static final Logger LOGGER = Logger.getLogger(EmotionalState.class.getName());

    public enum Emotion {
        NEUTRAL, FRUSTRATED, EXCITED, BORED, CONFIDENT, CAUTIOUS
    }

    private Emotion currentEmotion = Emotion.NEUTRAL;
    private double frustrationLevel = 0.0;  // 0.0 to 1.0
    private double excitementLevel = 0.0;
    private double boredomLevel = 0.0;
    private double confidenceLevel = 0.5;
    private long lastEmotionShift = System.currentTimeMillis();

    /** Called when the AI player dies. */
    public void onDeath() {
        frustrationLevel = Math.min(1.0, frustrationLevel + 0.3);
        confidenceLevel = Math.max(0.0, confidenceLevel - 0.2);
        excitementLevel = Math.max(0.0, excitementLevel - 0.15);
        recalculateEmotion();
        LOGGER.fine("[Emotion] Death -> " + currentEmotion + " (frustration=" + frustrationLevel + ")");
    }

    /** Called when the AI player levels up. */
    public void onLevelUp() {
        excitementLevel = Math.min(1.0, excitementLevel + 0.4);
        confidenceLevel = Math.min(1.0, confidenceLevel + 0.2);
        frustrationLevel = Math.max(0.0, frustrationLevel - 0.3);
        boredomLevel = Math.max(0.0, boredomLevel - 0.2);
        recalculateEmotion();
        LOGGER.fine("[Emotion] Level up -> " + currentEmotion);
    }

    /** Called when the AI gets good loot. */
    public void onGoodLoot() {
        excitementLevel = Math.min(1.0, excitementLevel + 0.2);
        boredomLevel = Math.max(0.0, boredomLevel - 0.1);
        recalculateEmotion();
    }

    /** Called when the AI has been idle for a while. */
    public void onIdle() {
        boredomLevel = Math.min(1.0, boredomLevel + 0.1);
        excitementLevel = Math.max(0.0, excitementLevel - 0.05);
        recalculateEmotion();
    }

    /** Called when the AI completes a quest. */
    public void onQuestComplete() {
        confidenceLevel = Math.min(1.0, confidenceLevel + 0.15);
        frustrationLevel = Math.max(0.0, frustrationLevel - 0.2);
        boredomLevel = Math.max(0.0, boredomLevel - 0.15);
        recalculateEmotion();
    }

    /** Called when a trade is profitable. */
    public void onProfitableTrade() {
        excitementLevel = Math.min(1.0, excitementLevel + 0.15);
        confidenceLevel = Math.min(1.0, confidenceLevel + 0.1);
        recalculateEmotion();
    }

    private void recalculateEmotion() {
        if (frustrationLevel > 0.6) {
            currentEmotion = Emotion.FRUSTRATED;
        } else if (excitementLevel > 0.6) {
            currentEmotion = Emotion.EXCITED;
        } else if (boredomLevel > 0.6) {
            currentEmotion = Emotion.BORED;
        } else if (confidenceLevel > 0.7) {
            currentEmotion = Emotion.CONFIDENT;
        } else if (confidenceLevel < 0.3) {
            currentEmotion = Emotion.CAUTIOUS;
        } else {
            currentEmotion = Emotion.NEUTRAL;
        }
        lastEmotionShift = System.currentTimeMillis();
    }

    /** Gradual emotional decay - emotions return to neutral over time. */
    public void decay() {
        frustrationLevel = Math.max(0.0, frustrationLevel - 0.05);
        excitementLevel = Math.max(0.0, excitementLevel - 0.05);
        boredomLevel = Math.max(0.0, boredomLevel - 0.03);
        confidenceLevel = confidenceLevel + (0.5 - confidenceLevel) * 0.05;
        recalculateEmotion();
    }

    public Emotion getCurrentEmotion() { return currentEmotion; }
    public double getFrustrationLevel() { return frustrationLevel; }
    public double getExcitementLevel() { return excitementLevel; }
    public double getBoredomLevel() { return boredomLevel; }
    public double getConfidenceLevel() { return confidenceLevel; }

    @Override
    public String toString() {
        return "Emotion{" + currentEmotion + ", fr=" + String.format("%.2f", frustrationLevel)
                + ", ex=" + String.format("%.2f", excitementLevel)
                + ", bo=" + String.format("%.2f", boredomLevel)
                + ", co=" + String.format("%.2f", confidenceLevel) + "}";
    }
}
