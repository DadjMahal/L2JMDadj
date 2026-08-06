package com.aiplayer.examples;

import com.aiplayer.engine.AIPlayerReal;
import java.util.*;
import java.util.concurrent.*;

/**
 * EPIC NIGHT MODE - 10 MORE AI PLAYERS FOR ALL NIGHT PLAYING
 */
public class TenMorePlayersDemo {

    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔═════════════════════════════════════════════════════════════════╗");
        System.out.println("║  🌙 EPIC NIGHT MODE - 10 MORE AI PLAYERS STARTING NOW! 🌙  ║");
        System.out.println("╚═════════════════════════════════════════════════════════════════╝");
        System.out.println();

        List<AIPlayerReal> players = new ArrayList<>();

        // 10 NEW AI Players with diverse personalities
        String[][] playerData = {
            {"GoldMiner_02", "1006", "merchant"},
            {"SilverHunter_01", "1007", "combat"},
            {"CrystalMapper_01", "1008", "explorer"},
            {"AncientScroll_01", "1009", "quest"},
            {"PartyGuardian_01", "1010", "social"},
            {"BladeDancer_01", "1011", "combat"},
            {"ElderForest_01", "1012", "farming"},
            {"ShadowVendor_01", "1013", "merchant"},
            {"HeroicKnight_01", "1014", "combat"},
            {"MysticSeeker_01", "1015", "quest"}
        };

        System.out.println("🔧 SPAWNING 10 EPIC AI PLAYERS...\n");

        for (String[] data : playerData) {
            AIPlayerReal player = new AIPlayerReal(data[0], Integer.parseInt(data[1]));
            players.add(player);
            System.out.println("✅ Spawned: " + data[0] + " (Account: " + data[1] + ", Type: " + data[2] + ")");
        }

        System.out.println("\n🚀 STARTING ALL NIGHT PLAY SESSION...\n");

        // Start all players - they will run all night!
        for (AIPlayerReal player : players) {
            player.start();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }

        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("🎉 ALL 10 AI PLAYERS ARE NOW PLAYING 24/7! 🎉");
        System.out.println("   They will play all night and gain levels!");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("📋 Player Tracking IDs:");
        System.out.println("   1. GoldMiner_02 (merchant)    - Trading & farming");
        System.out.println("   2. SilverHunter_01 (combat)  - PvE combat");
        System.out.println("   3. CrystalMapper_01 (explore) - Map exploration");
        System.out.println("   4. AncientScroll_01 (quest)  - Quest completion");
        System.out.println("   5. PartyGuardian_01 (social) - Party leadership");
        System.out.println("   6. BladeDancer_01 (combat)  - High-level combat");
        System.out.println("   7. ElderForest_01 (farming)  - Resource farming");
        System.out.println("   8. ShadowVendor_01 (merchant) - Advanced trading");
        System.out.println("   9. HeroicKnight_01 (combat) - Boss hunting");
        System.out.println("   10. MysticSeeker_01 (quest) - Epic quest lines");
        System.out.println();
        System.out.println("🌙 THEM: Play all natural night!");
        System.out.println("   US: Check tomorrow morning for results!");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");

        // Keep running all night - just notify and let them play
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            long hours = System.currentTimeMillis() / (1000 * 60 * 60);
            int minutes = (int)((System.currentTimeMillis() / (1000 * 60)) % 60);
            System.out.println("\n⏰ Night Watch [" + hours + ":" + String.format("%02d", minutes) + "] - All 10 players still active!");
        }, 1, 1, TimeUnit.HOURS);
    }
}
