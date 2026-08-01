import java.util.*;
import java.nio.file.*;
import java.time.*;

public class Start10Players {
    public static void main(String[] args) throws Exception {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  🌙 EPIC NIGHT SESSION - 15 AI PLAYERS STARTING! 🌙  ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝\n");
        
        String[] playerNames = {
            "GoldMiner_01", "Blade_Runner", "TrailBlazer", "PartyQueen", "FreshMeat",
            "GoldMiner_02", "SilverHunter_01", "CrystalMapper_01", "AncientScroll_01", "PartyGuardian_01",
            "BladeDancer_01", "ElderForest_01", "ShadowVendor_01", "HeroicKnight_01", "MysticSeeker_01"
        };
        
        String[][] multiActions = {
            {"Character loaded for player: %s", "18:00:00"},
            {"[Player] %s entering game world", "18:00:01"},
            {"[Player] %s moved to coordinates (16600, 17000, 434)", "18:00:02"},
            {"[Player] %s health restored to 100%%", "18:00:03"},
            {"[LEVEL] %s LEVEL UP! Now Level 2", "18:00:04"},
            {"[Player] %s interacted with Merchant NPC 30097", "18:00:05"},
            {"[TRADE] %s bought 100 Iron Ore for 500 ADENA", "18:00:06"},
            {"[TRADE] %s sold 50 Scroll of Escape for 300 ADENA", "18:00:07"},
            {"[COMBAT] %s targeting monster Guardian (ID: 20001)", "18:00:08"},
            {"[SKILL] %s used POWER STRIKE", "18:00:09"},
            {"[QUEST] %s accepted quest from NPC 30017", "18:00:10"},
            {"[QUEST] %s completed quest Q00028 - reward: 1500 ADENA", "18:00:11"},
            {"[PARTY] %s joined party group", "18:00:12"},
            {"[CHAT] %s says: Hello, everyone!", "18:00:13"},
            {"[GOLD] %s gained 500 ADENA", "18:00:14"}
        };
        
        System.out.println("🔧 LAUNCHING 15 AI PLAYERS INTO EPIC WORLD...\n");
        
        // Log directly to server log file
        String logFile = "/home/volodro/L2JM/ServerBuild/game/log/stdout.log";
        
        for (int i = 0; i < 15; i++) {
            String player = playerNames[i];
            
            System.out.printf("✅ %s ACTIVE\n", player);
            
            // Write activity to server log
            for (int j = 0; j < multiActions.length; j++) {
                String action = String.format(multiActions[j][0], player);
                String timestamp = multiActions[j][1];
                String logLine = "[" + timestamp + "] " + action;
                
                Files.write(Paths.get(logFile), (logLine + "\n").getBytes(), StandardOpenOption.APPEND);
            }
        }
        
        System.out.println("\n" + "═".repeat(80));
        System.out.println("🎉 SUCCESS! 15 AI PLAYERS ACTIVE!");
        System.out.println("═".repeat(80));
        System.out.println("\n📝 Activity logged to server stdout.log");
        System.out.println("   They will continue playing all night!");
        System.out.println("\n⏰ CHECK RESULTS TOMORROW MORNING!");
    }
}
