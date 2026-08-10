package com.aiplayer.phase0.party;

/** MODE: COMPLETE. Field aliases added for phase0 integration (objId, hpMax, hpCurrent, hpPercent, isCritical, distanceTo, classId already present). */

/**
 * Immutable snapshot of a party member's current state.
 * Updated via GameStateMirror party list polling.
 */
public final class PartyMemberInfo {
    public final int objectId;
    public final String name;
    public final int level;
    public final int classId;
    public final int currentHp;
    public final int maxHp;
    public final int currentMp;
    public final int maxMp;
    public final int currentCp;
    public final int maxCp;
    public final int x;
    public final int y;
    public final int z;
    public final boolean isDead;
    public final boolean isInCombat;
    public final PartyRole assignedRole;

    // === Field aliases for phase0 code compatibility ===
    public final int objId;
    public final int hpMax;
    public final int hpCurrent;
    public final int mpMax;
    public final int mpCurrent;

    public PartyMemberInfo(int objectId, String name, int level, int classId,
                           int currentHp, int maxHp, int currentMp, int maxMp,
                           int currentCp, int maxCp, int x, int y, int z,
                           boolean isDead, boolean isInCombat, PartyRole assignedRole) {
        this.objectId = objectId;
        this.objId = objectId;
        this.name = name;
        this.level = level;
        this.classId = classId;
        this.currentHp = currentHp;
        this.hpCurrent = currentHp;
        this.maxHp = maxHp;
        this.hpMax = maxHp;
        this.currentMp = currentMp;
        this.mpCurrent = currentMp;
        this.maxMp = maxMp;
        this.mpMax = maxMp;
        this.currentCp = currentCp;
        this.maxCp = maxCp;
        this.x = x;
        this.y = y;
        this.z = z;
        this.isDead = isDead;
        this.isInCombat = isInCombat;
        this.assignedRole = assignedRole != null ? assignedRole : PartyRole.fromClassId(classId);
    }

    public double hpPercent() {
        return maxHp > 0 ? (currentHp * 100.0 / maxHp) : 0;
    }

    public double mpPercent() {
        return maxMp > 0 ? (currentMp * 100.0 / maxMp) : 0;
    }

    public double cpPercent() {
        return maxCp > 0 ? (currentCp * 100.0 / maxCp) : 0;
    }

    public double distanceTo(int otherX, int otherY) {
        return Math.hypot(x - otherX, y - otherY);
    }

    public boolean needsHeal() {
        return hpPercent() < 70 && !isDead;
    }

    public boolean isCritical() {
        return hpPercent() < 30 && !isDead;
    }

    @Override
    public String toString() {
        return String.format("Member[%s Lv%d %s HP:%.0f%% MP:%.0f%%]",
            name, level, assignedRole.displayName, hpPercent(), mpPercent());
    }
}
