package com.aiplayer.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.behavior.combat.ShotManager;
import com.aiplayer.behavior.combat.SkillDatabase;
import com.aiplayer.behavior.inventory.ItemDatabase;

/**
 * This test intentionally asserts the CURRENT placeholder defaults (sellPrice=0,
 * isQuestItem=false) as well as the real fields — so that whoever eventually
 * wires real vendor-price/quest-item data in has a failing test telling them
 * exactly what to update, instead of the gap silently staying invisible.
 */
public class ItemSnapshotTest {

    @Test
    public void testKnownItemPullsRealMetadata() {
        // Soulshot: No Grade, itemId 1835 per SkillDatabase/ShotManager's own constants
        ItemSnapshot item = ItemSnapshot.from(1835, 4000L);
        assertEquals(1835, item.itemId);
        assertEquals(4000L, item.count);
        assertNotEquals("item#1835", item.name, "a known item should resolve a real name from ItemDatabase");
    }

    @Test
    public void testUnknownItemFallsBackHonestly() {
        ItemSnapshot item = ItemSnapshot.from(999999, 1L);
        assertEquals("item#999999", item.name, "unknown items get an honest placeholder name, not a guess");
        assertEquals(0, item.grade);
    }

    @Test
    public void testPlaceholderFieldsAreDocumentedNotHidden() {
        ItemSnapshot item = ItemSnapshot.from(1835, 1L);
        // These assert the CURRENT known-placeholder values on purpose (see class javadoc).
        // If this test starts failing because someone wired real data in, that's
        // good — update the test, don't just delete the assertion.
        assertEquals(0, item.sellPrice, "sellPrice has no real source yet — see INTEGRATION_GAPS.md");
        assertFalse(item.isQuestItem, "isQuestItem has no real source yet — see INTEGRATION_GAPS.md");
        assertEquals(item.itemId, item.objId, "objId is a placeholder (=itemId), not a real inventory slot id");
    }
}
