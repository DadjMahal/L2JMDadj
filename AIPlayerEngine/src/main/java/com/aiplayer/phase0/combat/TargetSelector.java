package com.aiplayer.phase0.combat;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.EntitySnapshot;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;

import java.util.*;

/**
 * Scores potential targets and selects the optimal one.
 * Real players consider: level diff, distance, aggro status, mob type, party context.
 *
 * FIX applied during integration (2026-08-08): every entity.objectId reference
 * below was entity.objId in Kimi's original file. The real EntitySnapshot class
 * (in the already-committed Phase 0 base) has a final int field named objId, not
 * objectId — this file would not have compiled as delivered. See
 * INTEGRATION_ORDER.md item 4a for the verification trail.
 */
public class TargetSelector {
    private static final int MAX_LEVEL_DIFF = 5;
    private static final int OPTIMAL_DISTANCE = 600;
    private static final int MAX_DISTANCE = 3000;
    private static final double AGGRO_BONUS = 15.0;
    private static final double LEADER_TARGET_BONUS = 20.0;
    private static final double BOSS_PENALTY = -50.0;
    private static final double PASSIVE_MOB_PENALTY = -5.0;

    private final String accountName;
    private final int aiPlayerLevel;
    private final AggroTracker aggro;
    private final PartyAssistTracker partyAssist;

    public TargetSelector(String accountName, int aiPlayerLevel) {
        this.accountName = accountName;
        this.aiPlayerLevel = aiPlayerLevel;
        this.aggro = new AggroTracker(accountName);
        this.partyAssist = new PartyAssistTracker(accountName);
    }

    /**
     * Select best target from GameStateMirror nearby entities.
     * Returns targetObjId or 0 if no valid target.
     */
    public int selectTarget() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return 0;

        List<TargetScore> scores = new ArrayList<>();
        int leaderTargetId = partyAssist.getPartyLeaderTarget();

        for (EntitySnapshot entity : self.nearby.values()) {
            if (!entity.isAttackable || entity.isDead) continue;
            if (entity.isPlayer && !entity.isEnemy) continue; // don't attack friendly players

            double dist = distance(self.x, self.y, entity.x, entity.y);
            if (dist > MAX_DISTANCE) continue;

            double levelScore = scoreLevelDiff(entity.level);
            double distScore = scoreDistance(dist);
            double aggroScore = scoreAggro(entity);
            double threatScore = scoreThreat(entity, leaderTargetId);

            scores.add(new TargetScore(entity.objId, levelScore, distScore, aggroScore, threatScore));
        }

        if (scores.isEmpty()) return 0;
        Collections.sort(scores);
        return scores.get(0).targetObjId;
    }

    /**
     * Check if current target is still optimal. If not, suggest switch.
     */
    public boolean shouldSwitchTarget(int currentTargetId) {
        int best = selectTarget();
        if (best == 0 || best == currentTargetId) return false;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        EntitySnapshot current = self.nearby.get(currentTargetId);
        EntitySnapshot bestEnt = self.nearby.get(best);

        if (current == null) return true; // target dead/gone
        if (bestEnt == null) return false;

        // Don't switch if current is aggro and best is not (stick to aggro)
        boolean currentAggro = aggro.isAggroedBy(current.objId);
        boolean bestAggro = aggro.isAggroedBy(bestEnt.objId);
        if (currentAggro && !bestAggro) return false;

        // Switch if best is significantly higher score
        double currentScore = scoreEntity(current, self);
        double bestScore = scoreEntity(bestEnt, self);
        return bestScore > currentScore + 10.0;
    }

    private double scoreEntity(EntitySnapshot e, BotStateSnapshot self) {
        double dist = distance(self.x, self.y, e.x, e.y);
        int leaderTarget = partyAssist.getPartyLeaderTarget();
        return scoreLevelDiff(e.level) * 1.5
             + scoreDistance(dist) * 1.0
             + scoreAggro(e) * 2.0
             + scoreThreat(e, leaderTarget) * 1.2;
    }

    private double scoreLevelDiff(int targetLevel) {
        int diff = Math.abs(targetLevel - aiPlayerLevel);
        if (diff > MAX_LEVEL_DIFF) return -diff * 2.0;
        return MAX_LEVEL_DIFF - diff; // 0 diff = max score
    }

    private double scoreDistance(double dist) {
        if (dist < 100) return 8.0; // very close, good
        if (dist > OPTIMAL_DISTANCE * 2) return 2.0;
        return 10.0 - (dist / OPTIMAL_DISTANCE); // linear falloff
    }

    private double scoreAggro(EntitySnapshot e) {
        if (aggro.isAggroedBy(e.objId)) return AGGRO_BONUS;
        if (!e.isAggressive) return PASSIVE_MOB_PENALTY;
        return 0.0;
    }

    private double scoreThreat(EntitySnapshot e, int leaderTargetId) {
        double score = 0.0;
        if (e.objId == leaderTargetId) score += LEADER_TARGET_BONUS;
        if (e.isBoss) score += BOSS_PENALTY;
        if (e.isElite) score += BOSS_PENALTY * 0.5;
        return score;
    }

    private static double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    public AggroTracker getAggroTracker() { return aggro; }
    public PartyAssistTracker getPartyAssistTracker() { return partyAssist; }
}
