package com.aiplayer.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.engine.CombatFramePlanner.FrameStep;

/**
 * Verifies the USE_SKILL path of {@link CombatFramePlanner} now that
 * REQUEST_MAGIC_SKILL_USE (0x2F) is live-proven on the H5 GameServer and
 * {@code PacketCodec.encodeUseSkill} exists (see PacketCodecUseSkillTest for the
 * byte layout). The planner must emit the exact [0x2F][int skillId][int ctrl][byte shift]
 * 12-byte frame — Action first when a target is present, bare skill otherwise —
 * and must refuse to fabricate a frame for non-numeric skill placeholders.
 */
class CombatFramePlannerUseSkillTest
{
    @Test
    void useSkillEmitsActionThenTheProven0x2FFrame()
    {
        CombatDecision decision = CombatDecision.useSkill("3", "268461932", "CombatRotation");
        List<FrameStep> steps = new CombatFramePlanner().plan(decision, 10, 20, -3600, 268461932);

        assertEquals(2, steps.size(), "Action(0x04) to lock the target + USE_SKILL(0x2F)");
        assertEquals(0x04, steps.get(0).getOpcode(), "first step must be Action 0x04");
        assertEquals(0x2F, steps.get(1).getOpcode(), "second step must be REQUEST_MAGIC_SKILL_USE 0x2F");
        assertEquals(12, steps.get(1).frame.length, "skill frame is 12 bytes incl. size header");
        assertEquals(3, leInt(steps.get(1).frame, 3), "skillId right after opcode");
    }

    @Test
    void useSkillWithoutTargetSendsBareSkillFrame()
    {
        // Self-buff / healer cast where no targetObjId is known: no Action needed.
        CombatDecision decision = CombatDecision.useSkill("1068", null, "Rotation");
        List<FrameStep> steps = new CombatFramePlanner().plan(decision, 0, 0, 0, 0);

        assertEquals(1, steps.size(), "bare 0x2F frame only");
        assertEquals(0x2F, steps.get(0).getOpcode());
        assertEquals(1068, leInt(steps.get(0).frame, 3));
    }

    @Test
    void nonNumericSkillPlaceholderIsNotFabricated()
    {
        // CombatAI.heal() produces skillId="HEAL" — that is a placeholder, not a real skill
        // id. The planner must not invent a frame for it (INTEGRATION_GAPS: no fake frames).
        CombatDecision decision = CombatDecision.heal();
        List<FrameStep> steps = new CombatFramePlanner().plan(decision, 0, 0, 0, 0);

        assertEquals(0, steps.size(), "placeholder skill ids must be skipped, not guessed");
    }

    private static int leInt(byte[] d, int i)
    {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
    }
}