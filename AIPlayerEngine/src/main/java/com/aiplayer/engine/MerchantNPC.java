package com.aiplayer.engine;

/**
 * Merchant NPC Representation
 * Represents a merchant we've identified for trading
 */
public class MerchantNPC {
    private final String npcId;
    private final int x, y, z;
    private final String merchantType;
    private final double buyMultiplier;
    private final double sellMultiplier;

    public MerchantNPC(String npcId, int x, int y, int z) {
        this(npcId, x, y, z, "OTHER", 1.0, 1.0);
    }

    public MerchantNPC(String npcId, int x, int y, int z, String type,
                       double buyMult, double sellMult) {
        this.npcId = npcId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.merchantType = type;
        this.buyMultiplier = buyMult;
        this.sellMultiplier = sellMult;
    }

    public String getNpcId() { return npcId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getMerchantType() { return merchantType; }
    public double getBuyMultiplier() { return buyMultiplier; }
    public double getSellMultiplier() { return sellMultiplier; }

    @Override
    public String toString() {
        return "MerchantNPC{" +
                "npcId='" + npcId + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", type='" + merchantType + '\'' +
                '}';
    }
}
