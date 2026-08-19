package com.aiplayer.behavior.town;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.behavior.town.MerchantAI;
import com.aiplayer.behavior.town.MerchantDecision;
import com.aiplayer.net.AIPlayer;

/**
 * Stream E slice 1 tests (tasks 78, 79, 86, 87).
 *
 * <p>Proves the economy wiring is genuine: AIPlayer exposes the social/economy subsystems (they
 * had no getters before), ItemList(0x1B) actually extracts adena + item inventory (it only counted
 * items before), and MerchantAI decides on REAL inventory/adena instead of the removed
 * {@code Math.random()} mocks. Also proves trade outcomes feed MarketEngine + emotion +
 * reinforcement.
 */
public class StreamETradeTest {

    private AIPlayer newPlayer() {
        return new AIPlayer("StreamEBot", 0, 1, 0); // AGGRESSIVE
    }

    @Test
    public void socialAndEconomySubsystemsAreExposed() {
        AIPlayer p = newPlayer();
        assertNotNull(p.getCollectiveKnowledge(), "must expose CollectiveKnowledge");
        assertNotNull(p.getSwarmCoordinator(), "must expose SwarmCoordinator");
        assertNotNull(p.getDiplomacy(), "must expose DiplomacyEngine");
        assertNotNull(p.getMarketEngine(), "must expose MarketEngine");
        assertNotNull(p.getEconomicEngine(), "must expose EconomicEngine");
        assertNotNull(p.getNetWorthOptimizer(), "must expose NetWorthOptimizer");
    }

    @Test
    public void itemListParseExtractsAdenaAndItems() {
        // Build a full ItemList(0x1B) packet as the live reader would deliver it to logPacket:
        // [size:short LE][opcode:byte=0x1B][showWindow:short][itemCount:short] + 3 items x 32 bytes.
        ByteBuffer body = ByteBuffer.allocate(4 + 3 * 32).order(ByteOrder.LITTLE_ENDIAN);
        body.putShort((short) 1); // showWindow
        body.putShort((short) 3); // itemCount
        writeItem(body, 111, 57, 12345); // adena stack
        writeItem(body, 222, 186, 2);    // a normal weapon item
        writeItem(body, 333, 57, 777);   // another adena stack (overwrites)
        byte[] payload = body.array();

        int size = 2 /*size field*/ + 1 /*opcode*/ + payload.length;
        ByteBuffer pkt = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        pkt.putShort((short) size);
        pkt.put((byte) com.aiplayer.protocol.PacketLogger.OP_ITEM_LIST);
        pkt.put(payload);

        com.aiplayer.protocol.PacketLogger logger = new com.aiplayer.protocol.PacketLogger("TestBot");
        logger.logPacket(pkt.array());

        assertEquals(777, logger.getAdena(), "adena must equal the last adena stack count");
        assertEquals(777, (long) logger.getInventoryItems().get(57),
                "the adena item map holds the last count for item 57");
        assertEquals(2L, (long) logger.getInventoryItems().get(186), "normal item must be recorded");
        assertEquals(2, logger.getInventoryItems().size(), "two distinct item ids (57, 186)");
    }

    private void writeItem(ByteBuffer buf, int objId, int itemId, long count) {
        buf.putShort((short) 0); // type1
        buf.putInt(objId);
        buf.putInt(itemId);
        buf.putInt((int) count);
        buf.putShort((short) 20); // type2 (weapon)
        buf.putShort((short) 0);  // coobjectId
        buf.putInt(0);            // bodypart
        buf.putShort((short) 0);  // enchant
        buf.putShort((short) 0);  // customType1
        buf.putShort((short) 0);  // customType2
    }

    @Test
    public void merchantSellsWhenInventoryFull() {
        AIPlayer p = newPlayer();
        MerchantAI m = p.getMerchantAI();
        // Full inventory (> 90) + some adena (> emergency 1000) => SELL_ITEM.
        m.getPacketLogger().setInventoryUsagePercent(95);
        m.getPacketLogger().setAdena(5000);
        MerchantDecision d = m.makeDecision();
        assertTrue(d.getAction() == MerchantDecision.Action.SELL_ITEM
                        || d.getAction() == MerchantDecision.Action.EMERGENCY_SELL
                        || d.getAction() == MerchantDecision.Action.INTERACT_MERCHANT
                        || d.getAction() == MerchantDecision.Action.FIND_MERCHANT,
                "full-inventory bot should head to sell, got " + d.getAction());
    }

    @Test
    public void merchantUsesRealInventoryForBuyDecision() {
        AIPlayer p = newPlayer();
        MerchantAI m = p.getMerchantAI();
        // Low inventory (< 30) + plenty of adena (> minBuyAmount 10000) => BUY intent.
        m.getPacketLogger().setInventoryUsagePercent(20);
        m.getPacketLogger().setAdena(50000);
        MerchantDecision d = m.makeDecision();
        assertNotEquals(MerchantDecision.Action.IDLE, d.getAction(),
                "low-inventory wealthy bot must not idle");
        assertTrue(d.getAction() == MerchantDecision.Action.BUY_ITEM
                        || d.getAction() == MerchantDecision.Action.INTERACT_MERCHANT
                        || d.getAction() == MerchantDecision.Action.FIND_MERCHANT,
                "expected a buy/trade intent, got " + d.getAction());
    }

    @Test
    public void tradeOutcomesFeedEconomyEmotionAndReinforcement() {
        AIPlayer p = newPlayer();
        MerchantAI m = p.getMerchantAI();

        // recordPrice -> MarketEngine tracks it.
        m.recordPrice("IronOre", "Gludio", 100, 150);
        assertTrue(p.getMarketEngine().trackedItemCount() >= 1,
                "recordPrice must populate MarketEngine price history");
        assertEquals("Gludio", p.getMarketEngine().findBestSellTown("IronOre"),
                "MarketEngine should know the best sell town");

        // onTradeProfit -> emotion excitement + a learned trade action.
        double excitementBefore = p.getEmotions().getExcitementLevel();
        int tradeLearnedBefore = p.getAdaptiveLearner().getTradeActionsLearned();
        m.onTradeProfit("IronOre", "sell", 50);
        assertTrue(p.getEmotions().getExcitementLevel() > excitementBefore,
                "a profitable trade must raise excitement");
        assertEquals(tradeLearnedBefore + 1, p.getAdaptiveLearner().getTradeActionsLearned(),
                "a profitable trade must register a learned trade action");
    }

}
