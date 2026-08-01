package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class SiegeWeaponAI {
    private static final Logger LOGGER = Logger.getLogger(SiegeWeaponAI.class.getName());
    
    public enum WeaponType { CATAPULT, RAM, BALLISTA, BOMBARDIER }
    public enum TargetType { GATE, WALL, TOWER, CHARACTER }
    
    public static class WeaponAssignment {
        public final WeaponType weapon;
        public final TargetType target;
        public final int power;
        
        public WeaponAssignment(WeaponType w, TargetType t, int p) {
            weapon = w; target = t; power = p;
        }
    }
    
    public static WeaponAssignment assignWeapon(WeaponType weapon, TargetType target, int armor) {
        int power = 0;
        if (target == TargetType.WALL || target == TargetType.TOWER) {
            power = Math.max(10, 100 - armor);
        } else if (target == TargetType.GATE) {
            power = Math.max(20, 80 - armor);
        } else {
            power = 50;
        }
        return new WeaponAssignment(weapon, target, power);
    }
    
    public static boolean shouldFire(WeaponType weapon, int targetDistance, int cooldown) {
        if (cooldown > 0) return false;
        if (weapon == WeaponType.CATAPULT && targetDistance > 300) return false;
        if (weapon == WeaponType.BALLISTA && targetDistance > 500) return false;
        return true;
    }
}
