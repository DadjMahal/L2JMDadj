package com.aiplayer.phase0.guide;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the per-race / per-profession guide map end-to-end.
 *
 * <p>Every tutorial/newbie node, 1st-class Path quest, 2nd-class Trial and 3rd-class Saga must
 * resolve to a real in-world coordinate; the profession tree must match the Interlude classList
 * (5 races, 18 first / 31 second / 31 third professions); and the "next goal" planner must walk a
 * bot from its start NPC through the whole class-change chain and give it a real idle anchor.
 *
 * <p>Ground data sources: {@code spawns/*.xml} NPC coords, {@code custom_town.xml} centroids,
 * {@code classList.xml} class ids, quest script class lists.
 */
public class RaceGuideTest
{
    private static final int VOID_X = 16600;
    private static final int VOID_Y = 17000;
    private static final int VOID_Z = 434;

    private static void assertReal(QuestNode n)
    {
        assertFalse(n.x == VOID_X && n.y == VOID_Y && n.z == VOID_Z,
                n.signature() + " still uses the void idle coordinate");
        assertFalse(n.x == 0 && n.y == 0 && n.z == 0, n.signature() + " carries no coordinate");
    }

    private static void assertAncestry(int... ids)
    {
        for (int i = 1; i < ids.length; i++)
            assertEquals(ids[i - 1], RaceGuide.profession(ids[i]).orElseThrow().parentClassId,
                    "parent of class " + ids[i]);
    }

    private static Set<Integer> setOf(int... ids)
    {
        Set<Integer> out = new HashSet<>();
        for (int id : ids)
            out.add(id);
        return out;
    }

    private static List<RaceGuide.ProfessionNode> firstProfessions()
    {
        List<RaceGuide.ProfessionNode> out = new ArrayList<>();
        for (PlayerRace r : PlayerRace.values())
            for (RaceGuide.ProfessionNode p : RaceGuide.professionTree(r))
                if (p.tier == 1)
                    out.add(p);
        return out;
    }

    @Test
    public void everyRaceHasARealStartLandmarkAndHelper()
    {
        assertEquals(5, PlayerRace.values().length, "Interlude has exactly 5 races (no Kamael)");
        for (PlayerRace r : PlayerRace.values())
        {
            QuestNode t = RaceGuide.tutorial(r);
            assertEquals(999, t.questId, t.signature());
            assertTrue(t.core, "tutorial is core");
            assertTrue(t.npcId > 0, t.signature() + " newbie helper must exist");
            assertEquals(r.helperNpcId(), t.npcId);
            assertReal(t);
            assertEquals(r.startZone(), t.zone);
            assertEquals(r.startTown(), t.town);
            assertFalse(t.x == VOID_X && t.y == VOID_Y && t.z == VOID_Z, "helper is at the void spot");
        }
    }

    @Test
    public void newbieChainIsPerRaceWithRealSpawnsInStartZone()
    {
        for (PlayerRace r : PlayerRace.values())
        {
            List<QuestNode> chain = RaceGuide.newbieChain(r);
            assertEquals(3, chain.size(), r + ": tutorial + 2 village quests");
            assertEquals(999, chain.get(0).questId);
            for (QuestNode n : chain)
            {
                assertTrue(n.core, n.signature());
                assertEquals(r.startZone(), n.zone, n.signature() + " zone");
                assertTrue(n.npcId > 0, n.signature());
                assertReal(n);
            }
        }
        List<Integer> firstIds = new ArrayList<>();
        for (PlayerRace r : PlayerRace.values())
            firstIds.add(RaceGuide.newbieChain(r).get(1).questId);
        assertEquals(List.of(1, 2, 3, 4, 5), firstIds, "first village quests are Q1-Q5");
        List<Integer> secondIds = new ArrayList<>();
        for (PlayerRace r : PlayerRace.values())
            secondIds.add(RaceGuide.newbieChain(r).get(2).questId);
        assertEquals(List.of(6, 7, 8, 9, 10), secondIds, "second village quests are Q6-Q10");
    }

    @Test
    public void classTreeMatchesInterlude()
    {
        // expected {t0, t1, t2, t3} per race, from classList.xml
        int[][] expected = {
            { 2, 5, 11, 11 },  // HUMAN
            { 2, 4, 7, 7 },    // ELF
            { 2, 4, 7, 7 },    // DARK_ELF
            { 2, 3, 4, 4 },    // ORC
            { 1, 2, 2, 2 },    // DWARF
        };
        PlayerRace[] races = PlayerRace.values();
        for (int i = 0; i < races.length; i++)
        {
            int[] got = new int[4];
            for (RaceGuide.ProfessionNode p : RaceGuide.professionTree(races[i]))
            {
                assertSame(races[i], p.race, p.toString());
                assertTrue(p.tier >= 0 && p.tier <= 3, p.toString());
                got[p.tier]++;
            }
            assertArrayEquals(expected[i], got, races[i] + " tier shape must match classList.xml");
        }
    }

    @Test
    public void everyFirstProfessionHasAPathQuestWithRealSpawn()
    {
        Set<Integer> seen = new HashSet<>();
        for (RaceGuide.ProfessionNode p : firstProfessions())
        {
            QuestNode path = RaceGuide.firstClassChangeQuest(p.classId)
                    .orElseThrow(() -> new AssertionError("no Path quest for " + p));
            assertEquals(QuestNode.Kind.CLASS_CHANGE_1, path.kind, path.signature());
            assertEquals(19, path.levelMin, path.signature() + " min level");
            assertTrue(path.core, path.signature());
            assertTrue(path.npcId > 0, path.signature() + " must have a starter NPC");
            assertTrue(path.questId >= 401 && path.questId <= 418, path.signature());
            assertReal(path);
            assertTrue(seen.add(path.questId), "Path quest " + path.questId + " mapped to two professions");
        }
        assertEquals(18, seen.size(), "the 18 Path quests (Q401-418)");
    }

    @Test
    public void everyFirstProfessionHasSecondClassTrials()
    {
        Set<Integer> referenced = new HashSet<>();
        for (RaceGuide.ProfessionNode p : firstProfessions())
        {
            List<QuestNode> trials = RaceGuide.secondClassChangeQuests(p.classId);
            assertFalse(trials.isEmpty(), p + " must have at least one trial");
            for (QuestNode t : trials)
            {
                assertEquals(QuestNode.Kind.CLASS_CHANGE_2, t.kind, t.signature());
                assertTrue(t.levelMin >= 35 && t.levelMin <= 38, t.signature() + " min level");
                assertTrue(t.core, t.signature());
                assertTrue(t.npcId > 0, t.signature());
                assertReal(t);
                referenced.add(t.questId);
            }
        }
        List<QuestNode> pool = RaceGuide.allSecondClassQuests();
        assertEquals(25, pool.size(), "pool is Q211..Q235");
        for (int id : referenced)
            assertTrue(pool.stream().anyMatch(n -> n.questId == id), "trial " + id + " not in pool");
        // branch/state-machine quests live in the pool but are not force-attached to any class
        assertTrue(pool.stream().anyMatch(n -> n.questId == 234), "Testimony of Blood in pool");
        assertTrue(pool.stream().anyMatch(n -> n.questId == 235), "Testimony of War in pool");
    }

    @Test
    public void everyThirdProfessionHasASagaQuest()
    {
        int third = 0;
        for (PlayerRace r : PlayerRace.values())
            for (RaceGuide.ProfessionNode p : RaceGuide.professionTree(r))
            {
                if (p.tier != 3)
                    continue;
                third++;
                QuestNode saga = RaceGuide.thirdClassChangeQuest(p.classId)
                        .orElseThrow(() -> new AssertionError("no Saga quest for " + p));
                assertEquals(QuestNode.Kind.CLASS_CHANGE_3, saga.kind, saga.signature());
                assertTrue(saga.name.startsWith("Saga of the "), saga.signature());
                assertEquals(76, saga.levelMin, saga.signature() + " min level");
                assertEquals("Town of Aden", saga.town, "Sagas assemble in the main town hub");
            }
        assertEquals(31, third, "31 third professions total");
    }

    @Test
    public void teleportRoutesCrossTheWholeMap()
    {
        assertTrue(RaceGuide.teleportLeg("Giran", "Aden").isPresent(), "core Giran-Aden leg");
        assertFalse(RaceGuide.teleportLeg("Aden", "Goddard").isEmpty());
        List<TeleportLeg> route = RaceGuide.route("Talking Island", "Hunters Village");
        assertFalse(route.isEmpty(), "boat+gatekeeper path must exist");
        assertEquals("Talking Island", route.get(0).fromTown);
        TeleportLeg lastLeg = route.get(route.size() - 1);
        assertEquals("Hunters Village", lastLeg.toTown);
        for (int i = 0; i < route.size() - 1; i++)
            assertEquals(route.get(i).toTown, route.get(i + 1).fromTown, "legs must chain");
    }

    @Test
    public void humanWarriorFullPath()
    {
        QuestNode tut = RaceGuide.tutorial(PlayerRace.HUMAN);
        assertEquals(30008, tut.npcId, "Grand Master Roien");
        assertEquals("Talking Island", tut.town);

        List<QuestNode> nb = RaceGuide.newbieChain(PlayerRace.HUMAN);
        assertEquals(1, nb.get(1).questId);  // Letters of Love (Darin 30048)
        assertEquals(6, nb.get(2).questId);  // Step into the Future (Roxxy 30006)
        assertEquals(30048, nb.get(1).npcId);
        assertEquals(30006, nb.get(2).npcId);

        QuestNode path = RaceGuide.firstClassChangeQuest(1).orElseThrow(); // Warrior
        assertEquals(401, path.questId);
        assertEquals("Path to a Warrior", path.name);
        assertEquals(30010, path.npcId, "Auron");
        assertEquals("Gludin Village", path.town);

        List<QuestNode> trials = RaceGuide.secondClassChangeQuests(1);
        assertEquals(1, trials.size(), "Warrior only needs the Challenger trial");
        assertEquals(211, trials.get(0).questId);
        assertEquals(30644, trials.get(0).npcId, "Kashi");
        assertEquals("Dion Castle Town", trials.get(0).town);

        QuestNode saga = RaceGuide.thirdClassChangeQuest(88).orElseThrow(); // Duelist
        assertEquals(73, saga.questId);
        assertEquals("Saga of the Duelist", saga.name);

        assertAncestry(0, 1, 2, 88); // Human Fighter -> Warrior -> Gladiator -> Duelist
    }

    @Test
    public void elfElvenKnightFullPath()
    {
        assertEquals(30370, RaceGuide.tutorial(PlayerRace.ELF).npcId, "Elven Newbie Helper");
        QuestNode path = RaceGuide.firstClassChangeQuest(19).orElseThrow(); // Elven Knight
        assertEquals(406, path.questId);
        assertEquals(30327, path.npcId, "Sorius");
        assertEquals("Gludio Castle Town", path.town);

        List<Integer> trialIds = new ArrayList<>();
        for (QuestNode t : RaceGuide.secondClassChangeQuests(19))
            trialIds.add(t.questId);
        assertEquals(List.of(211, 212, 226), trialIds, "Elven Knight trials");

        QuestNode saga = RaceGuide.thirdClassChangeQuest(99).orElseThrow(); // Eva's Templar
        assertEquals(71, saga.questId);
        assertEquals("Saga of the Eva's Templar", saga.name);
        assertAncestry(18, 19, 20, 99);
    }

    @Test
    public void darkElfAssassinFullPath()
    {
        QuestNode tut = RaceGuide.tutorial(PlayerRace.DARK_ELF);
        assertEquals(30129, tut.npcId);
        assertEquals("Dark Elven Village", tut.town);
        assertEquals(3, RaceGuide.newbieChain(PlayerRace.DARK_ELF).get(1).questId);

        QuestNode path = RaceGuide.firstClassChangeQuest(35).orElseThrow(); // Assassin
        assertEquals(411, path.questId);
        assertEquals(30416, path.npcId, "Triskel");
        assertEquals("Gludio Castle Town", path.town);

        List<Integer> quests = new ArrayList<>();
        for (QuestNode t : RaceGuide.secondClassChangeQuests(35))
            quests.add(t.questId);
        assertEquals(List.of(213, 224, 225), quests, "Assassin branch");

        QuestNode saga = RaceGuide.thirdClassChangeQuest(108).orElseThrow(); // Ghost Hunter
        assertEquals(81, saga.questId);
        assertEquals("Saga of the Ghost Hunter", saga.name);
        assertAncestry(31, 35, 36, 108);
    }

    @Test
    public void orcRaiderFullPath()
    {
        QuestNode tut = RaceGuide.tutorial(PlayerRace.ORC);
        assertEquals(30573, tut.npcId);
        assertEquals("Orc Village", tut.town);
        assertEquals(4, RaceGuide.newbieChain(PlayerRace.ORC).get(1).questId);

        QuestNode path = RaceGuide.firstClassChangeQuest(45).orElseThrow(); // Orc Raider
        assertEquals(414, path.questId);
        assertEquals(30570, path.npcId, "Karukia");
        assertEquals("Orc Village", path.town);

        List<Integer> quests = new ArrayList<>();
        for (QuestNode t : RaceGuide.secondClassChangeQuests(45))
            quests.add(t.questId);
        assertEquals(List.of(211, 223), quests);

        QuestNode saga = RaceGuide.thirdClassChangeQuest(113).orElseThrow(); // Titan
        assertEquals(75, saga.questId);
        assertEquals("Saga of the Titan", saga.name);
        assertAncestry(44, 45, 46, 113);
    }

    @Test
    public void dwarfScavengerFullPath()
    {
        QuestNode tut = RaceGuide.tutorial(PlayerRace.DWARF);
        assertEquals(30528, tut.npcId);
        assertEquals("Dwarven Village", tut.town);
        assertEquals(5, RaceGuide.newbieChain(PlayerRace.DWARF).get(1).questId);

        QuestNode path = RaceGuide.firstClassChangeQuest(54).orElseThrow(); // Scavenger
        assertEquals(417, path.questId);
        assertEquals(30524, path.npcId, "Pippi");
        assertEquals("Dwarven Village", path.town);

        List<Integer> quests = new ArrayList<>();
        for (QuestNode t : RaceGuide.secondClassChangeQuests(54))
            quests.add(t.questId);
        assertEquals(List.of(216, 225, 231), quests);

        QuestNode saga = RaceGuide.thirdClassChangeQuest(117).orElseThrow(); // Fortune Seeker
        assertEquals(99, saga.questId);
        assertEquals("Saga of the Fortune Seeker", saga.name);
        assertAncestry(53, 54, 55, 117);
    }

    @Test
    public void nextQuestGoalWalksTheWholeChain()
    {
        // fresh human on Talking Island
        Optional<QuestNode> g = RaceGuide.nextQuestGoal(PlayerRace.HUMAN, 0, 1, setOf());
        assertEquals(999, g.map(n -> n.questId).orElseThrow(), "start at the race helper");

        // tutorial + Letters of Love done at Lv10 -> Step into the Future
        g = RaceGuide.nextQuestGoal(PlayerRace.HUMAN, 0, 10, setOf(999, 1));
        assertEquals(6, g.map(n -> n.questId).orElseThrow());

        // newbie chain done, Lv25 base class -> Path of the Warrior
        g = RaceGuide.nextQuestGoal(PlayerRace.HUMAN, 0, 25, setOf(999, 1, 6));
        assertEquals(401, g.map(n -> n.questId).orElseThrow());

        // already a Warrior (1st profession) at Lv40 -> Trial of the Challenger
        g = RaceGuide.nextQuestGoal(PlayerRace.HUMAN, 1, 40, setOf(999, 1, 6, 401));
        assertEquals(211, g.map(n -> n.questId).orElseThrow());

        // trial done -> no more forced quest for the Warrior->Gladiator chain
        g = RaceGuide.nextQuestGoal(PlayerRace.HUMAN, 2, 40, setOf(999, 1, 6, 401, 211));
        assertTrue(g.isEmpty(), "chain complete; the AI hunts/levels (side-quest rule TBD)");
    }

    @Test
    public void idleAnchorIsAlwaysARealCoordinate()
    {
        for (PlayerRace r : PlayerRace.values())
        {
            for (int level : new int[] { 1, 8, 14, 20, 30, 45, 60, 70, 78 })
            {
                QuestNode anchor = RaceGuide.idleAnchor(r, level);
                assertFalse(anchor.x == VOID_X && anchor.y == VOID_Y && anchor.z == VOID_Z,
                        anchor.name + " at Lv" + level + " is the void point (STEP 6 fix)");
                assertFalse(anchor.x == 0 && anchor.y == 0 && anchor.z == 0, anchor.name + " has no coords");
                assertTrue(anchor.core, anchor.name + " must be a core landmark");
            }
            // level 1-14: stay home, helpers are the anchor
            assertEquals(r.helperNpcId(), RaceGuide.idleAnchor(r, 1).npcId, r + " low-idle at helper");
            // level 70: Varka/Ketra band -> Goddard gatekeeper (real GK coords)
            QuestNode goddard = RaceGuide.idleAnchor(r, 70);
            assertEquals("Goddard", goddard.town, r + " Lv70 idles at Goddard hub");
            assertReal(goddard);
        }
    }
}