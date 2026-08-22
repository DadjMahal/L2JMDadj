package com.aiplayer.behavior.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * S3-T03 — turn-in + reward-receipt detection (pure, deterministic).
 *
 * <p>The server signals a completed quest with two live events the engine already parses:
 * <ol>
 *   <li>the TURN-IN NPC dialog html (the reward-presentation page, e.g. datapack
 *       30283-06.htm: "<i>...I will now present you with this sword... accept</i>");</li>
 *   <li>the follow-up SystemMessage whose params carry item-name args
 *       (PacketLogger type {@code SM_TYPE_ITEM_NAME=3} → {@code Arg(3, itemName)}).</li>
 * </ol>
 * This parser turns those raw signals into a structured reward receipt, and says nothing
 * (never invents) when the signals are absent.
 */
public final class QuestTurnRewardParser
{
    /** Minimal item-param shape so the pure parser never couples to PacketLogger. */
    public static final class ItemArg
    {
        public final int type;
        public final String rendered;

        public ItemArg(int type, String rendered)
        {
            this.type = type;
            this.rendered = rendered == null ? "" : rendered;
        }
    }

    /** A detected reward line. */
    public static final class RewardReceipt
    {
        public final String itemName;
        public final int count;

        public RewardReceipt(String itemName, int count)
        {
            this.itemName = itemName;
            this.count = count;
        }

        @Override
        public String toString()
        {
            return count > 1 ? itemName + " x" + count : itemName;
        }
    }

    private static final String[] TURN_IN_HINTS = {
        "present you with", "present it to you", "take this as a reward", "take it as a reward",
        "accept it", "accept this", "is finished", "here is your reward", "reward you"
    };

    private QuestTurnRewardParser()
    {
    }

    /**
     * True when the dialog html is a turn-in / reward-presentation page (the quest-complete
     * point where the giver hands over rewards).
     */
    public static boolean isTurnInDialog(String html)
    {
        String text = QuestObjectiveParser.toPlainText(html);
        if (text == null || text.isEmpty())
        {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String hint : TURN_IN_HINTS)
        {
            if (lower.contains(hint))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract the reward items from a system-message's params: any ITEM_NAME (type 3) arg whose
     * rendered item name is non-blank becomes a reward receipt. Returns empty when no item args.
     */
    public static List<RewardReceipt> itemReceipts(List<ItemArg> params)
    {
        if (params == null || params.isEmpty())
        {
            return Collections.emptyList();
        }
        List<RewardReceipt> receipts = new ArrayList<>();
        for (ItemArg arg : params)
        {
            if (arg.type == 3 && arg.rendered != null && !arg.rendered.isBlank())
            {
                receipts.add(new RewardReceipt(arg.rendered, 1));
            }
        }
        return receipts;
    }
}