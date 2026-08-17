package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ControlRoomAI {
    private static final Logger LOGGER = Logger.getLogger(ControlRoomAI.class.getName());
    private final Map<Integer, Boolean> capturedFlags = new HashMap<>();

    public enum ControlRole { CASTLE_GATE, BOUNDARY, FLAG, CONTROL_ROOM }

    public static class PositionAssignment {
        public final int x, y;
        public final ControlRole role;
        public final boolean isProtected;

        public PositionAssignment(int x, int y, ControlRole role, boolean prot) {
            this.x = x; this.y = y; this.role = role; this.isProtected = prot;
        }
    }

    public PositionAssignment getOptimalPosition(ControlRole role, int castleId) {
        switch (role) {
            case CASTLE_GATE: return new PositionAssignment(0, 0, role, false);
            case BOUNDARY: return new PositionAssignment(500, 500, role, false);
            case FLAG: return new PositionAssignment(100, 100, role, true);
            case CONTROL_ROOM: return new PositionAssignment(0, 0, role, true);
            default: return new PositionAssignment(0, 0, ControlRole.BOUNDARY, false);
        }
    }

    public boolean shouldCaptureFlag(int defenderCount, int attackerCount) {
        return attackerCount > defenderCount;
    }

    public void markFlagCaptured(int flagId, int castleId) {
        capturedFlags.put(flagId, true);
        LOGGER.info("Flag " + flagId + " captured at castle " + castleId);
    }
}
