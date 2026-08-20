package com.aiplayer.net;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import com.aiplayer.protocol.GameServerFrameWriter;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.behavior.combat.CombatDecision;
import com.aiplayer.behavior.combat.CombatFramePlanner;
import com.aiplayer.core.CoreWiring;

/**
 * AI Player Connection Manager
 * Handles connection between AIPlayer and L2J server
 *
 * Task 67 Extension - Protocol Integration
 */
public class AIPlayerConnection {
    private static final Logger LOGGER = Logger.getLogger(AIPlayerConnection.class.getName());

    private final AIPlayer aiPlayer;
    private final L2JProtocol protocol;
    /** S10-T03: single shared frame planner (was allocated per call — dedupe vs CoreWiring). */
    private final CombatFramePlanner planner = new CombatFramePlanner();

    /** Real GameServer frame writer (crypt disabled = plaintext); attached once a GS socket is held. */
    private volatile GameServerFrameWriter gameServerWriter;

    public AIPlayerConnection(AIPlayer aiPlayer, String serverHost, int loginPort, int gamePort) {
        this.aiPlayer = aiPlayer;
        this.protocol = new L2JProtocol(aiPlayer, serverHost, loginPort, gamePort);
    }

    /**
     * Connect this AI player to the L2JM server
     */
    public boolean connect(String accountName, String password, int characterId) {
        LOGGER.info("[" + aiPlayer.getName() + "] Attempting connection...");

        boolean result = protocol.connectAndLogin(accountName, password, characterId);

        if (result) {
            aiPlayer.setConnected(true);
            aiPlayer.setLoggedIn(true);
            aiPlayer.setLoginTime(System.currentTimeMillis());
            LOGGER.info("[" + aiPlayer.getName() + "] Successfully connected and logged in");
        } else {
            LOGGER.warning("[" + aiPlayer.getName() + "] Connection/login failed");
        }

        return result;
    }

    /**
     * Send movement command
     */
    public void sendMove(int x, int y, int z) {
        if (!aiPlayer.isLoggedIn()) {
            LOGGER.warning("[" + aiPlayer.getName() + "] Cannot move - not logged in");
            return;
        }
        try {
            protocol.sendMove(x, y, z);
        } catch (IOException e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Move error: " + e.getMessage());
        }
    }

    /**
     * Send attack command
     */
    public void sendAttack(int targetId) {
        if (!aiPlayer.isLoggedIn()) {
            LOGGER.warning("[" + aiPlayer.getName() + "] Cannot attack - not logged in");
            return;
        }
        try {
            protocol.sendAttack(targetId);
        } catch (IOException e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Attack error: " + e.getMessage());
        }
    }

    /**
     * Send chat message
     */
    public void sendChat(String message) {
        if (!aiPlayer.isLoggedIn()) {
            LOGGER.warning("[" + aiPlayer.getName() + "] Cannot chat - not logged in");
            return;
        }
        try {
            protocol.sendChat(message);
        } catch (IOException e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Chat error: " + e.getMessage());
        }
    }

    /**
     * Disconnect the AI player
     */
    public void disconnect() {
        protocol.disconnect();
        aiPlayer.setConnected(false);
        aiPlayer.setLoggedIn(false);
        LOGGER.info("[" + aiPlayer.getName() + "] Disconnected");
    }

    public boolean isConnected() {
        return aiPlayer.isConnected();
    }

    public boolean isLoggedIn() {
        return aiPlayer.isLoggedIn();
    }

    /**
     * Attach a real GameServer frame writer (from a held GS socket) so {@link CombatFramePlanner}
     * frames are actually written to the wire. May be null (no GS socket held yet).
     */
    public void setGameServerWriter(GameServerFrameWriter writer) {
        this.gameServerWriter = writer;
    }

    /**
     * Stream C: plan and send the wire frames for a combat decision.
     * Plans with {@link CombatFramePlanner} (real Action/AttackRequest/MoveToLocation frames), writes
     * them via the attached GameServer frame writer (when present), and respects the flood-protector
     * gap between frames. Logs the frames even when no GS writer is attached yet.
     *
     * @param decision    the combat decision to execute
     * @param targetObjId the selected target's objectId (required for attack actions)
     */
    public void executeCombatDecision(CombatDecision decision, int targetObjId) {
        if (!aiPlayer.isLoggedIn()) {
            LOGGER.warning("[" + aiPlayer.getName() + "] Cannot execute combat - not logged in");
            return;
        }
        List<CombatFramePlanner.FrameStep> steps =
            planner.plan(decision, aiPlayer.getX(), aiPlayer.getY(), aiPlayer.getZ(), targetObjId);

        for (CombatFramePlanner.FrameStep step : steps) {
            try {
                if (gameServerWriter != null) {
                    gameServerWriter.writeFrame(step.frame);
                }
                LOGGER.info("[" + aiPlayer.getName() + "] COMBAT_SEND opcode=0x"
                    + String.format("%02X", step.getOpcode())
                    + " bytes=" + step.frame.length
                    + " (sent=" + (gameServerWriter != null) + ")");
                if (step.delayAfterMs > 0 && gameServerWriter != null) {
                    try {
                        Thread.sleep(step.delayAfterMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IOException e) {
                LOGGER.severe("[" + aiPlayer.getName() + "] Combat send error: " + e.getMessage());
            }
        }
    }

    public AIPlayer getPlayer() {
        return aiPlayer;
    }
}
