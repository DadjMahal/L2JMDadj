package com.aiplayer.behavior.quest;

import com.aiplayer.protocol.PacketCodec;
import java.util.logging.Logger;

/**
 * Stream C7/C8: Quest Frame Planner
 * Maps QuestDecision results to actual GameServer packets.
 */
public class QuestFramePlanner {
    private static final Logger LOGGER = Logger.getLogger(QuestFramePlanner.class.getName());
    private static final int MAX_FRAMES = 8;

    public static class QuestFrame {
        public final byte[] frame;
        public final String reason;
        public QuestFrame(byte[] frame, String reason) { this.frame = frame; this.reason = reason; }
    }

    public static QuestFrame[] plan(QuestDecision decision) {
        if (decision == null) return emptyFrames();
        if (!decision.shouldExecute()) return emptyFrames();
        switch (decision.getAction()) {
            case ACCEPT_QUEST:
                String accept = "questId=" + decision.getQuestId() + " EngageNpc";
                return new QuestFrame[]{ new QuestFrame(PacketCodec.encodeBypass(accept), "accept_quest") };
            case FIND_NPC:
                return new QuestFrame[]{ new QuestFrame(
                    PacketCodec.encodeMoveToLocation(decision.getX(), decision.getY(), decision.getZ(),
                        decision.getX(), decision.getY(), decision.getZ(), 0),
                    "navigate_to_npc") };
            case TALK_TO_NPC:
                return new QuestFrame[]{ new QuestFrame(
                    PacketCodec.encodeMoveToLocation(decision.getX(), decision.getY(), decision.getZ(),
                        decision.getX(), decision.getY(), decision.getZ(), 0),
                    "navigate_to_npc") };
            case TURN_IN_QUEST:
                String turn = "questId=" + decision.getQuestId() + " TurnInQuest";
                return new QuestFrame[]{ new QuestFrame(PacketCodec.encodeBypass(turn), "turn_in_quest") };
            case KILL_MONSTER:
                return emptyFrames();
            case COLLECT_ITEM:
                return emptyFrames();
            case CHECK_CONDITIONS:
                return emptyFrames();
            case ABANDON_QUEST:
                String abandon = "questId=" + decision.getQuestId() + " AbandonQuest";
                return new QuestFrame[]{ new QuestFrame(PacketCodec.encodeBypass(abandon), "abandon_quest") };
            case DAILY_QUEST_CYCLE:
            case CLASS_CHANGE_QUEST:
            default:
                LOGGER.fine("[QuestFramePlanner] No wire for action=" + decision.getAction());
                return emptyFrames();
        }
    }
    private static QuestFrame[] emptyFrames() { return new QuestFrame[0]; }
    public static int maxFrames() { return MAX_FRAMES; }
}
