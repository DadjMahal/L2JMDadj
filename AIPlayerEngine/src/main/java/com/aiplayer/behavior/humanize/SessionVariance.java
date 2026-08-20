package com.aiplayer.behavior.humanize;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Models session-level variance in AI Player behavior.
 * Real humans don't play identically every session:
 * - Morning sessions: slower, more cautious
 * - Late night sessions: tired, more mistakes, longer pauses
 * - Weekend sessions: longer, more social
 * - Weekday sessions: shorter, focused, efficient
 * - Session fatigue: APM degrades, reaction times increase
 * - Day-to-day variance: some days you're "on", some days you're off
 *
 * This class provides session-wide modifiers that shift the baseline
 * behavior of the AI Player for the entire session, creating
 * believable long-term patterns.
 */
public final class SessionVariance {

    public enum PlayTime {
        MORNING,      // 06:00 - 11:00
        AFTERNOON,    // 11:00 - 17:00
        EVENING,      // 17:00 - 22:00
        NIGHT,        // 22:00 - 02:00
        LATE_NIGHT    // 02:00 - 06:00
    }

    public enum DayType {
        WEEKDAY,
        WEEKEND,
        HOLIDAY     // Phase 1: configurable holidays
    }

    private final String accountName;
    private final BehavioralFingerprint fingerprint;

    // Session start metadata
    private final long sessionStartMs;
    private final PlayTime playTime;
    private final DayType dayType;
    private final int sessionSeed;

    // Session state
    private long lastActivityMs;
    private int actionsThisSession = 0;
    private int chatMessagesThisSession = 0;
    private int deathsThisSession = 0;

    // Cached modifiers (recalculated periodically)
    private double cachedSpeedMod = 1.0;
    private double cachedVarianceMod = 1.0;
    private double cachedAccuracyMod = 1.0;
    private long lastCacheUpdate = 0;

    public SessionVariance(String accountName, BehavioralFingerprint fingerprint) {
        this.accountName = accountName;
        this.fingerprint = fingerprint;
        this.sessionStartMs = System.currentTimeMillis();
        this.lastActivityMs = sessionStartMs;

        // Determine session characteristics from real-world time
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        this.playTime = classifyPlayTime(now.getHour());
        this.dayType = classifyDayType(now.getDayOfWeek());

        // Deterministic session seed from account + date
        this.sessionSeed = accountName.hashCode() + (int) (sessionStartMs / 86400000L);

        // Initial modifiers
        recalculateModifiers();
    }

    /**
     * Get session fatigue factor (0.0 = fresh, 1.0 = exhausted).
     * Increases over session duration based on stamina fingerprint.
     */
    public double getSessionFatigue() {
        long sessionMinutes = (System.currentTimeMillis() - sessionStartMs) / 60000L;
        int expectedDuration = fingerprint.avgSessionMinutes;

        if (sessionMinutes <= expectedDuration * 0.3) {
            return 0.0; // Warmup phase
        }

        double fatigue = (sessionMinutes - expectedDuration * 0.3) / (expectedDuration * 0.7);
        // Stamina fingerprint extends session endurance
        fatigue *= (1.0 - fingerprint.sessionStamina * 0.5);

        return clamp(fatigue);
    }

    /**
     * Get speed modifier for this session.
     * Morning = slower, Night = faster but sloppier
     */
    public double getSpeedModifier() {
        recalculateIfNeeded();
        return cachedSpeedMod;
    }

    /**
     * Get variance boost for this session.
     * Tired/sleepy sessions have higher variance.
     */
    public double getVarianceBoost() {
        recalculateIfNeeded();
        return cachedVarianceMod;
    }

    /**
     * Get accuracy modifier for this session.
     * Late night = less accurate
     */
    public double getAccuracyModifier() {
        recalculateIfNeeded();
        return cachedAccuracyMod;
    }

    /**
     * Should the player take a break soon?
     */
    public boolean shouldTakeBreak() {
        long sessionMinutes = (System.currentTimeMillis() - sessionStartMs) / 60000L;
        double fatigue = getSessionFatigue();

        // Natural break points based on stamina
        int breakInterval = (int) lerp(fingerprint.sessionStamina, 30, 90);
        boolean intervalTrigger = sessionMinutes > 0 && sessionMinutes % breakInterval < 2;
        boolean fatigueTrigger = fatigue > 0.75;

        return intervalTrigger || fatigueTrigger;
    }

    /**
     * Get recommended break duration in minutes.
     */
    public int getBreakDurationMinutes() {
        double fatigue = getSessionFatigue();
        return (int) lerp(fatigue, 3, 15);
    }

    /**
     * Should player end session soon?
     */
    public boolean shouldEndSession() {
        long sessionMinutes = (System.currentTimeMillis() - sessionStartMs) / 60000L;
        double fatigue = getSessionFatigue();

        // End session if exceeded expected duration + variance
        int maxDuration = fingerprint.avgSessionMinutes + ThreadLocalRandom.current().nextInt(15, 45);
        return sessionMinutes > maxDuration || fatigue > 0.9;
    }

    /**
     * Get social modifier for this session.
     * Weekends/evenings = more social
     */
    public double getSocialModifier() {
        double base = fingerprint.socialTendency;

        switch (playTime) {
            case EVENING: base *= 1.3; break;
            case NIGHT:   base *= 1.2; break;
            case MORNING: base *= 0.7; break;
            case LATE_NIGHT: base *= 0.5; break;
            default: break;
        }

        if (dayType == DayType.WEEKEND) {
            base *= 1.25;
        }

        return clamp(base);
    }

    /**
     * Get risk modifier for this session.
     * Late night = more reckless, Morning = more cautious
     */
    public double getRiskModifier() {
        double base = fingerprint.riskTolerance;

        switch (playTime) {
            case NIGHT:
            case LATE_NIGHT:
                base *= 1.2; // More reckless when tired
                break;
            case MORNING:
                base *= 0.85; // Cautious when groggy
                break;
            default:
                break;
        }

        // Deaths make player more cautious
        base *= Math.max(0.5, 1.0 - deathsThisSession * 0.15);

        return clamp(base);
    }

    /**
     * Record an action for fatigue tracking.
     */
    public void recordAction() {
        actionsThisSession++;
        lastActivityMs = System.currentTimeMillis();
    }

    /**
     * Record a chat message.
     */
    public void recordChat() {
        chatMessagesThisSession++;
    }

    /**
     * Record a death.
     */
    public void recordDeath() {
        deathsThisSession++;
    }

    /**
     * Get current play time classification.
     */
    public PlayTime getPlayTime() {
        return playTime;
    }

    /**
     * Get current day type.
     */
    public DayType getDayType() {
        return dayType;
    }

    /**
     * Get session duration in minutes.
     */
    public long getSessionDurationMinutes() {
        return (System.currentTimeMillis() - sessionStartMs) / 60000L;
    }

    /**
     * Get total actions taken in this session (for status reporting).
     */
    public int getActionsThisSession() {
        return actionsThisSession;
    }

    /**
     * Get session summary for logging/monitoring.
     */
    public String getSessionSummary() {
        return String.format(
            "Session[%s: %s %s, %dmin, actions=%d, chats=%d, deaths=%d, fatigue=%.2f]",
            accountName, dayType, playTime, getSessionDurationMinutes(),
            actionsThisSession, chatMessagesThisSession, deathsThisSession,
            getSessionFatigue()
        );
    }

    // ------------------------------------------------------------------

    private void recalculateIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate < 60000) return; // Recalc every minute
        recalculateModifiers();
    }

    private void recalculateModifiers() {
        lastCacheUpdate = System.currentTimeMillis();

        double speed = 1.0;
        double variance = 1.0;
        double accuracy = 1.0;

        // Time-of-day effects
        switch (playTime) {
            case MORNING:
                speed *= 0.92;
                variance *= 1.05;
                accuracy *= 0.95;
                break;
            case AFTERNOON:
                speed *= 1.0;
                variance *= 1.0;
                accuracy *= 1.0;
                break;
            case EVENING:
                speed *= 1.05;
                variance *= 0.95;
                accuracy *= 1.02;
                break;
            case NIGHT:
                speed *= 1.08;
                variance *= 1.15;
                accuracy *= 0.92;
                break;
            case LATE_NIGHT:
                speed *= 0.95;
                variance *= 1.3;
                accuracy *= 0.85;
                break;
        }

        // Day type effects
        if (dayType == DayType.WEEKEND) {
            speed *= 0.95; // More relaxed
            variance *= 1.1;
        }

        // Fatigue effects
        double fatigue = getSessionFatigue();
        speed *= Math.max(0.7, 1.0 - fatigue * 0.25);
        variance *= 1.0 + fatigue * 0.4;
        accuracy *= Math.max(0.75, 1.0 - fatigue * 0.2);

        // Day-to-day variance (some days you're "on", some you're not)
        // Deterministic from session seed so it's consistent for the whole session
        double dailyVariance = ((sessionSeed % 100) / 100.0 - 0.5) * 0.2;
        speed *= (1.0 + dailyVariance);
        accuracy *= (1.0 - dailyVariance * 0.5);

        cachedSpeedMod = clamp(speed);
        cachedVarianceMod = clamp(variance);
        cachedAccuracyMod = clamp(accuracy);
    }

    private static PlayTime classifyPlayTime(int hour) {
        if (hour >= 6 && hour < 11) return PlayTime.MORNING;
        if (hour >= 11 && hour < 17) return PlayTime.AFTERNOON;
        if (hour >= 17 && hour < 22) return PlayTime.EVENING;
        if (hour >= 22 || hour < 2) return PlayTime.NIGHT;
        return PlayTime.LATE_NIGHT;
    }

    private static DayType classifyDayType(DayOfWeek day) {
        return (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
            ? DayType.WEEKEND
            : DayType.WEEKDAY;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double clamp(double v) {
        return Math.max(0.1, Math.min(2.0, v));
    }
}
