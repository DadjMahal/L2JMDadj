package com.aiplayer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.core.EngineConfig.ConfigIssue;
import com.aiplayer.behavior.combat.CombatConfig;
import com.aiplayer.behavior.quest.QuestConfig;

/**
 * EB-11 — locks config VALIDATION (engine.* cross-field/range checks) and the SINGLE-SOURCE
 * conversion (CombatConfig/QuestConfig now read through the one AIConfiguration store).
 */
class EngineConfigTest
{
    private static final EngineConfig EC = EngineConfig.getInstance();

    @Test
    void saneDefaultsProduceNoIssues()
    {
        assertTrue(EC.validate().isEmpty(), "pristine config must be sane: " + EC.validate());
    }

    @Test
    void negativeIdleRouteIsCaught()
    {
        AIConfiguration.getInstance().setProperty("engine.movement.idle_route_ms", "-1");
        List<EngineConfig.ConfigIssue> issues = EC.validate();
        assertTrue(issues.stream().anyMatch(i -> i.field.equals("movement.idle_route_ms")));
        cleanUpDefault("engine.movement.idle_route_ms");
    }

    @Test
    void maxRadiusSmallerThanMinIsCaught()
    {
        AIConfiguration.getInstance().setProperty("engine.movement.min_radius", "9000");
        AIConfiguration.getInstance().setProperty("engine.movement.max_radius", "100");
        List<ConfigIssue> issues = EC.validate();
        assertTrue(issues.stream().anyMatch(i -> i.field.equals("movement.max_radius")));
        cleanUpDefault("engine.movement.min_radius");
        cleanUpDefault("engine.movement.max_radius");
    }

    @Test
    void outlierPercentOutsideRangeIsCaught()
    {
        AIConfiguration.getInstance().setProperty("engine.reaction_outlier_pct", "250");
        List<ConfigIssue> issues = EC.validate();
        assertTrue(issues.stream().anyMatch(i -> i.field.equals("reaction_outlier_pct")));
        cleanUpDefault("engine.reaction_outlier_pct");
    }

    @Test
    void combatConfigReadsThroughAIConfiguration()
    {
        // Same key/value a caller would set in the ONE loaded store is seen by CombatConfig.
        AIConfiguration.getInstance().setProperty("combat.detect_range", "7777");
        assertEquals(7777, CombatConfig.getInstance().getDetectRange());
        AIConfiguration.getInstance().setProperty("combat.detect_range", "3000");
    }

    @Test
    void questConfigReadsThroughAIConfiguration()
    {
        AIConfiguration.getInstance().setProperty("quest.search_radius", "4242");
        assertEquals(4242, QuestConfig.getInstance().getSearchRadius());
        AIConfiguration.getInstance().setProperty("quest.search_radius", "2000");
    }

    private static void cleanUpDefault(String key)
    {
        AIConfiguration.getInstance().setProperty(key, "");
    }
}