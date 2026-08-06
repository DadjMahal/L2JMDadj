package com.aiplayer.engine;
import java.util.logging.Logger;

public class KarmaManager {
    private static final Logger LOGGER = Logger.getLogger(KarmaManager.class.getName());
    private int karma = 0;

    public void awardKarma(int amount) { karma += amount; }
    public void penalizeKarma(int amount) { karma -= amount; }
    public boolean isInSafeZone(int x, int y) { return x > -2000 && x < 2000 && y > -2000 && y < 2000; }
    public int getKarma() { return karma; }
    public boolean canPK() { return karma < 0; }
}
