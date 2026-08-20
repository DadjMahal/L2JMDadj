package com.aiplayer.core;

/** MODE: COMPLETE (callers wired: CombatAI + EngineDriver; subsystems without live protocol/data are honest seams). */

import java.util.logging.Logger;

import com.aiplayer.behavior.combat.AggroTracker;
import com.aiplayer.behavior.combat.CooldownTracker;
import com.aiplayer.behavior.combat.CombatRotation;
import com.aiplayer.behavior.combat.RotationFactory;
import com.aiplayer.behavior.combat.ShotManager;
import com.aiplayer.behavior.combat.SkillDatabase;
import com.aiplayer.behavior.combat.SkillInfo;
import com.aiplayer.behavior.combat.TargetSelector;
import com.aiplayer.behavior.humanize.HumanizedRandom;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.net.AIPlayer;
import com.aiplayer.behavior.combat.FighterRotation;
import com.aiplayer.behavior.lifecycle.DeathHandler;
import com.aiplayer.behavior.lifecycle.RecoveryFlow;
import com.aiplayer.behavior.social.ChatResponder;
import com.aiplayer.examples.EngineDriver;
import com.aiplayer.protocol.PacketLogger;

/**
 * The single runtime seam through which the Phase-0 Task 1-11 modules are reached
 * from the (unchanged-on-master) engine. Constructed by {@link CombatAI} only when
 * {@code engine.enabled=true} (default OFF), so the default build ignores behavior
 * entirely.
 *
 * Honest integration status (see Documentation/Audit/43):
 *   REAL  — Task 1 combat rotation + cooldown + shots, Task 2 targeting/aggro,
 *           Task 8 humanized reaction (pure helpers, no wire frames),
 *           Task 5 inventory advice (read-only recommendation from BotSnapshot).
 *   SEAM  — Task 4 death/recovery, Task 6 social/chat, Tasks 9/11 quest/farm:
 *           callers exist, but each depends on a not-yet-live data source or
 *           protocol opcode, so they answer with an explicit SKIP-... string
 *           instead of pretending to work (same rule as CoreWiring.SKIP-UNPROVEN).
 */
public final class EngineWiring
{
    private static final Logger LOGGER = Logger.getLogger(EngineWiring.class.getName());

    private final EngineConfig cfg = EngineConfig.getInstance();
    private final AIPlayer aiPlayer;
    private final String accountName;
    private final int classId;

    // Task 1 — lazy so an OFF system allocates nothing.
    private CombatRotation rotation;
    private CooldownTracker cooldownTracker;
    private ShotManager shotManager;

    // Task 2 — lazy.
    private TargetSelector targetSelector;
    private AggroTracker aggroTracker;

    public EngineWiring(AIPlayer aiPlayer)
    {
        this.aiPlayer = aiPlayer;
        this.accountName = aiPlayer.getName();
        this.classId = aiPlayer.getClassId();
    }

    // ===================== Task 1 — combat rotation / cooldown / shots =====================

    /**
     * Ask the class rotation which skill to cast. Returns -1 when the rotation is
     * not wired, the target is too far, MP is too low, or the skill is on cooldown.
     * Advancing the rotation (combo) is a side effect of the FighterRotation combo
     * design, so call this AT MOST ONCE per combat decision (CombatAI does).
     */
    public int recommendSkill(int hpPercent, int mpPercent, double distance)
    {
        CombatRotation rot = rotation();
        if (rot == null || distance <= 0.0)
        {
            return -1;
        }
        int skillId;
        try
        {
            skillId = rot.selectSkill(hpPercent, mpPercent, distance, true, -1);
        }
        catch (Exception e)
        {
            LOGGER.warning("[Behavior] selectSkill failed for " + accountName + ": " + e.getMessage());
            return -1;
        }
        if (skillId > 0 && isOnCooldown(skillId))
        {
            return -1;
        }
        return skillId;
    }

    /** Record that a skill was actually sent, starting its cooldown. */
    public void noteSkillUsed(int skillId)
    {
        if (skillId <= 0)
        {
            return;
        }
        long cooldownMs = 5000;
        SkillInfo lookup = SkillDatabase.get(skillId);
        if (lookup != null)
        {
            cooldownMs = lookup.cooldownMs;
        }
        cooldownTracker().putOnCooldown(skillId, cooldownMs);
        CombatRotation rot = rotation();
        if (rot != null)
        {
            rot.onSkillUsed(skillId);
        }
    }

    public boolean isOnCooldown(int skillId)
    {
        return cooldownTracker().isOnCooldown(skillId);
    }

    public boolean shouldKite()
    {
        CombatRotation rot = rotation();
        return rot != null && rot.shouldKite();
    }

    public int fleeThreshold()
    {
        CombatRotation rot = rotation();
        return rot != null ? rot.getFleeThreshold() : 20;
    }

    /** Called when a combat tick fires while in combat and shots are enabled. */
    public void tickShots()
    {
        if (!cfg.isCombatShotsEnabled())
        {
            return;
        }
        CombatRotation rot = rotation();
        if (rot != null && rot.useShots())
        {
            shotManager().tick(rot.getShotType());
        }
    }

    public boolean areShotsEnabled()
    {
        return shotManager() != null && shotManager().isShotsEnabled();
    }

    // ===================== Task 2 — targeting / aggro =====================

    public int selectTarget()
    {
        TargetSelector ts = targetSelector();
        return ts != null ? ts.selectTarget() : 0;
    }

    public void noteAggro(int mobObjId, int mobLevel)
    {
        AggroTracker tracker = aggroTracker();
        if (tracker != null && mobObjId > 0)
        {
            tracker.addAggro(mobObjId, mobLevel);
        }
    }

    public int aggroCount()
    {
        AggroTracker tracker = aggroTracker();
        return tracker != null ? tracker.getAggroCount() : 0;
    }

    // ===================== Task 8 — humanized reaction (pure) =====================

    /** Extra human-like delay (ms) before an action when humanize is on; 0 when off. */
    public long reactionDelayMs()
    {
        if (!cfg.isHumanizeEnabled())
        {
            return 0L;
        }
        try
        {
            return Math.max(0, HumanizedRandom.reactionTime(cfg.getReactionBaseMs(), cfg.getReactionSigmaMs(),
                cfg.getReactionOutlierChance(), cfg.getReactionOutlierMs()));
        }
        catch (Exception e)
        {
            return 0L;
        }
    }

    // ===================== Task 5 — inventory advice (read-only) =====================

    /** Recommendation string from the REAL BotSnapshot data, or null when nothing to say. */
    public String inventoryAdvice(BotSnapshot snapshot)
    {
        if (!cfg.isInventoryEnabled() || snapshot == null)
        {
            return null;
        }
        if (snapshot.inventoryFull)
        {
            return "inventory FULL (" + snapshot.inventoryUsagePercent + "%) — vendor/warehouse stop advised (VENDOR opcode not live-proven, see Audit/43)";
        }
        if (snapshot.inventoryUsagePercent >= 80)
        {
            return "inventory at " + snapshot.inventoryUsagePercent + "% — plan a vendor stop";
        }
        return null;
    }

    // ===================== Honest seams (callers exist; data/protocol does not yet) =====================

    /** Task 6 social/chat seam — PacketLogger has no incoming-chat source and sendSay() is a stub. */
    public String socialStatus()
    {
        if (!cfg.isSocialEnabled())
        {
            return null;
        }
        return "SKIP-SOCIAL: ChatResponder wiring blocked on an incoming-chat packet source in PacketLogger + a live-proven sendSay() opcode — see Documentation/Audit/43";
    }

    /** Task 4 death/respawn seam — restart-to-village opcode not live-proven (Audit/42 gate discipline). */
    public String deathRecoveryStatus()
    {
        if (!cfg.isDeathRecoveryEnabled())
        {
            return null;
        }
        return "SKIP-DEATH: respawn/restart opcode not live-proven yet — DeathHandler/RecoveryFlow stay queued (see Documentation/Audit/43)";
    }

    /** Tasks 9/11 quest+farm seam — blocked on real currentXp / quest packet parsing. */
    public String questFarmStatus()
    {
        if (!cfg.isQuestFarmEnabled())
        {
            return null;
        }
        return "SKIP-QUEST/FARM: depends on a real PacketLogger.getCurrentXp() + quest parsing, which does not exist yet (INTEGRATION_GAPS.md) — not faked";
    }

    // ===================== lazy accessors =====================

    private CombatRotation rotation()
    {
        if (rotation == null && cfg.isCombatRotationEnabled())
        {
            rotation = RotationFactory.forClassId(classId);
        }
        return rotation;
    }

    private CooldownTracker cooldownTracker()
    {
        if (cooldownTracker == null)
        {
            cooldownTracker = new CooldownTracker(accountName.toLowerCase());
        }
        return cooldownTracker;
    }

    private ShotManager shotManager()
    {
        if (shotManager == null)
        {
            shotManager = new ShotManager(aiPlayer);
        }
        return shotManager;
    }

    private TargetSelector targetSelector()
    {
        if (targetSelector == null && cfg.isTargetingEnabled())
        {
            targetSelector = new TargetSelector(accountName, aiPlayer.getLevel());
        }
        return targetSelector;
    }

    private AggroTracker aggroTracker()
    {
        if (aggroTracker == null && cfg.isTargetingEnabled())
        {
            aggroTracker = new AggroTracker(accountName);
        }
        return aggroTracker;
    }
}
