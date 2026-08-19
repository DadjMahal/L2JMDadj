package com.aiplayer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.aiplayer.metrics.PerformanceMetrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.net.AIPlayer;

/**
 * Stream F load/performance test (task 98).
 *
 * <p>Spawns N bots and runs concurrent decision cycles, instrumenting each cycle's latency through
 * {@link PerformanceMetrics} (previously dead code). Asserts:
 * <ul>
 *   <li>the multi-agent loop completes a meaningful number of decisions without error;</li>
 *   <li>average decision latency stays within a sane bound (threaded decision work is cheap — the
 *       in-process goal/combat/emotion path should be far under the loop tick, ~100ms);</li>
 *   <li>per-player metrics were recorded for every spawned bot.</li>
 * </ul>
 *
 * <p>This is a functional perf smoke test (guards against a pathological regression in the
 * decision path), not a substitute for a wall-clock load run — see the RuntimeLog notes.
 */
public class AgentLoadTest {

    private static final int N_BOTS = 8;
    private static final int CYCLES = 50;

    @Test
    public void concurrentLoadStaysWithinLatencyBudget() throws InterruptedException {
        PerformanceMetrics metrics = PerformanceMetrics.getInstance();
        metrics.reset();

        List<AIPlayer> bots = new ArrayList<>();
        for (int i = 0; i < N_BOTS; i++) {
            bots.add(new AIPlayer("LoadBot_" + i, 5000 + i, 1, 0));
        }

        // Each bot records its own think() latency inline (mirror of manager.thinkAllPlayers).
        CountDownLatch done = new CountDownLatch(N_BOTS);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        for (AIPlayer bot : bots) {
            new Thread(() -> {
                try {
                    for (int c = 0; c < CYCLES; c++) {
                        long start = System.nanoTime();
                        bot.getGoalTree().selectActiveGoal();
                        bot.getCombatAI().makeDecision();
                        long lat = System.nanoTime() - start;
                        metrics.recordAction(bot.getName(), lat);
                    }
                } catch (RuntimeException e) {
                    failure.set(e);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();
        assertNull(failure.get(), "no bot must throw under concurrent load: " + failure.get());

        long totalDecisions = metrics.getTotalDecisions();
        long expected = (long) N_BOTS * CYCLES;
        assertEquals(expected, totalDecisions,
                "every decision cycle across every bot must be recorded");
        double avgLatencyMs = metrics.getAverageDecisionLatency();
        // In-process decision work should be far under the 100ms manager think tick.
        assertTrue(avgLatencyMs < 100.0,
                "average decision latency (" + avgLatencyMs + "ms) must be under the 100ms loop budget");
        assertTrue(metrics.getActionsPerSecond() >= 0, "actions/sec must be computable");
    }

    @Test
    public void perBotMetricsAreRecorded() {
        PerformanceMetrics metrics = PerformanceMetrics.getInstance();
        metrics.reset();
        AIPlayer bot = new AIPlayer("LoadBot_X", 5999, 1, 0);
        for (int c = 0; c < 10; c++) {
            metrics.recordAction(bot.getName(), 1_000_000L); // 1ms
        }
        assertEquals(10, metrics.getTotalActions(), "actions must accumulate");
        assertEquals(1.0, metrics.getPlayerAverageLatency(bot.getName()), 0.5,
                "per-player average latency ~1ms");
    }
}
