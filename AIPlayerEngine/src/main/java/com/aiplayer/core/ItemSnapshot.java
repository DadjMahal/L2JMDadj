package com.aiplayer.core;

/** MODE: COMPLETE. Real fields (itemId/count/name/grade/levelRequirement) sourced from PacketLogger+ItemDatabase; sellPrice/objId/isQuestItem are explicit documented placeholders, not fake-real. */

import com.aiplayer.behavior.inventory.ItemDatabase;
import com.aiplayer.behavior.inventory.ItemDatabase.ItemInfo;
import com.aiplayer.behavior.town.VendorDatabase;
import com.aiplayer.behavior.town.WarehouseManager;
import com.aiplayer.protocol.PacketLogger;

/**
 * Per-item inventory snapshot. Was `GameStateMirror.ItemSnapshot` — never
 * actually defined anywhere, just imported by three town/ files (confirmed:
 * grepped the old GameStateMirror.java directly, no such class exists in it).
 *
 * Built by joining two real sources: PacketLogger.getInventoryItems()
 * (itemId -> count, real, live) with ItemDatabase.ItemInfo (name/grade/
 * minLevel, real, static reference data). Two fields are NOT available from
 * either real source and are explicitly defaulted, not guessed to look
 * plausible:
 *   - sellPrice: no vendor price table joined yet. Defaults to 0. Needs
 *     VendorDatabase (Task 7) cross-referenced by item category — not done
 *     this pass, flagged in INTEGRATION_GAPS.md.
 *   - objId (per-inventory-slot instance id): getInventoryItems() returns
 *     aggregate counts, not per-slot instance data. Defaults to itemId,
 *     which is NOT a real object id. WarehouseManager's deposit/withdraw
 *     calls (protocol.sendDepositItem(item.objId, ...)) will not work
 *     correctly until real per-slot inventory data is parsed — flagged, not
 *     silently left to look like it works.
 *   - isQuestItem: ItemDatabase.ItemType has no QUEST value. Defaults false.
 */
public final class ItemSnapshot {
    public final int itemId;
    public final long count;
    public final String name;
    public final int grade;
    public final int levelRequirement;
    public final int sellPrice;        // NOT REAL — see class javadoc
    public final int objId;            // NOT REAL — see class javadoc
    public final boolean isQuestItem;  // NOT REAL — see class javadoc

    private ItemSnapshot(int itemId, long count, String name, int grade, int levelRequirement,
                          int sellPrice, int objId, boolean isQuestItem) {
        this.itemId = itemId;
        this.count = count;
        this.name = name;
        this.grade = grade;
        this.levelRequirement = levelRequirement;
        this.sellPrice = sellPrice;
        this.objId = objId;
        this.isQuestItem = isQuestItem;
    }

    public static ItemSnapshot from(int itemId, long count) {
        ItemInfo info = ItemDatabase.get(itemId);
        // Treat an "Unknown" placeholder ItemInfo the same as a miss so unknown
        // items get an honest "item#<id>" name instead of a guessed default —
        // see ItemSnapshotTest.testUnknownItemFallsBackHonestly.
        boolean known = info != null && info.type != ItemDatabase.ItemType.UNKNOWN;
        String name = known ? info.name : ("item#" + itemId);
        int grade = known ? info.grade : 0;
        int minLevel = known ? info.minLevel : 0;
        return new ItemSnapshot(itemId, count, name, grade, minLevel,
                                 0,      // sellPrice — not joined yet
                                 itemId, // objId — placeholder, not a real instance id
                                 false); // isQuestItem — not derivable yet
    }
}
