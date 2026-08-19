package com.aiplayer.protocol;

import com.aiplayer.net.AIPlayer;

/**
 * Protocol Packet Factory
 * Generates common L2JMobius protocol packets for AI actions
 */
public class ProtocolFactory {

    /**
     * Generate packet for movement action
     */
    public static ProtocolPacket createMovementPacket(AIPlayer player, int destX, int destY, int destZ) {
        return ProtocolPacket.createMoveTo(destX, destY, destZ, 0);
    }

    /**
     * Generate packet for chat action
     */
    public static ProtocolPacket createChatPacket(AIPlayer player, String message) {
        return ProtocolPacket.createChat(message, 0); // 0 = normal chat
    }

    /**
     * Generate packet for attack action
     */
    public static ProtocolPacket createAttackPacket(AIPlayer player, int targetId) {
        return ProtocolPacket.createAttack(targetId);
    }

    /**
     * Generate packet for stop movement
     */
    public static ProtocolPacket createStopMovementPacket(AIPlayer player) {
        return new ProtocolPacket(ProtocolPacket.PacketType.STOP_MOVEMENT);
    }

    /**
     * Generate packet for trade request
     */
    public static ProtocolPacket createTradeRequestPacket(AIPlayer player, int targetObjectId) {
        // Would need to serialize target object ID
        return new ProtocolPacket(ProtocolPacket.PacketType.TRADE_REQUEST);
    }
}
