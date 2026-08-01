package com.aiplayer.engine;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

public class PvPSkillRotation {
    private static final Logger LOGGER = Logger.getLogger(PvPSkillRotation.class.getName());
    
    public enum SkillType { HIGH_BURST, CONTROL, SHOOT, DOOM, SILence }
    
    public static List<SkillType> getOptimalRotation(String enemyClass, boolean hasBurstReady) {
        List<SkillType> rotation = new ArrayList<>();
        switch(enemyClass) {
            case "Wizard": case "Cleric": rotation.addAll(List.of(SkillType.SILence, SkillType.HIGH_BURST, SkillType.CONTROL)); break;
            case "Warrior": case "Gladiator": rotation.addAll(List.of(SkillType.CONTROL, SkillType.HIGH_BURST, SkillType.SHOOT)); break;
            default: rotation.addAll(List.of(SkillType.HIGH_BURST, SkillType.SHOOT, SkillType.SHOOT));
        }
        return rotation;
    }
}
