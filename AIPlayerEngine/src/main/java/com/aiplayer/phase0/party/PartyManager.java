package com.aiplayer.phase0.party;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.humanize.TimingJitter;
import com.aiplayer.phase0.movement.MovementController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.aiplayer.behavior.AIBrain;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.cli.AIPlayerEngine;

/**
 * Core party orchestrator for AI Players.
 * Manages party formation, role assignment, follow logic,
 * loot rules, and cross-player state synchronization via Redis.
 *
 * Integration: AIBrain polls PartyManager every 1s when in party.
 * MovementController uses follow target from here.
 * CombatAI checks party assist target.
 */
public final class PartyManager {

    private final String accountName;
    // JedisPool removed — see syncToRedis()/syncFromRedis()
    private final AntiDetectionEngine anti;
    private final MovementController movement;

    // Local party state
    private final List<PartyMemberInfo> members = new CopyOnWriteArrayList<>();
    private final Map<String, PartyRole> roleAssignments = new ConcurrentHashMap<>();
    private final Set<String> pendingInvites = ConcurrentHashMap.newKeySet();

    private boolean isInParty = false;
    private boolean isLeader = false;
    private String leaderName = null;
    private String followTarget = null;
    private LootRule lootRule = LootRule.RANDOM;
    private long lastSyncTime = 0;
    private long nextActionTime = 0;

    // Redis
    private static final String REDIS_PARTY_PREFIX = "player:party:";
    private static final long SYNC_INTERVAL_MS = 2000;

    public PartyManager(String accountName,
                        AntiDetectionEngine anti, MovementController movement) {
        this.accountName = accountName;
        this.anti = anti;
        this.movement = movement;
    }

    // ================================================================
    // PARTY STATE UPDATES (called from GameStateMirror / AIBrain)
    // ================================================================

    /**
     * Update party list from GameStateMirror snapshot.
     */
    public void updatePartyList(List<PartyMemberInfo> newMembers, boolean leaderFlag) {
        this.members.clear();
        this.members.addAll(newMembers);
        this.isInParty = !newMembers.isEmpty();
        this.isLeader = leaderFlag;

        if (isInParty) {
            // Auto-assign roles for unknown members
            for (PartyMemberInfo m : newMembers) {
                roleAssignments.putIfAbsent(m.name, PartyRole.fromClassId(m.classId));
            }
            // Identify leader
            if (leaderFlag) {
                this.leaderName = accountName;
            } else if (!newMembers.isEmpty()) {
                // Leader is first member or the one with LEADER role
                this.leaderName = newMembers.get(0).name;
            }
        } else {
            this.leaderName = null;
            this.followTarget = null;
        }
        syncToRedis();
    }

    /**
     * Main tick — call every 1s from AIBrain when in party.
     */
    public void tick(BotStateSnapshot self) {
        long now = System.currentTimeMillis();

        // Periodic Redis sync
        if (now - lastSyncTime > SYNC_INTERVAL_MS) {
            syncFromRedis();
            lastSyncTime = now;
        }

        if (!isInParty) return;
        if (now < nextActionTime) return;

        // Leader logic
        if (isLeader) {
            tickLeader(self);
        } else {
            tickMember(self);
        }
    }

    // ================================================================
    // LEADER LOGIC
    // ================================================================

    private void tickLeader(BotStateSnapshot self) {
        // Ensure all members have roles
        for (PartyMemberInfo m : members) {
            if (!roleAssignments.containsKey(m.name)) {
                assignOptimalRole(m);
            }
        }

        // Check for member deaths -> call for rez or pause
        long deadCount = members.stream().filter(m -> m.isDead).count();
        if (deadCount >= 2) {
            // Too many dead — pause pull, regroup
            setFollowTarget(null);
        }

        // If we have a follow target (moving to new zone), ensure followers keep up
        if (followTarget != null && followTarget.equals(accountName)) {
            PartyMemberInfo straggler = findStraggler(self);
            if (straggler != null) {
                // Wait for straggler with human-like patience
                nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.IDLE_PAUSE);
            }
        }
    }

    // ================================================================
    // MEMBER LOGIC
    // ================================================================

    private void tickMember(BotStateSnapshot self) {
        if (followTarget == null && leaderName != null) {
            followTarget = leaderName;
        }

        if (followTarget != null && !followTarget.equals(accountName)) {
            PartyMemberInfo target = findMemberByName(followTarget);
            if (target != null) {
                double dist = target.distanceTo(self.x, self.y);
                int followDistance = getFollowDistance();

                if (dist > followDistance + 200) {
                    // Follow with humanized offset
                    int[] dest = anti.perturbDestination(target.x, target.y, target.z, 100);
                    movement.moveTo(dest[0], dest[1], dest[2]);
                    nextActionTime = System.currentTimeMillis() + anti.getMovementInterval();
                } else if (dist < followDistance - 100) {
                    // Too close — stop to avoid train collision look
                    movement.stop();
                }
            }
        }
    }

    // ================================================================
    // ROLE ASSIGNMENT
    // ================================================================

    public void assignRole(String memberName, PartyRole role) {
        if (!isLeader) return;
        roleAssignments.put(memberName, role);
        syncToRedis();
    }

    private void assignOptimalRole(PartyMemberInfo member) {
        PartyRole baseRole = PartyRole.fromClassId(member.classId);
        // If we already have a healer and this would be second healer, make it support
        long healerCount = roleAssignments.values().stream().filter(r -> r == PartyRole.HEALER).count();
        if (baseRole == PartyRole.HEALER && healerCount >= 1) {
            roleAssignments.put(member.name, PartyRole.SUPPORT);
        } else {
            roleAssignments.put(member.name, baseRole);
        }
    }

    public PartyRole getMyRole(BotStateSnapshot self) {
        return roleAssignments.getOrDefault(accountName,
            PartyRole.fromClassId(self.classId));
    }

    // ================================================================
    // TARGET COORDINATION
    // ================================================================

    /**
     * Get the party's assist target (leader's target or called target).
     */
    public Integer getAssistTargetId() {
        if (!isInParty) return null;
        // In real integration: read leader's target from GameStateMirror
        // For now, return null to let CombatAI use its own logic
        return null;
    }

    /**
     * Find the best heal target for a healer role.
     */
    public PartyMemberInfo getHealTarget() {
        return members.stream()
            .filter(m -> !m.isDead && (m.hpMax > 0 ? m.hpCurrent * 100.0 / m.hpMax : 100.0) < 80)
            .min(Comparator.comparingDouble(PartyMemberInfo::hpPercent))
            .orElse(null);
    }

    /**
     * Find the party member in most critical condition.
     */
    public PartyMemberInfo getCriticalMember() {
        return members.stream()
            .filter(m -> m.isCritical())
            .min(Comparator.comparingDouble(PartyMemberInfo::hpPercent))
            .orElse(null);
    }

    // ================================================================
    // INVITE HANDLING
    // ================================================================

    public void onPartyInvite(String inviterName) {
        // Auto-accept if not in party and inviter is known/trusted
        // Phase 0: accept all invites from players for social blending
        if (!isInParty) {
            pendingInvites.add(inviterName);
            nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.NPC_INTERACT);
        }
    }

    public void acceptInvite(String inviterName) {
        pendingInvites.remove(inviterName);
        // In real integration: protocol.acceptPartyInvite()
    }

    public void declineInvite(String inviterName) {
        pendingInvites.remove(inviterName);
        // In real integration: protocol.declinePartyInvite()
    }

    public boolean hasPendingInvite() {
        return !pendingInvites.isEmpty();
    }

    // ================================================================
    // LOOT RULES
    // ================================================================

    public enum LootRule {
        RANDOM, ROUND_ROBIN, LEADER, FINDERS_KEEPERS
    }

    public void setLootRule(LootRule rule) {
        if (!isLeader) return;
        this.lootRule = rule;
        syncToRedis();
    }

    public LootRule getLootRule() {
        return lootRule;
    }

    // ================================================================
    // QUERIES
    // ================================================================

    public boolean isInParty() {
        return isInParty;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public List<PartyMemberInfo> getMembers() {
        return new ArrayList<>(members);
    }

    public int getPartySize() {
        return members.size() + (isInParty ? 1 : 0); // +1 for self if in party
    }

    public void setFollowTarget(String name) {
        this.followTarget = name;
    }

    public String getFollowTarget() {
        return followTarget;
    }

    private int getFollowDistance() {
        PartyRole myRole = roleAssignments.get(accountName);
        if (myRole == null) return 300;
        return myRole.preferredPosition.preferredDistance;
    }

    private PartyMemberInfo findMemberByName(String name) {
        for (PartyMemberInfo m : members) {
            if (m.name.equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private PartyMemberInfo findStraggler(BotStateSnapshot self) {
        for (PartyMemberInfo m : members) {
            double dist = m.distanceTo(self.x, self.y);
            if (dist > 3000) return m;
        }
        return null;
    }

    // ================================================================
    // REDIS SYNC
    // ================================================================

    private void syncToRedis() {
        // LEGIT_TODO: was Redis-backed (2s sync interval, 300s TTL). In-memory
        // fields (isInParty, isLeader, roleAssignments, etc.) already ARE the
        // real state within this JVM — no-op, not a stub. Cross-process party
        // state sync would need a real reason (multiple AIPlayerEngine
        // processes) before reaching for Redis again.
    }

    private void syncFromRedis() {
        // No-op — see syncToRedis() above.
    }

    public String getStatusReport() {
        return String.format("Party[%s: inParty=%s leader=%s members=%d role=%s]",
            accountName, isInParty, isLeader, members.size(),
            roleAssignments.getOrDefault(accountName, PartyRole.DAMAGE_DEALER).displayName);
    }
}
