package com.aiplayer.examples;

import com.aiplayer.engine.AIPlayerSimple;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Magic Show - Spawns 5 AI Players and runs them live!
 */
public class FivePlayerMagicShow {
    private static final Logger LOGGER = Logger.getLogger(FivePlayerMagicShow.class.getName());
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         🤖 AI PLAYER MAGIC SHOW - 5 PLAYERS! 🤖            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        List<AIPlayerSimple> players = new ArrayList<>();
        
        // Spawn 5 AI Players with different personalities
        String[] names = {
            "GoldMiner_01",
            "Blade_Runner",
            "TrailBlazer",
            "PartyQueen",
            "FreshMeat"
        };
        
        int[] accountIds = {1001, 1002, 1003, 1004, 1005};
        
        System.out.println("🔧 SPAWNING 5 AI PLAYERS...\n");
        
        for (int i = 0; i < 5; i++) {
            AIPlayerSimple player = new AIPlayerSimple(names[i], accountIds[i]);
            players.add(player);
            System.out.println("✅ Spawned: " + names[i] + " (Account: " + accountIds[i] + ")");
        }
        
        System.out.println("\n🚀 STARTING AI BEHAVIORS...\n");
        
        // Start all AI players
        for (AIPlayerSimple player : players) {
            player.start();
        }
        
        System.out.println("════════════════════════════════════════════════════════════════╗");
        System.out.println("║  🎉 ALL 5 AI PLAYERS ARE ALIVE AND THINKING! 🎉              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // Run for demonstration
        try {
            System.out.println("📊 AI Activity Log:");
            System.out.println("─────────────────────────────────────────────────────────────────\n");
            
            // Let them run for a bit
            Thread.sleep(30000); // 30 seconds of demo
            
            System.out.println("\n─────────────────────────────────────────────────────────────────");
            System.out.println("🛑 Shutting down AI Players...\n");
            
            // Stop all players
            for (AIPlayerSimple player : players) {
                player.stop();
            }
            
            Thread.sleep(2000);
            
            System.out.println("════════════════════════════════════════════════════════════════╗");
            System.out.println("║  ✅ MAGIC SHOW COMPLETE! 5 AI PLAYERS RAN SUCCESSFULLY! ✅  ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            
        } catch (InterruptedException e) {
            System.out.println("\n🛑 Interrupted! Shutting down...");
            for (AIPlayerSimple player : players) {
                player.stop();
            }
        }
    }
}
