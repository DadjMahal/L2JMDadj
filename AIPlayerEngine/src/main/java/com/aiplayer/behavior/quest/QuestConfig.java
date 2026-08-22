package com.aiplayer.behavior.quest;

import com.aiplayer.core.AIConfiguration;

/**
 * Quest AI Configuration — controls quest selection, acceptance, and completion behavior.
 *
 * <p>EB-11 SINGLE-SOURCE: this class no longer parses its own copy of
 * {@code config/ai-player.properties}. Every read delegates to the ONE loaded store,
 * {@link AIConfiguration}, which the engine loads exactly once — the duplicate parse is gone.
 */
public class QuestConfig {
    private static final QuestConfig INSTANCE = new QuestConfig();

    private QuestConfig() {
    }

    public static QuestConfig getInstance() {
        return INSTANCE;
    }

    // Configuration getters
    public boolean isEnabled() {
        return getBooleanProperty("quest.enabled", true);
    }

    public int getMaxActiveQuests() {
        return getIntProperty("quest.max_active", 3);
    }

    public int getSearchRadius() {
        return getIntProperty("quest.search_radius", 2000);
    }

    public boolean isDailyPriority() {
        return getBooleanProperty("quest.daily_priority", true);
    }

    public boolean isClassChangeEnabled() {
        return getBooleanProperty("quest.class_change_enabled", true);
    }

    public boolean shouldAbandonOnFailure() {
        return getBooleanProperty("quest.abandon_on_failure", true);
    }

    public int getDeadlineHours() {
        return getIntProperty("quest.deadline_hours", 24);
    }

    // Utility methods
    // EB-11 SINGLE-SOURCE: delegate every read to the ONE loaded AIConfiguration store.
    private String getProperty(String key, String defaultValue) {
        return AIConfiguration.getInstance().getProperty(key, defaultValue);
    }

    private int getIntProperty(String key, int defaultValue) {
        return AIConfiguration.getInstance().getIntProperty(key, defaultValue);
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        return AIConfiguration.getInstance().getBooleanProperty(key, defaultValue);
    }
}
