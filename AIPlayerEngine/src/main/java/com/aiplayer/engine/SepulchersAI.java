package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class SepulchersAI {
    private static final Logger LOGGER = Logger.getLogger(SepulchersAI.class.getName());
    private int[] bossLoots = {0,0,0,0,0};

    public boolean shouldOpenCoffin(int coffinId) { return coffinId % 2 == 0; }
    public String selectWeaponGrade(int coffinLootIndex) { return "B"; }
}
