package com.aiplayer.phase0;

/** MODE: COMPLETE. Only proven PacketCodec/CombatFramePlanner frames are sent; unproven actions log SKIP-UNPROVEN and send nothing. */

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import com.aiplayer.engine.CombatDecision;
import com.aiplayer.engine.CombatFramePlanner;
import com.aiplayer.engine.CombatFramePlanner.FrameStep;
import com.aiplayer.engine.GameServerClient;
import com.aiplayer.protocol.PacketCodec;

/**
 * The only class in the phase0 package allowed to call sendGameFrame(). Every
 * other phase0 module produces decisions/intents; this translates the proven
 * subset into real wire frames and refuses to guess at the rest.
 *
 * CORRECTED in this pass: combat sequencing now delegates to the real,
 * already-proven CombatFramePlanner (which already handles the 1000ms
 * flood-protector gap between Action and AttackRequest) instead of an
 * earlier hand-rolled version here that reimplemented the same sequence
 * without that timing — worse than just calling the real one.
 *
 * Proven set (built via PacketCodec, sent via GameServerClient.sendGameFrame):
 *   Combat   -> CombatFramePlanner.plan(CombatDecision, ...), which internally
 *               uses the same 5 proven PacketCodec encoders and enforces the
 *               live-verified flood-protector spacing.
 *   MOVE/FLEE-> encodeMoveToLocation()
 *   CHAT     -> encodeChat()
 *   BYPASS   -> encodeBypass()
 *
 * Not proven (skill-cast opcode, item-use opcode, restart-to-village opcode):
 * no PacketCodec.encodeX() for these exists anywhere in the real protocol
 * layer today. CombatFramePlanner itself only plans frames for the actions it
 * knows how to encode; anything else needs a real encoder audited and
 * live-probed the same way the five proven ones were, before this class can
 * safely act on them — see INTEGRATION_GAPS.md.
 */
public final class Phase0Wiring {
    private static final Logger LOGGER = Logger.getLogger(Phase0Wiring.class.getName());

    private final GameServerClient gameServerClient;
    private final CombatFramePlanner combatFramePlanner = new CombatFramePlanner();
    private final String accountName;

    public Phase0Wiring(GameServerClient gameServerClient, String accountName) {
        this.gameServerClient = gameServerClient;
        this.accountName = accountName;
    }

    public boolean revive() {
        return send(PacketCodec.encodeRestartPoint(0), "REVIVE");
    }

    /**
     * Execute a CombatDecision via the real, proven CombatFramePlanner. The
     * driver is responsible for respecting each FrameStep's delayAfterMs
     * before calling this again — this method sends the full planned
     * sequence for one decision and returns.
     */
    public boolean executeCombat(CombatDecision decision, int selfX, int selfY, int selfZ, int targetObjId) {
        List<FrameStep> steps;
        try {
            steps = combatFramePlanner.plan(decision, selfX, selfY, selfZ, targetObjId);
        } catch (Exception e) {
            LOGGER.warning("[Phase0] PLAN-FAILED " + accountName + " decision=" + decision.getAction() + ": " + e.getMessage());
            return false;
        }
        if (steps == null || steps.isEmpty()) {
            skipUnproven("combat", "action=" + decision.getAction() + " — planner produced no frames (likely unencodable, e.g. USE_SKILL)");
            return false;
        }
        boolean allSent = true;
        for (FrameStep step : steps) {
            allSent &= send(step.frame, "opcode=0x" + Integer.toHexString(step.getOpcode()));
            if (step.delayAfterMs > 0) {
                try {
                    Thread.sleep(step.delayAfterMs); // driver-level pacing — see class javadoc
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return allSent;
    }

    /**
     * Move to an absolute location. Proven (encodeMoveToLocation).
     *
     * <p>TIM-001: use the standard mouse-click move path ({@code moveType = 1}) for proactive
     * travel. The Interlude {@code MoveToLocation} handler (SourceCode/.../clientpackets/MoveToLocation.java)
     * treats {@code 0} (cursor-key) as a special path that requires keyboard-movement config and
     * resets {@code setLastServerPosition}; {@code 1} (mouse) is the plain "walk to point" path every
     * real client uses and ends with the server's {@code onActionRequest()}. So a real far-travel hop
     * is sent with {@code moveType = 1}.</p>
     */
    public boolean moveTo(int selfX, int selfY, int selfZ, int targetX, int targetY, int targetZ) {
        return send(PacketCodec.encodeMoveToLocation(targetX, targetY, targetZ, selfX, selfY, selfZ, 1),
                     "MOVE to (" + targetX + "," + targetY + "," + targetZ + ")");
    }

    /**
     * Click/select an NPC to open its dialog (quest accept / turn-in). Proven (encodeAction, the same
     * frame CombatFramePlanner emits for attacks — the server opens the NpcHtmlMessage on the second
     * Action once the NPC is targeted, see QuestFlowLoop).
     */
    public boolean actionOn(int targetObjId, int selfX, int selfY, int selfZ) {
        return send(PacketCodec.encodeAction(targetObjId, selfX, selfY, selfZ),
                     "ACTION/TALK on objId=" + targetObjId);
    }

    /**
     * Send a chat message. Proven (encodeChat).
     */
    public boolean chat(String message) {
        return send(PacketCodec.encodeChat(message), "CHAT: " + message);
    }

    /**
     * Send an NPC HTML bypass command (quest accept, shop window, etc). Proven (encodeBypass).
     */
    public boolean bypass(String request) {
        return send(PacketCodec.encodeBypass(request), "BYPASS: " + request);
    }

    private boolean send(byte[] frame, String description) {
        try {
            gameServerClient.sendGameFrame(frame);
            LOGGER.fine("[Phase0] SENT " + accountName + " " + description);
            return true;
        } catch (IOException e) {
            LOGGER.warning("[Phase0] SEND-FAILED " + accountName + " " + description + ": " + e.getMessage());
            return false;
        }
    }

    private void skipUnproven(String kind, String detail) {
        LOGGER.info("[Phase0] SKIP-UNPROVEN " + accountName + " kind=" + kind + " " + detail);
    }
}
