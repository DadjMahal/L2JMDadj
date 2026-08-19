package com.aiplayer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.behavior.GoalTree;
import com.aiplayer.core.AIPlayerManager;
import com.aiplayer.net.AIPlayer;

/**
 * Stream F integration test (tasks 92, 97) — multi-agent scale & agent isolation.
 *
 * <p>Spawns N real {@link AIPlayer} instances concurrently and asserts per-instance isolation
 * (each bot's emotion/goals/learning are independent) while collective singletons are shared by
 * design, plus concurrency safety and graceful shutdown. Does NOT require a live server.
 */
public class MultiAgentIntegrationTest {

    private static final int N_BOTS = 5;

    private List<AIPlayer> spawnBots(int n) {
        List<AIPlayer> bots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            bots.add(new AIPlayer("IsoBot_" + i, 1000 + i, 1, 0));
        }
        return bots;
    }

    @Test
    public void eachBotHasIndependentPerInstanceSubsystems() {
        List<AIPlayer> bots = spawnBots(N_BOTS);
        for (AIPlayer b : bots) {
            assertNotNull(b.getPersonality(), "personality must exist");
            assertNotNull(b.getEmotions(), "emotions must exist");
            assertNotNull(b.getGoalTree(), "goalTree must exist");
            assertNotNull(b.getDeepLearning(), "deepLearning must exist");
            assertNotNull(b.getActivityScheduler(), "scheduler must exist");
        }
        assertNotSame(bots.get(0).getEmotions(), bots.get(1).getEmotions(),
                "two bots must NOT share an EmotionalState");
        assertNotSame(bots.get(0).getGoalTree(), bots.get(1).getGoalTree(),
                "two bots must NOT share a GoalTree");
        assertNotSame(bots.get(0).getDeepLearning(), bots.get(1).getDeepLearning(),
                "two bots must NOT share a DeepLearningCore");
    }

    @Test
    public void mutatingOneBotsEmotionDoesNotAffectAnother() {
        List<AIPlayer> bots = spawnBots(2);
        AIPlayer a = bots.get(0);
        AIPlayer b = bots.get(1);
        double aFrustrationBefore = a.getEmotions().getFrustrationLevel();
        double bFrustrationBefore = b.getEmotions().getFrustrationLevel();
        a.getCombatAI().onDeath();
        assertTrue(a.getEmotions().getFrustrationLevel() > aFrustrationBefore,
                "bot A frustration must rise after its death");
        assertEquals(bFrustrationBefore, b.getEmotions().getFrustrationLevel(), 1e-9,
                "bot B frustration must be UNCHANGED by bot A's death (isolation)");
    }

    @Test
    public void learningIsIsolatedPerBot() {
        List<AIPlayer> bots = spawnBots(2);
        AIPlayer a = bots.get(0);
        AIPlayer b = bots.get(1);
        int aBefore = a.getAdaptiveLearner().getCombatActionsLearned();
        int bBefore = b.getAdaptiveLearner().getCombatActionsLearned();
        a.getCombatAI().onKill("Wolf", 100L);
        assertEquals(aBefore + 1, a.getAdaptiveLearner().getCombatActionsLearned(),
                "bot A must record its kill");
        assertEquals(bBefore, b.getAdaptiveLearner().getCombatActionsLearned(),
                "bot B must NOT record bot A's kill (per-bot PatternMemory)");
        assertTrue(a.getDeepLearning().getMemory().size() > 0, "A learned a pattern");
        assertEquals(0, b.getDeepLearning().getMemory().size(), "B's memory must stay empty");
    }

    @Test
    public void collectiveKnowledgeIsSharedAcrossBots() {
        List<AIPlayer> bots = spawnBots(2);
        assertSame(bots.get(0).getCollectiveKnowledge(), bots.get(1).getCollectiveKnowledge(),
                "CollectiveKnowledge must be the shared singleton");
        int before = bots.get(0).getCollectiveKnowledge().totalKnowledge();
        bots.get(0).getCollectiveKnowledge().share("BotA", "hunting_spot", "spot1", "good", 1.0);
        assertTrue(bots.get(1).getCollectiveKnowledge().totalKnowledge() > before,
                "a share by bot A must be visible to bot B (shared collective)");
    }

    @Test
    public void concurrentDecisionCyclesCompleteSafely() throws InterruptedException {
        List<AIPlayer> bots = spawnBots(N_BOTS);
        int iterations = 20;
        CountDownLatch done = new CountDownLatch(N_BOTS);
        AtomicInteger errors = new AtomicInteger(0);
        for (AIPlayer bot : bots) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        bot.getGoalTree().selectActiveGoal();
                        bot.getCombatAI().makeDecision();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();
        assertEquals(0, errors.get(), "no bot must throw during concurrent decision cycles");
    }

    @Test
    public void managerGracefulShutdownClearsAllPlayers() {
        AIPlayerManager mgr = AIPlayerManager.getInstance();
        mgr.spawnAIPlayer("IsolationBot_A", 9001, 1, 0, 2);
        mgr.spawnAIPlayer("IsolationBot_B", 9002, 1, 0, 3);
        assertTrue(mgr.getAIPlayerCount() >= 2, "manager must hold the spawned bots");
        mgr.shutdownAll();
        assertEquals(0, mgr.getAIPlayerCount(), "shutdownAll must clear all managed players");
        new java.io.File("aiplayer-IsolationBot_A.state").delete();
        new java.io.File("aiplayer-IsolationBot_B.state").delete();
    }

}
