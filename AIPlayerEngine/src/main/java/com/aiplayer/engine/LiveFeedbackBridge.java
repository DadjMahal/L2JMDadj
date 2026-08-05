package com.aiplayer.engine;

import java.util.logging.Logger;

import com.aiplayer.protocol.PacketLogger;

/**
 * Live Feedback Bridge — Stream G (G-Live).
 *
 * <p>Turns the previously "unit-proven" D/E/F outcome hooks into live behavior by detecting
 * game-state transitions from real {@link PacketLogger} data (parsed from server packets) and
 * firing the corresponding {@link CombatAI} / {@link QuestAI} hooks:
 * <ul>
 *   <li><b>Level-up</b> — when the parsed StatusUpdate level rises past the track level, call
 *       {@code CombatAI.onLevelUp(level)} (which bumps emotion, sets the player level, and advances
 *       the long-term MAX_LEVEL goal).</li>
 *   <li><b>Death / respawn</b> — when HP crosses 0 (call {@code CombatAI.onDeath()}) and recovers to
 *       {@code &gt; 0} (call {@code CombatAI.onRespawn(level)}).</li>
 *   <li><b>Kill</b> — when a hostile entity is removed (DeleteObject), {@code onKill(name, xp)}.
 *       XP is unknown from packets, so we pass 0 and rely on the reward being XP-scaled at 0 — the
 *       kill still counts + bumps excitement; a future StatusUpdate XP parse can fill it in.</li>
 * </ul>
 * The bridge is driven once per think-tick by the live loop; a {@code handleTick()} call detects
 * transitions since the last call. Fully unit-testable by feeding synthetic StatusUpdate packets /
 * HP values.
 */
public class LiveFeedbackBridge {
    private static final Logger LOGGER = Logger.getLogger(LiveFeedbackBridge.class.getName());

    private final AIPlayer aiPlayer;
    private PacketLogger logger;
    private int lastLevel;
    private boolean wasDead;
    private int lastHostileCount;

    public LiveFeedbackBridge(AIPlayer aiPlayer, PacketLogger logger) {
        this.aiPlayer = aiPlayer;
        this.logger = logger;
        this.lastLevel = aiPlayer.getLevel();
        this.wasDead = logger.getCurHp() <= 0;
        this.lastHostileCount = logger.getHostileEntityCount();
    }

    /** Attach/re-attach the live reader's logger (e.g. after a reconnect). Idempotent. */
    public void attach(PacketLogger newLogger) {
        if (newLogger != null) {
            this.logger = newLogger;
            this.wasDead = newLogger.getCurHp() <= 0;
            this.lastHostileCount = newLogger.getHostileEntityCount();
        }
    }

    /**
     * Detect + fire outcome hooks since the previous tick. Called once per live think-tick.
     * @return true if at least one event was fired this tick (for telemetry/diagnostics).
     */
    public boolean handleTick() {
        boolean fired = false;
        int hp = logger.getCurHp();
        int parsedLevel = logger.getLevel();

        // Death / respawn transition (self HP).
        boolean nowDead = hp <= 0;
        if (nowDead && !wasDead) {
            aiPlayer.getCombatAI().onDeath();
            fired = true;
        } else if (!nowDead && wasDead) {
            aiPlayer.getCombatAI().onRespawn(Math.max(lastLevel, 1));
            fired = true;
        }
        wasDead = nowDead;

        // Level-up transition (parsed StatusUpdate level rose past track level).
        if (parsedLevel > lastLevel) {
            aiPlayer.getCombatAI().onLevelUp(parsedLevel);
            lastLevel = parsedLevel;
            fired = true;
        } else if (parsedLevel > aiPlayer.getLevel()) {
            // Logger already ahead of the AIPlayer (e.g. fresh connection) — adopt without re-firing.
            lastLevel = parsedLevel;
        }

        // Kill: hostiles dropped (DeleteObject removed an enemy we were engaged with).
        int hostileNow = logger.getHostileEntityCount();
        if (hostileNow < lastHostileCount && aiPlayer.getCombatAI().getSelectedTargetObjId() > 0) {
            String target = "objId=" + aiPlayer.getCombatAI().getSelectedTargetObjId();
            aiPlayer.getCombatAI().onKill(target); // XP unknown from packets -> 0
            fired = true;
        }
        lastHostileCount = hostileNow;

        return fired;
    }
}
