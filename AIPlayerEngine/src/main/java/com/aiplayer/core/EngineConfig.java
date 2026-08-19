package com.aiplayer.core;
import com.aiplayer.behavior.AIBrain;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.net.AIPlayer;

/** MODE: COMPLETE. Central flag switchboard for the Phase-0 upgrade (Tasks 1-11 bundle). */

/**
 * Phase-0 subsystem switchboard. Every feature defaults to OFF so the engine,
 * CombatAI / AIPlayer / AIBrain behavior is byte-for-byte unchanged unless the
 * keys below are enabled in {@code config/ai-player.properties}.
 *
 * Integration rule (see Documentation/Audit/43): a phase0 subsystem is never
 * reachable through the flags until it has (a) a real caller, (b) unit tests,
 * and (c) — for anything that sends wire frames — a live-proven opcode.
 */
public final class EngineConfig
{
    private static final EngineConfig INSTANCE = new EngineConfig();

    private final AIConfiguration aiConfig = AIConfiguration.getInstance();

    private EngineConfig()
    {
    }

    public static EngineConfig getInstance()
    {
        return INSTANCE;
    }

    /** Master switch — everything else ANDs with this. */
    public boolean isEnabled()
    {
        return aiConfig.getBooleanProperty("phase0.enabled", false);
    }

    /** Task 1 — class rotations / cooldown / shots inside CombatAI. */
    public boolean isCombatRotationEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.combat_rotation", false);
    }

    /** Task 1 — soul/spirit shot toggle via ShotManager. */
    public boolean isCombatShotsEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.combat_shots", false);
    }

    /** Task 2 — TargetSelector + AggroTracker callers. */
    public boolean isTargetingEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.targeting", false);
    }

    /** Task 8 — humanized reaction/perturbation (pure, no wire frames). */
    public boolean isHumanizeEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.humanize", false);
    }

    /** Task 6 — social/chat (needs incoming-chat packet source + sendSay(); seam today). */
    public boolean isSocialEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.social", false);
    }

    /** Task 5 — inventory advice (read-only from BotSnapshot). */
    public boolean isInventoryEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.inventory", false);
    }

    /** Task 4 — death/respawn recovery (respawn opcode not live-proven; seam today). */
    public boolean isDeathRecoveryEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.death_recovery", false);
    }

    /** Tasks 9+11 — quest/farm scoring (blocked on real currentXp parsing; seam today). */
    public boolean isQuestFarmEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.quest_farm", false);
    }

    /** TIM-001 — proactive far-travel goal (ZoneRouter) inside the fleet loop (W6 movement wiring). */
    public boolean isMovementEnabled()
    {
        return isEnabled() && aiConfig.getBooleanProperty("phase0.movement", false);
    }

    /** TIM-001 — how long a bot must be idle with no target before it routes to a far destination. */
    public int getMovementIdleRouteMs()
    {
        return aiConfig.getIntProperty("phase0.movement.idle_route_ms", 20_000);
    }

    /** TIM-001 — smallest acceptable "travel" distance for the zone router (units). */
    public int getMovementMinRadius()
    {
        return aiConfig.getIntProperty("phase0.movement.min_radius", 4_000);
    }

    /** TIM-001 — largest acceptable route distance for the zone router (units). */
    public int getMovementMaxRadius()
    {
        return aiConfig.getIntProperty("phase0.movement.max_radius", 30_000);
    }

    // Humanize parameters used by EngineWiring.reactionDelayMs().
    public int getReactionBaseMs()
    {
        return aiConfig.getIntProperty("phase0.reaction_base_ms", 250);
    }

    public double getReactionSigmaMs()
    {
        return aiConfig.getIntProperty("phase0.reaction_sigma_ms", 60);
    }

    public double getReactionOutlierChance()
    {
        return aiConfig.getIntProperty("phase0.reaction_outlier_pct", 10) / 100.0;
    }

    public int getReactionOutlierMs()
    {
        return aiConfig.getIntProperty("phase0.reaction_outlier_ms", 1200);
    }
}
