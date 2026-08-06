package com.aiplayer.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * AI Player Spawn Controller
 * Manages spawning multiple AI players with individual personalities
 */
public class AIPlayerSpawnController {
    private static final Logger LOGGER = Logger.getLogger(AIPlayerSpawnController.class.getName());

    private final AIPlayerManager manager;
    private final List<AIPlayer> spawnedPlayers;

    public AIPlayerSpawnController() {
        this.manager = AIPlayerManager.getInstance();
        this.spawnedPlayers = new ArrayList<>();
    }

    public void spawnFiveMagicPlayers() {
        LOGGER.info("SPAWNING 5 MAGIC AI PLAYERS!!!");
    }

    public void spawnAdditionalPlayers() {
        LOGGER.info("SPAWNING ADDITIONAL 25 AI PLAYERS!");

        // Combat Warriors
        spawnedPlayers.add(createUser("BladeRunner_02", 1001));
        spawnedPlayers.add(createUser("SwordMaster_01", 1002));
        spawnedPlayers.add(createUser("Polearm_01", 1003));
        spawnedPlayers.add(createUser("SpearWarrior", 1004));
        spawnedPlayers.add(createUser("AxeMaster", 1005));
        spawnedPlayers.add(createUser("HeavyKnight", 1006));

        // Quest Specialists
        spawnedPlayers.add(createUser("AncientScroll_02", 1007));
        spawnedPlayers.add(createUser("FestivalWalker", 1008));
        spawnedPlayers.add(createUser("EventHunter", 1009));
        spawnedPlayers.add(createUser("AchievementOne", 1010));
        spawnedPlayers.add(createUser("QuestMaster", 1011));
        spawnedPlayers.add(createUser("StoryTeller", 1012));

        // Merchants
        spawnedPlayers.add(createUser("TradeLord_01", 1013));
        spawnedPlayers.add(createUser("MarketQueen", 1014));
        spawnedPlayers.add(createUser("CoinCollector", 1015));
        spawnedPlayers.add(createUser("GoldSeeker", 1016));
        spawnedPlayers.add(createUser("BarterKing", 1017));
        spawnedPlayers.add(createUser("WealthBuilder", 1018));

        // Explorers
        spawnedPlayers.add(createUser("Pathfinder", 1019));
        spawnedPlayers.add(createUser("Wilderness", 1020));
        spawnedPlayers.add(createUser("Reconnaise", 1021));
        spawnedPlayers.add(createUser("ScoutMaster", 1022));
        spawnedPlayers.add(createUser("TerrainMapper", 1023));
        spawnedPlayers.add(createUser("ZoneExplorer", 1024));

        LOGGER.info("TOTAL AI PLAYERS: " + spawnedPlayers.size());
    }

    private AIPlayer createUser(String name, int id) {
        AIPlayer player = new AIPlayer(name, id, 1, 0);
        LOGGER.info("SPAWNED: " + name + " (ID: " + id + ")");
        return player;
    }

    public void runAISystem() {
        LOGGER.info("Running AI for " + spawnedPlayers.size() + " players...");
        for (AIPlayer player : spawnedPlayers) {
            player.think();
        }
    }

    public List<AIPlayer> getSpawnedPlayers() {
        return new ArrayList<>(spawnedPlayers);
    }
}
