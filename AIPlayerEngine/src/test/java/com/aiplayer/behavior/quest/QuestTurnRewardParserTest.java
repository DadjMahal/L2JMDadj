package com.aiplayer.behavior.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.behavior.quest.QuestTurnRewardParser.ItemArg;
import com.aiplayer.behavior.quest.QuestTurnRewardParser.RewardReceipt;

/**
 * S3-T03 — locks turn-in + reward-receipt detection. Fixtures are REAL datapack html
 * (Q00101_SwordOfSolidarity: 30283-06 reward presentation, 30008-04 deliver step) and the
 * same-shaped SystemMessage item-name args the engine's PacketLogger parses (type 3).
 */
class QuestTurnRewardParserTest
{
    // REAL 30283-06.htm — Altran presents the completed Sword of Solidarity.
    private static final String REAL_TURN_IN_HTML =
        "Blacksmith Altran: Ta da, it's finished! This is the legendary Sword of Solidarity. "
            + "Isn't it beautiful? ... I will now present you with this sword. "
            + "Please do not refuse, and accept it.";

    // REAL 30008-03/04 deliver step — NOT a turn-in.
    private static final String REAL_DELIVER_HTML =
        "Grand Master Roien: Please deliver my letter to Blacksmith Altran. You can find him at the village forge.";

    // ================================================================

    @Test
    void recognisesRealTurnInDialog()
    {
        assertTrue(QuestTurnRewardParser.isTurnInDialog(REAL_TURN_IN_HTML),
            "\"present you with ... accept it\" is the quest-complete reward page");
    }

    @Test
    void rejectsDeliverStepAsTurnIn()
    {
        assertFalse(QuestTurnRewardParser.isTurnInDialog(REAL_DELIVER_HTML), "deliver step is not turn-in");
    }

    @Test
    void rejectsBlankHtml()
    {
        assertFalse(QuestTurnRewardParser.isTurnInDialog(null));
        assertFalse(QuestTurnRewardParser.isTurnInDialog("   "));
        assertFalse(QuestTurnRewardParser.isTurnInDialog("<br><br>"));
    }

    @Test
    void extractsItemNameReceipts()
    {
        List<RewardReceipt> r = QuestTurnRewardParser.itemReceipts(realParams("Sword of Solidarity"));
        assertEquals(1, r.size());
        assertEquals("Sword of Solidarity", r.get(0).itemName);
        assertEquals("Sword of Solidarity", r.get(0).toString());
    }

    @Test
    void extractsMultipleItemReceipts()
    {
        ItemArg sword = new ItemArg(3, "Sword of Solidarity");
        ItemArg pots = new ItemArg(3, "Lesser Healing Potion");
        List<RewardReceipt> r = QuestTurnRewardParser.itemReceipts(Arrays.asList(sword, pots));
        assertEquals(2, r.size());
        assertEquals("Sword of Solidarity", r.get(0).itemName);
        assertEquals("Lesser Healing Potion", r.get(1).itemName);
    }

    @Test
    void ignoresNonItemArgs()
    {
        ItemArg sword = new ItemArg(3, "Sword of Solidarity");
        ItemArg number = new ItemArg(6, "100");      // SM_TYPE_LONG_NUMBER — a count, not an item
        List<RewardReceipt> r = QuestTurnRewardParser.itemReceipts(Arrays.asList(number, sword));
        assertEquals(1, r.size());
        assertEquals("Sword of Solidarity", r.get(0).itemName);
    }

    @Test
    void emptyParamsYieldNoReceipt()
    {
        assertTrue(QuestTurnRewardParser.itemReceipts(null).isEmpty());
        assertTrue(QuestTurnRewardParser.itemReceipts(Collections.emptyList()).isEmpty());
        assertTrue(QuestTurnRewardParser.itemReceipts(
            Arrays.asList(new ItemArg(6, "7"), new ItemArg(3, "  "))).isEmpty(),
            "blank item name is not a receipt");
    }

    // Real-shaped SYSMSG param list from PacketLogger's decode of SM_TYPE_ITEM_NAME (type 3).
    private static List<ItemArg> realParams(String itemName)
    {
        return new java.util.ArrayList<>(Arrays.asList(new ItemArg(3, itemName)));
    }
}