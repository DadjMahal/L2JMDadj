package com.aiplayer.behavior.combat;

import java.util.ArrayList;
import java.util.List;

import com.aiplayer.protocol.PacketCodec;
import com.aiplayer.protocol.GameServerFrameWriter;
import com.aiplayer.behavior.combat.CombatDecision.Action;

/**
 * Stream C: turns a {@link CombatDecision} into the exact, ordered list of client wire frames to send.
 *
 * <p>Each {@link FrameStep} carries a fully framed packet (2-byte self-inclusive size + payload, as the
 * B4/B8 probes proved) plus the delay to respect before the next send. The server flood protector
 * {@code canPerformPlayerAction()} requires ~1s between {@code Action} and {@code AttackRequest}.
 *
 * <p>This is pure logic (no I/O) so it is unit-testable; a {@code GameServerFrameWriter} actually writes
 * the frames over a GameServer socket.
 */
public class CombatFramePlanner
{
    /** Server flood-protector spacing required between client actions (ms). */
    public static final int FLOOD_PROTECTOR_DELAY_MS = 1000;

    /** Simple escape offset used for FLEE/RETREAT until geo-aware escape routing (Stream G). */
    public static final int ESCAPE_DISTANCE = 300;

    /** One frame to send, plus the delay to wait before sending the next one. */
    public static class FrameStep
    {
        public final byte[] frame;
        public final long delayAfterMs;

        public FrameStep(byte[] frame, long delayAfterMs)
        {
            this.frame = frame;
            this.delayAfterMs = delayAfterMs;
        }

        public int getOpcode()
        {
            return frame != null && frame.length >= 3 ? (frame[2] & 0xff) : -1;
        }
    }

    /**
     * Plan the frames for a combat decision.
     *
     * @param decision   the decision to execute
     * @param playerX/Y/Z the player's current position (used as Action/Move origin)
     * @param targetObjId the selected target's objectId (>&nbsp;0 required for attack)
     * @return ordered, non-null list of frames (empty when nothing to send)
     */
    public List<FrameStep> plan(CombatDecision decision, int playerX, int playerY, int playerZ, int targetObjId)
    {
        List<FrameStep> steps = new ArrayList<>();
        if (decision == null)
        {
            return steps;
        }

        switch (decision.getAction())
        {
            case ENGAGE_TARGET:
            case ATTACK:
                // Select + auto-attack: Action(0x04) then, after the flood-protector gap, AttackRequest(0x0A).
                if (targetObjId > 0)
                {
                    steps.add(new FrameStep(PacketCodec.encodeAction(targetObjId, playerX, playerY, playerZ),
                        FLOOD_PROTECTOR_DELAY_MS));
                    steps.add(new FrameStep(PacketCodec.encodeAttackRequest(targetObjId), 0));
                }
                break;

            case FLEE:
            case RETREAT:
            case BLOCK:
                // Run to a simple deterministic escape offset (geo-aware routing is a follow-up).
                int ex = playerX + ESCAPE_DISTANCE;
                int ey = playerY;
                steps.add(new FrameStep(
                    PacketCodec.encodeMoveToLocation(ex, ey, playerZ, playerX, playerY, playerZ, 0), 0));
                break;

            case USE_SKILL:
                // REQUEST_MAGIC_SKILL_USE (0x2F) is LIVE-PROVEN on the Interlude GameServer:
                // the probe sent [0x2F][int skillId][int ctrl][byte shift] straight after
                // an Action(0x04) and the server parsed it (no disconnect; it replied
                // ACTION_FAIL/SystemMessage on game conditions, never on opcode/parse).
                // Send order mirrors that proof: optional Action to (re)lock the target,
                // then the skill frame. The skill's own weapon/range conditions decide
                // whether the cast actually fires — same as a real client.
                if (decision.getSkillId() != null)
                {
                    int skillIdForCast;
                    try
                    {
                        skillIdForCast = Integer.parseInt(decision.getSkillId().trim());
                    }
                    catch (NumberFormatException nfe)
                    {
                        skillIdForCast = -1; // e.g. "HEAL"/"ATTACK" placeholder — not a real skill id
                    }
                    if (skillIdForCast > 0)
                    {
                        if (targetObjId > 0)
                        {
                            steps.add(new FrameStep(
                                PacketCodec.encodeAction(targetObjId, playerX, playerY, playerZ), 0));
                        }
                        steps.add(new FrameStep(PacketCodec.encodeUseSkill(skillIdForCast, false, false), 0));
                    }
                }
                break;

            default:
                // IDLE / LEAVE_COMBAT / AUTO_PLAY / CAMPAIGN — nothing to send.
                break;
        }
        return steps;
    }
}
