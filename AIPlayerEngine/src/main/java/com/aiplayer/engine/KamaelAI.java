package com.aiplayer.engine;
import java.util.logging.Logger;

public class KamaelAI {
    private static final Logger LOGGER = Logger.getLogger(KamaelAI.class.getName());

    public enum KamaelAbility { BASE_CRAFTING, INVENTORY_EXPANSION, CLASS_CHANGE, ARMOR_PIERCING,
        DUAL_WIELD, EXTREME_PIERCING, RAID_BLESSING, CHARGING_BLOW, DESTINY,
        RAPTURING_BLOW, HARMONIC_DAMAGE, WARDING_RAIN, CONVERGENCE }

    public static KamaelAbility predictAbilityUse(int level, String currentClass) {
        if (level < 37) return KamaelAbility.BASE_CRAFTING;
        switch (currentClass) {
            case "Fighter": return KamaelAbility.RAID_BLESSING;
            case "Warrior": return KamaelAbility.CHARGING_BLOW;
            case "Rogue": return KamaelAbility.DUAL_WIELD;
            default: return KamaelAbility.WARDING_RAIN;
        }
    }
}
