package com.aiplayer.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stream G (G-Content): proves EventCalendarAI / AchievementAI / HeroTitleAI are wired into
 * AIPlayer (getters + a real achievement->goal hook). Before G all three had ZERO callers.
 */
public class StreamGContentTest {

    @Test
    public void contentAIsAreExposedAndNonNull() {
        AIPlayer p = new AIPlayer("GContentBot", 1, 1, 0);
        assertNotNull(p.getAchievementAI());
        assertNotNull(p.getEventCalendarAI());
        assertNotNull(p.getHeroTitleAI());
    }

    @Test
    public void achievementCompletionAdvancesLongTermGoal() {
        AIPlayer p = new AIPlayer("GContentBot", 1, 1, 0);
        assertFalse(p.getAchievementAI().hasAchievement("KILL_BOSS"));
        p.markAchievementCompleted("KILL_BOSS");
        assertTrue(p.getAchievementAI().hasAchievement("KILL_BOSS"));
        // The hook feeds the ACHIEVEMENT_RAID long-term goal (wiring, not just a record).
        assertEquals(1, p.getLongTermGoals().getGoalProgress(LongTermGoalsAI.Goal.ACHIEVEMENT_RAID));
    }

    @Test
    public void eventCalendarSelectsHourlyPriorityEvent() {
        AIPlayer p = new AIPlayer("GContentBot", 1, 1, 0);
        EventCalendarAI.CalendarEvent top = p.getEventCalendarAI().getHighestPriorityEvent(1);
        assertNotNull(top);
        assertTrue(top.priority >= 9);
        // Participation gate: the bot participates on the event's day.
        assertTrue(EventCalendarAI.shouldParticipate(
            new EventCalendarAI.CalendarEvent("X", 3, 5), 3));
        assertFalse(EventCalendarAI.shouldParticipate(
            new EventCalendarAI.CalendarEvent("X", 3, 5), 4));
    }

    @Test
    public void heroTitleBuffsApplyForHighHeroOnSiege() {
        AIPlayer p = new AIPlayer("GContentBot", 1, 1, 0);
        var buffs = p.getHeroTitleAI().calculateHeroBuffs(60, true);
        assertTrue(buffs.containsKey(HeroTitleAI.HeroBuff.HERO_COMMAND)); // siege aura
        assertTrue(buffs.containsKey(HeroTitleAI.HeroBuff.BLADE_DANCE)); // high hero level
        // Not in combat -> don't toggle a buff, regardless of cooldown.
        assertFalse(p.getHeroTitleAI().shouldUseHeroBuff(HeroTitleAI.HeroBuff.BLADE_DANCE, 1000, false));
    }
}
