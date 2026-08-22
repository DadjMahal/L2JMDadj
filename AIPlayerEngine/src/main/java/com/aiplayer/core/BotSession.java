package com.aiplayer.core;

/** EP-4 extraction: the whole per-bot session machine (tick loop, quest-dialog driver, potions,
 *  relocation, reconnect, death guard) that lived as FleetPlay's ~1,050-line inner BotLoop.
 *  Behavior is byte-identical to the pre-split loop — same packets out, same timings; the only
 *  change is that the shared fleet structures (bot map, event ring, history ring, metrics) are
 *  passed in explicitly instead of being reached as FleetPlay statics. */

import static com.aiplayer.core.FleetConfig.TICK_MS;
import static com.aiplayer.core.FleetConfig.WANDER_INTERVAL_MS;
import static com.aiplayer.core.FleetConfig.WANDER_RADIUS;
import static com.aiplayer.core.FleetConfig.CHASE_REACH;
import static com.aiplayer.core.FleetConfig.CHASE_HOP;
import static com.aiplayer.core.FleetConfig.CHASE_INTERVAL_MS;
import static com.aiplayer.core.FleetConfig.REGEN_HOLD_MS;
import static com.aiplayer.core.FleetConfig.SURROUND_CAP;
import static com.aiplayer.core.FleetConfig.DEATH_GUARD_DEATHS;
import static com.aiplayer.core.FleetConfig.DEATH_GUARD_MS;
import static com.aiplayer.core.FleetConfig.RECONNECT_BASE_MS;
import static com.aiplayer.core.FleetConfig.RECONNECT_MAX_MS;
import static com.aiplayer.core.FleetConfig.accountPassword;
import static com.aiplayer.core.FleetConfig.raceRadiusFactor;
import static com.aiplayer.core.FleetConfig.staleBudgetMs;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.aiplayer.behavior.BotSurvival;
import com.aiplayer.behavior.QuestDialogSession;
import com.aiplayer.net.AIPlayer;
import com.aiplayer.net.GameServerClient;
import com.aiplayer.behavior.combat.CombatDecision;
import com.aiplayer.behavior.combat.TargetSelector;
import com.aiplayer.behavior.quest.QuestProgressTracker;
import com.aiplayer.knowledge.PlayerRace;
import com.aiplayer.behavior.movement.HopGate;
import com.aiplayer.behavior.movement.MoveTelemetry;
import com.aiplayer.behavior.movement.RelocationPlanner;
import com.aiplayer.behavior.movement.TravelPlanner;
import com.aiplayer.behavior.movement.ZoneRouter;
import com.aiplayer.behavior.movement.ZoneRouter.RouteGoal;
import com.aiplayer.behavior.AcquireCooldown;
import com.aiplayer.behavior.BotPlayController;
import com.aiplayer.behavior.BotPlayController.Hostile;
import com.aiplayer.behavior.BotPlayController.PlayContext;
import com.aiplayer.behavior.GoalAction;
import com.aiplayer.behavior.GoalDecision;
import com.aiplayer.behavior.HumanReactionSimulator;
import com.aiplayer.behavior.PersonalityBehavior;
import com.aiplayer.behavior.PlayerGoal;
import com.aiplayer.behavior.QuestDialogDriver;
import com.aiplayer.behavior.QuestDialogDriver.Objective;
import com.aiplayer.behavior.QuestDialogDriver.QuestDialog;
import com.aiplayer.learning.PersonalityProfile;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.protocol.PacketCodec;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.protocol.PacketLogger.EntityInfo;
import com.aiplayer.monitor.AIMonitorDashboard;
import com.aiplayer.web.DashboardApi;
import com.aiplayer.web.EventRing;
import com.aiplayer.web.FleetMetrics;
import com.aiplayer.web.HistoryRing;

public final class BotSession implements Runnable
{
    private static final Logger LOGGER = Logger.getLogger(BotSession.class.getName());

    /** Shared fleet structures (single writer per row = this bot's own thread). */
    private final ConcurrentHashMap<String, BotInfo> bots;
    private final EventRing events;
    private final HistoryRing history;
    private final FleetMetrics metrics;

// Live-loop tuning knobs (tick/wander/chase/stale/death-guard/reconnect) live in
// FleetConfig since EP-4; static-imported below so the session body reads unchanged.

    private final String account;
    private final int charId;
    private final BotInfo info;
    private final String host;
    private final int loginPort;
    private final int gamePort;
    /** Per-bot race: drives the guide landmark + restock vendor so non-Humans stay in their own zone. */
    private final PlayerRace race;
    /** EB-04: deterministic per-bot personality (drives the decision config via PersonalityBehavior). */
    private final PersonalityProfile personality;
    /** EB-05: per-bot deterministic human reaction knob (live consumption of the humanize pack). */
    private final HumanReactionSimulator humanReaction;
    private final Random rng;
    // STEP 2: per-session quest-dialog driver — the driving DECISION state machine now lives in
    // behavior/QuestDialogSession (pure); the session only executes its step results.
    private final QuestDialogSession questDialog = new QuestDialogSession(10_000L);
    // S6-T04: HP potion (itemId 1061) at low HP, gated by a cooldown — decision lives in
    // BotSurvival (shouldSipPotion/findPotion); the session tracks only the last-use timestamp.
    private long lastPotionUseMs = 0;
    // S5-T10: position-drift watchdog (surface a bot that stops moving).
    private int freezeTicks = 0;
    private long lastFrozenLogMs = 0;
    private int lastSeenFx = Integer.MIN_VALUE;
    private int lastSeenFy = Integer.MIN_VALUE;
    // S3-T05: quest stepIndex persistence across ticks/sessions (feeds PlayContext).
    private final QuestProgressTracker questTracker;
    // S7-T04: session adena income accumulator.
    private long lastAdena = -1;
    private long sessionAdenaIncome = 0;
    // ACQUIRE-failure cooldown (per-bot, per-session state; reset() at each runSession start). Stops
    // the "re-plan the same geo-unreachable ACQUIRE giver forever" loop — e.g. Wolf Hunt at Gludio,
    // ~148k units across the ocean: after maxUnreachable abandoned goal:acquire:* routes the bot falls
    // back to plain farming for cooldownMs instead of re-issuing the dead ocean-hop. See AcquireCooldown.
    private final AcquireCooldown acquireCooldown = new AcquireCooldown();
    // S2-T07: grows on repeated failures, reset to base after a clean enter-world.
    private long reconnectDelayMs = RECONNECT_BASE_MS;
    // S6-T03/T06/T07/T10: survival-guard state (retreat regen hold, death-loop guard).
    private long regenHoldUntilMs = 0;
    private long deathGuardUntilMs = 0;
    private long lastDeathMs = 0;
    private int recentDeaths = 0;
    // S3-T07: per-bot controller tuning (race for restock, varietySeed for diverse quest picks).
    private final BotPlayController.BotPlayConfig cfg;

    public BotSession(String account, int charId, BotInfo info, String host, int loginPort, int gamePort,
                    PlayerRace race, ConcurrentHashMap<String, BotInfo> bots, EventRing events,
                    HistoryRing history, FleetMetrics metrics)
    {
        this.account = account;
        this.charId = charId;
        this.info = info;
        this.host = host;
        this.loginPort = loginPort;
        this.gamePort = gamePort;
        this.race = race != null ? race : PlayerRace.HUMAN;
        this.info.race = this.race.name(); // S9-T05 dashboard race badge/filter
        // EB-04: deterministic per-bot personality (same seed as AIPlayer uses: 100 + charId%100),
        // then feed its knobs into the decision config so risk/pace/restock actually vary by personality.
        PersonalityProfile botPersonality = PersonalityProfile.forSeed(100 + charId % 100);
        this.cfg = new BotPlayController.BotPlayConfig(0.25, 400, 2000, 300, 100,
            this.race, Math.abs(account.hashCode()),
            BotPlayController.BotPlayConfig.ladderForRace(this.race)) // EB-03: per-race ladder
                .withPersonality(PersonalityBehavior.knobs(botPersonality.getPersonality())); // EB-04
        this.personality = botPersonality;
        this.humanReaction = new HumanReactionSimulator(DeterministicRandom.seed("bot::" + account + "::reaction"));
        this.questTracker = new QuestProgressTracker(account);
        this.rng = new Random(account.hashCode());
        this.bots = bots;
        this.events = events;
        this.history = history;
        this.metrics = metrics;
    }

    @Override
    public void run()
    {
        while (!Thread.interrupted())
        {
            try
            {
                runSession();
            }
            catch (Exception e)
            {
                info.connected = false;
                info.loggedIn = false;
                info.state = "disconnected";
                emit(EventRing.TYPE_DISCONNECT, "reason", e.getClass().getSimpleName());
                metrics.noteReconnect();
                LOGGER.warning("[FleetPlay] " + account + " session ended: " + e.getMessage());
                try
                {
                    // S2-T07: exponential backoff + jitter so 50 bots don't stampede the server
                    // on a shared outage; escalate on repeated failures.
                    Thread.sleep(reconnectDelayMs + Math.abs(rng.nextInt() % 2000));
                }
                catch (InterruptedException ie)
                {
                    return;
                }
                reconnectDelayMs = Math.min(reconnectDelayMs * 2, RECONNECT_MAX_MS);
            }
        }
    }

    /** S6-T04: an HP potion in the live inventory records (objectId needed for UseItem). */
    /** Adapt the logger's inventory records to BotSurvival's minimal item view (no protocol leak). */
    private java.util.List<BotSurvival.Item> survivalInventory(PacketLogger logger)
    {
        java.util.List<PacketLogger.InventoryItem> raw = logger.getInventoryRecords();
        if (raw == null || raw.isEmpty())
        {
            return java.util.Collections.emptyList();
        }
        java.util.List<BotSurvival.Item> view = new java.util.ArrayList<>(raw.size());
        for (PacketLogger.InventoryItem it : raw)
        {
            if (it == null)
            {
                continue;
            }
            final PacketLogger.InventoryItem f = it;
            view.add(new BotSurvival.Item()
            {
                @Override
                public int getItemId()
                {
                    return f.itemId;
                }

                @Override
                public long getCount()
                {
                    return f.count;
                }

                @Override
                public int getObjectId()
                {
                    return f.objectId;
                }
            });
        }
        return view;
    }

    /** QUEST-PRIORITY: is the configured quest giver nearby (< QUEST_PRIORITY_DIST)? */
    private boolean nearQuestNpc(BotSnapshot s, PacketLogger logger, int questNpcId)
    {
        if (questNpcId <= 0)
        {
            return false;
        }
        PacketLogger.EntityInfo giver = logger.findEntityByNpcId(questNpcId);
        if (giver == null)
        {
            return false;
        }
        double dx = s.x - giver.x;
        double dy = s.y - giver.y;
        return Math.sqrt(dx * dx + dy * dy) <= BotPlayController.QUEST_PRIORITY_DIST;
    }

    /**
     * STEP 2: drive an NPC quest dialog directly inside the fleet loop, off by default. Only runs
     * when the controller returns BYPASS at the configured giver (engine.quest.npcId). Sequence:
     * click the NPC (Action) -> wait for its NpcHtmlMessage -> push the displayed bypass links
     * through {@link QuestDialogSession} (which reuses {@link QuestDialogDriver} for WHICH link),
     * which returns the ONE next validated command; send it via wiring.bypass and pause until the
     * server shows the next dialog. Never fabricates a command the server did not display and never
     * re-sends a link already sent in this session. The driving decision state machine now lives in
     * QuestDialogSession; this method only translates its step results into socket actions.
     */
    private void driveQuestDialog(BotSnapshot snapshot, PacketLogger logger, CoreWiring wiring,
                                  GoalDecision goal, int questNpcId, String questNameProp)
    {
        PacketLogger.EntityInfo giver = logger.findEntityByNpcId(questNpcId);
        if (giver == null)
        {
            LOGGER.fine("[FleetPlay] " + account + " quest NPC " + questNpcId + " not tracked yet; waiting");
            return;
        }
        boolean giverOnScreen = logger.getLastNpcHtmlOriginObjId() == giver.objectId;
        String html = logger.getLastNpcHtml();
        String[] links = html == null ? null : PacketLogger.extractBypassLinks(html);
        if (html != null)
        {
            LOGGER.info("[FleetPlay] " + account + " quest dialog links["
                + (links == null ? 0 : links.length) + "] " + java.util.Arrays.toString(links)); // S3-T06
        }
        Objective objective = (goal != null && goal.goal == PlayerGoal.ACQUIRE)
            ? Objective.ACCEPT : Objective.TURN_IN;
        QuestDialog dialogDef = new QuestDialog(0, questNameProp, objective, "", "");
        QuestDialogSession.Result r = questDialog.step(giver != null, giverOnScreen, html, links,
            dialogDef, System.currentTimeMillis());
        switch (r != null ? r.action : QuestDialogSession.Action.WAIT)
        {
            case SEND_BYPASS:
                wiring.bypass(r.bypass);
                LOGGER.info("[FleetPlay] " + account + " quest dialog -> bypass[" + (r.done ? "done" : "...")
                    + "] " + r.bypass);
                if (r.done)
                {
                    // real accept/turn-in sent — cancel any ACQUIRE cooldown and let the planner resume.
                    acquireCooldown.reset();
                    LOGGER.info("[FleetPlay] " + account + " quest dialog session complete; journal will refresh");
                }
                break;
            case CLICK_GIVER:
                wiring.actionOn(giver.objectId, snapshot.x, snapshot.y, snapshot.z);
                LOGGER.info("[FleetPlay] " + account + " clicking quest NPC objId=" + giver.objectId);
                break;
            case OPEN:
            default:
                LOGGER.info("[FleetPlay] " + account + " quest dialog on screen for objId=" + giver.objectId);
                break;
        }
    }

    private void runSession() throws Exception
    {
        AIPlayer player = new AIPlayer(account, 100 + charId % 100, 0, 0); // Human Fighter
        L2JProtocol login = new L2JProtocol(player, host, loginPort, gamePort);
        if (!login.connectAndLogin(account, accountPassword(), charId))
        {
            info.state = "login-failed";
            throw new IllegalStateException("login-server auth failed");
        }
        GameServerClient gs = new GameServerClient(player, host, gamePort);
        if (!gs.connectAndEnterWorld(login, account, 0))
        {
            info.state = "enter-failed";
            throw new IllegalStateException("enter-world failed");
        }
        gs.startReader();
        PacketLogger logger = gs.getPacketLogger();
        player.getCombatAI().setPacketLogger(logger);
        logger.setSelfObjectId(charId);

        CoreWiring wiring = new CoreWiring(gs, account);
        TargetSelector targetSelector = new TargetSelector(account, logger.getLevel());
        EngineConfig config = EngineConfig.getInstance();
        ZoneRouter zoneRouter = new ZoneRouter(account);
        // STEP 6: idle-relocation decision maker — prefers last-XP / nearest-mate when frozen,
        // real guide-map landmark otherwise, with a consecutive-abandon escape gate.
        RelocationPlanner relocation = new RelocationPlanner(account);
        MoveTelemetry telemetry = MoveTelemetry.getInstance();
        info.connected = true;
        info.loggedIn = true;
        info.packetsRead = gs.packetsRead;   // S2-T04: expose per-bot packet health
        info.idleTimeouts = gs.idleTimeouts;
        emit(EventRing.TYPE_CONNECT, "level", info.level);
        LOGGER.info("[FleetPlay] " + account + " ENTERED WORLD"
            + (config.isMovementEnabled() ? " [engine.movement ON]" : ""));
        reconnectDelayMs = RECONNECT_BASE_MS; // S2-T07: clean enter resets the backoff

        long lastWander = 0;
        long lastRoute = 0;
        // STEP 2: quest-dialog gating. When the operator sets engine.quest.npcId (>0) the bot
        // treats "being at the giver" as "open its dialog and follow the 1-validated-bypass driver".
        int questNpcId = AIConfiguration.getInstance().getIntProperty("engine.quest.npcId", 0);
        String questNameProp = AIConfiguration.getInstance().getProperty("engine.quest.name", "");
        ZoneRouter.RouteGoal activeRoute = null;
        int[] pendingHop = null;
        long hopSentAtMs = 0;
        int hopTimeouts = 0; // TIM-001: consecutive timeouts on the current hop (stuck-hop recovery)
        // ACQUIRE-failure cooldown is per-session run state: a fresh session (reconnect) starts clean.
        acquireCooldown.reset();
        int prevLevel = info.level;
        long prevExp = -1; // EVIDENCE-H5: StatusUpdate-driven per-kill XP-gain trail
        int prevHp = info.hp;
        boolean firstTick = true;
        long lastHistoryMs = 0;
        // STEP 3: gap-close gate. Only chase toward an out-of-melee combat target once per interval
        // (like wander) so a chasing bot re-routes without flooding the server every frame.
        long lastChaseMs = 0;
        // WPT-22: drain newly-parsed SystemMessage / chat broadcasts into the shared event ring
        // (only the count delta is emitted once per tick; message bodies are read back from logger).
        long lastSysCount = logger.getSystemMessageCount();
        long lastChatCount = logger.getChatCount();
        while (true)
        {
            // Broken-pipe / zombie guard: if the game-server reader thread died (EOF / reset /
            // IOException), the socket is gone. CoreWiring.send() swallows the write failure, so
            // WITHOUT this check the loop would keep planning HOPs against a dead server for hours
            // (the ~2h CLOSE-WAIT zombie loop in the 2026-08-15 run). Throwing here lets the outer
            // run() reconnect loop (15s sleep -> fresh runSession) rebuild the connection.
            if (!gs.isOpen())
            {
                info.connected = false;
                throw new IOException("GS connection lost (reader stopped)");
            }
            BotSnapshot snapshot = BotSnapshot.from(account, logger);
            // Dashboard data layer — feed BotInfo from the REAL PacketLogger state
            // (UserInfo 0x04 / StatusUpdate 0x0E / ValidateLocation 0x61 / ItemList 0x1B).
            info.level = logger.getLevel();
            info.charName = logger.getCharName() != null ? logger.getCharName() : info.account;
            info.exp = logger.getExp();
            info.sp = logger.getSp();
            info.hp = logger.getCurHp();
            info.hpMax = logger.getMaxHp();
            info.mp = logger.getCurMp();
            info.mpMax = logger.getMaxMp();
            info.cp = logger.getCurCp();
            info.cpMax = logger.getMaxCp();
            info.x = logger.getPlayerX();
            info.y = logger.getPlayerY();
            info.z = logger.getPlayerZ();
            info.heading = logger.getPlayerHeading();
            info.load = logger.getCurrentLoad();
            info.maxLoad = logger.getMaxLoad();
            info.weapon = logger.isWeaponEquipped();
            info.adena = logger.getAdena();
            // S7-T04: session adena income (delta from kills/quests/vendor as the server streams it).
            long _adena = info.adena;
            if (lastAdena >= 0 && _adena > lastAdena)
            {
                sessionAdenaIncome += (_adena - lastAdena);
            }
            lastAdena = _adena;
            info.adenaEarned = sessionAdenaIncome;
            info.invPct = logger.getInventoryUsagePercent();
            info.itemCount = logger.getInventoryItems().size();
            info.packetsRead = gs.packetsRead;   // S2-T04: refresh packet health each tick
            info.idleTimeouts = gs.idleTimeouts;
            info.hopSuccessPct = telemetry.hopSuccessPct(account); // S5-T06
            info.items = DashboardApi.topItems(logger);
            info.mobs = logger.getHostileEntityCount();
            info.npcs = logger.getEntityCountTotal();
            info.ents = DashboardApi.entitySnapshot(logger);
            // WPT-27: feed the quest journal from the real QUEST_LIST (0x80) parse.
            java.util.List<int[]> ql = logger.getActiveQuestList();
            if (!ql.isEmpty())
            {
                info.totalQuestCount = ql.size();
                info.activeQuests = ql.toArray(new int[ql.size()][]);
                int act = 0;
                int firstQuestId = 0, firstQuestState = 0;
                for (int[] q : ql)
                {
                    if (q[1] != 0) act++;
                    if (firstQuestId == 0)
                    {
                        firstQuestId = q[0];
                        firstQuestState = q[1];
                    }
                }
                info.questCount = act;
                // S3-T02: surface the current objective parsed from the LIVE quest dialog html the
                // server actually sent (never fabricated; empty until a dialog body is present).
                String html = logger.getLastNpcHtml();
                if (html != null && !html.isBlank())
                {
                    com.aiplayer.behavior.quest.QuestObjectiveParser.Parsed obj =
                        com.aiplayer.behavior.quest.QuestObjectiveParser.parse(
                            firstQuestId, firstQuestState, html);
                    info.questObjective = obj != null ? obj.toString() : "";
                    // S3-T03: reward receipts — when the on-screen dialog IS the turn-in page and
                    // a system message carries item-name args, surface the receipt (windowed, honest).
                    if (com.aiplayer.behavior.quest.QuestTurnRewardParser.isTurnInDialog(html))
                    {
                        PacketLogger.SystemMessageEvent sm = logger.getLastSystemMessage();
                        if (sm != null)
                        {
                            java.util.List<com.aiplayer.behavior.quest.QuestTurnRewardParser.ItemArg> args =
                                new java.util.ArrayList<>();
                            for (PacketLogger.Arg a : sm.params)
                            {
                                args.add(new com.aiplayer.behavior.quest.QuestTurnRewardParser.ItemArg(a.type, a.rendered));
                            }
                            java.util.List<com.aiplayer.behavior.quest.QuestTurnRewardParser.RewardReceipt> r =
                                com.aiplayer.behavior.quest.QuestTurnRewardParser.itemReceipts(args);
                            if (!r.isEmpty())
                            {
                                java.util.List<String> lines = new java.util.ArrayList<>();
                                for (com.aiplayer.behavior.quest.QuestTurnRewardParser.RewardReceipt rc : r)
                                {
                                    lines.add(rc.toString());
                                }
                                info.questReward = String.join(", ", lines);
                            }
                        }
                    }
                }
            }
            info.lastSeenMs = System.currentTimeMillis();
            // EB-14: how long until the LONGEST active per-bot cooldown clears (seconds), 0 = none.
            long nowCd = System.currentTimeMillis();
            long nextCdMs = Math.max(regenHoldUntilMs, Math.max(deathGuardUntilMs, lastPotionUseMs + BotSurvival.HP_POTION_COOLDOWN_MS));
            info.cooldownUntilSec = nextCdMs > nowCd ? (nextCdMs - nowCd + 999) / 1000 : 0;
            AIMonitorDashboard.getInstance().updatePlayerStats(player);

            // S6-T04: at low HP, sip an HP potion when one is stocked (server opcode 0x14), gated by a
            // cooldown so a pocket full of pots isn't drained in one fight. The DECISION is BotSurvival's
            // (pure); the session only executes it through the socket.
            if (BotSurvival.shouldSipPotion(snapshot, lastPotionUseMs, System.currentTimeMillis()))
            {
                BotSurvival.Item potion = BotSurvival.findPotion(survivalInventory(logger));
                if (potion != null)
                {
                    lastPotionUseMs = System.currentTimeMillis();
                    gs.sendGameFrame(PacketCodec.encodeUseItem(potion.getObjectId()));
                    LOGGER.info("[FleetPlay] " + account
                        + " sipping HP potion objectId=" + potion.getObjectId());
                }
            }

            // S5-T10: position-drift watchdog — surface a bot that stops advancing (the stream-fed
            // hop/ack telemetry already counts hops; this flags the frozen side by position).
            if (snapshot.x == lastSeenFx && snapshot.y == lastSeenFy)
            {
                freezeTicks++;
            }
            else
            {
                freezeTicks = 0;
            }
            lastSeenFx = snapshot.x;
            lastSeenFy = snapshot.y;
            if (freezeTicks >= 120 && System.currentTimeMillis() - lastFrozenLogMs > 30_000)
            {
                lastFrozenLogMs = System.currentTimeMillis();
                LOGGER.warning("[FleetPlay] " + account + " FROZEN drift watch: " + freezeTicks
                    + " ticks no move at " + snapshot.x + "," + snapshot.y + " state=" + info.state);
            }
            // position + exp (ValidateLocation 0x61 / CharInfo 0x03 parse in PacketLogger).
            telemetry.recordPosition(account, snapshot.x, snapshot.y, snapshot.z, info.exp);

            // WPT-03/04/08: live evidence emission from the SAME tick data (no fake packets).
            int tickLevel = logger.getLevel();
            if (prevLevel > 0 && tickLevel > prevLevel)
            {
                emit(EventRing.TYPE_LEVEL_UP, "from", prevLevel, "to", tickLevel);
            }
            prevLevel = tickLevel;
            if (!firstTick && info.hp < prevHp)
            {
                emit(EventRing.TYPE_DAMAGE, "dmg", prevHp - info.hp, "hp", info.hp);
            }
            firstTick = false;
            prevHp = info.hp;
            // EVIDENCE-H5: emit a human-readable per-kill XP-gain line whenever the server's
            // StatusUpdate EXP for this bot rises (level showed in logs above). This is the engine's
            // packet-level receipt of the same XP the server persisted to gameserver.characters.
            if (prevExp >= 0 && info.exp > prevExp)
            {
                long gained = info.exp - prevExp;
                System.out.println("[EVIDENCE-H5] " + account + " EXP +" + gained
                    + " (now " + info.exp + ", level=" + tickLevel + ")");
                // S9-T06: count the kill (an EXP receipt == the server granted a kill's XP).
                info.killCount++;
                // TOP-NOTCH AI: feed the real learning chain — rewardKill -> ReinforcementEngine
                // -> AdaptiveLearner -> DeepLearning (kills drive what the AI "learns to prefer").
                String targetName = (info.targetLabel != null && !info.targetLabel.isEmpty())
                    ? info.targetLabel : ("mob#" + info.targetObjId);
                player.getCombatAI().onKill(targetName, gained);
                // STEP 6: remember where this bot earned XP (hostiles live there) and clear any
                // relocation-freeze counter — earning XP proves the char can move/fight here.
                relocation.recordLastXp(info.x, info.y, info.z);
                relocation.noteProgress();
            }
            prevExp = info.exp;
            // WPT-22: emit newly-parsed SystemMessage / chat broadcasts (real server text).
            long sysNow = logger.getSystemMessageCount();
            long chatNow = logger.getChatCount();
            if (sysNow > lastSysCount)
            {
                java.util.List<PacketLogger.SystemMessageEvent> sysmsgs = logger.getSystemMessageEvents();
                for (int i = 0; i < sysmsgs.size() && lastSysCount < sysNow; i++)
                {
                    PacketLogger.SystemMessageEvent sm = sysmsgs.get(i);
                    if (sm == null) continue;
                    // EVIDENCE-H5: echo every server SystemMessage to stdout so the persisted run log
                    // carries the server's own per-kill cues (e.g. sysmsg#213 "You have earned X
                    // experience." / adena / level-up) — independent of the DB outcome delta.
                    System.out.println("[EVIDENCE-H5] " + account + " SYSMSG " + sm.msgId + " " + sm.text);
                    java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
                    d.put("msgId", sm.msgId);
                    d.put("text", sm.text);
                    events.add(EventRing.TYPE_SYSMSG, account, d);
                    lastSysCount++;
                }
            }
            lastSysCount = sysNow;
            if (chatNow > lastChatCount)
            {
                java.util.List<PacketLogger.ChatEvent> chats = logger.getChatEvents();
                for (int i = 0; i < chats.size() && lastChatCount < chatNow; i++)
                {
                    PacketLogger.ChatEvent c = chats.get(i);
                    if (c == null) continue;
                    java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
                    d.put("kind", c.kind);
                    d.put("speaker", c.speaker);
                    d.put("text", c.text);
                    events.add(EventRing.TYPE_CHAT, account, d);
                    lastChatCount++;
                }
            }
            lastChatCount = chatNow;
            long nowTick = System.currentTimeMillis();
            if (nowTick - lastHistoryMs >= 2000)
            {
                history.register(account, snapshot.x, snapshot.y, snapshot.z,
                    info.level, info.exp, info.hp, info.hpMax);
                lastHistoryMs = nowTick;
            }
            metrics.setBotCount(bots.size());
            int onlineCount = 0;
            for (BotInfo b : bots.values())
            {
                if (b.connected && b.loggedIn) onlineCount++;
            }
            metrics.setOnlineCount(onlineCount);
            if (info.getPktAgeMs() >= 0)
            {
                metrics.notePktAgeMs(info.getPktAgeMs());
            }

            if (snapshot.hpCurrent <= 0)
            {
                if (!"dead".equals(info.state))
                {
                    info.state = "dead";
                    // STEP 4: notify CombatAI so it clears target/aggression state
                    player.getCombatAI().onDeath();
                    // S6-T06/T10: track death rate; 3+ deaths in 60s = death-loop -> force a regen hold.
                    long nowMs = System.currentTimeMillis();
                    if (nowMs - lastDeathMs < 60_000) recentDeaths++;
                    else recentDeaths = 1;
                    lastDeathMs = nowMs;
                    if (recentDeaths >= DEATH_GUARD_DEATHS)
                    {
                        deathGuardUntilMs = nowMs + DEATH_GUARD_MS;
                        LOGGER.warning("[FleetPlay] " + account + " DEATH-LOOP guard: " + recentDeaths
                            + " deaths in 60s -> " + (DEATH_GUARD_MS / 1000) + "s regen/relocate hold");
                    }
                    emit(EventRing.TYPE_DEATH, "died",
                        snapshot.hpCurrent + "/" + snapshot.hpMax + " HP; lvl " + snapshot.level
                            + " combat target " + info.targetObjId,
                        "objId", info.targetObjId);
                    wiring.revive();
                }
                Thread.sleep(TICK_MS * 10);
                continue;
            }
            else if ("dead".equals(info.state))
            {
                info.state = "alive";
                // STEP 4: notify CombatAI the bot is back in the world
                player.getCombatAI().onRespawn(snapshot.level);
                emit(EventRing.TYPE_RESPAWN, "respawned",
                    "back at " + snapshot.x + "," + snapshot.y + " lvl " + snapshot.level,
                    "lvl", snapshot.level);
            }

            // TIM-001 H5: keep the CombatAI's AIPlayer position in sync with the LIVE snapshot.
            // detectNearbyEnemy() computes distance from aiPlayer.getX/Y/Z, which were seeded at
            // (0,0,0) at construction and never updated from packets, so every hostile read as
            // ~83k units away -> the bot never engaged (stuck at AUTO_PLAY, no kills / no XP).
            player.setPosition(snapshot.x, snapshot.y, snapshot.z);
            CombatDecision decision = player.getCombatAI().makeDecision();
            info.state = decision.getAction().toString();
            info.action = decision.getAction().toString();
            info.thought = decision.getReason();
            // "thoughts / target": resolve the bot's current target into a label + distance.
            int selTargetId = player.getCombatAI().getSelectedTargetObjId();
            // STEP 3 follow-up: stale-target watchdog. If the engaged enemy produced NO XP within
            // STALE_TARGET_BUDGET_MS, abandon it so detectNearbyEnemy() re-acquires a farmable target
            // next tick (kills the "chase the merchant / dead mob forever" stall with live proof).
            if (player.getCombatAI().checkStaleTarget(logger.getExp(), (int) staleBudgetMs(snapshot.level)))
            {
                emit(EventRing.TYPE_TARGET_ABANDON, "objId", selTargetId, "exp", logger.getExp());
                LOGGER.info("[FLEET] " + account + " abandoned stale target objId=" + selTargetId);
                selTargetId = 0;
                info.state = "idle";
                info.thought = "abandoned un-advancing target, re-acquiring";
            }
            info.targetObjId = selTargetId;
            if (selTargetId > 0)
            {
                EntityInfo target = logger.getEntity(selTargetId);
                if (target != null)
                {
                    info.targetX = target.x;
                    info.targetY = target.y;
                    info.targetZ = target.z;
                    info.targetKind = target.isHostile ? 1 : (target.name != null ? 2 : 0);
                    info.targetLabel = target.name != null ? target.name : ("npc#" + target.npcId);
                    info.targetDist = Math.sqrt(Math.pow(target.x - info.x, 2)
                        + Math.pow(target.y - info.y, 2) + Math.pow(target.z - info.z, 2));
                }
            }
            else
            {
                info.targetLabel = "";
                info.targetDist = 0;
            }

            switch (decision.getAction())
            {
                case ATTACK:
                case ENGAGE_TARGET:
                case USE_SKILL:
                    // QUEST-PRIORITY: if the bot is near its quest NPC (or mid-dialog), yield to the
                    // quest route instead of fighting — the CombatAI switch runs before the goal
                    // routing, so combat would otherwise steal the bot away from the giver forever.
                    if (questDialog.isOpen() || nearQuestNpc(snapshot, logger, questNpcId))
                    {
                        info.state = "quest-route";
                        info.thought = "near quest NPC — routing to talk (no combat)";
                        break;
                    }
                    // FINAL-MILE: while the quest dialog is open, HOLD (don't fight) — a real
                    // player in a conversation doesn't run off to attack. Keeps the bot standing
                    // at the giver through the multi-tick click -> html -> bypass -> accept flow.
                    if (questDialog.isOpen())
                    {
                        info.state = "quest-dialog";
                        info.thought = "in NPC dialog — holding (no combat while talking)";
                        break;
                    }
                    // TIM-001 H5: ATTACK/ENGAGE_TARGET decisions from CombatAI carry no explicit
                    // targetId (engageEnemy returns plain CombatDecision.attack()), so pass the
                    // target the CombatAI actually selected (getSelectedTargetObjId). Otherwise the
                    // combat planner gets targetObjId=0 and emits NO Action/AttackRequest frames
                    // (the "SKIP-UNPROVEN ... planner produced no frames" stall -> no kills, no XP).
                    int targetId = selTargetId > 0 ? selTargetId : parseObjId(decision.getTargetId());
                    if (targetId > 0)
                    {
                        // STEP 3 gap-close: CombatAI engages any hostile within combat.target_distance
                        // (default 1500) and never lowers a still-far mob (it only compares against 1500),
                        // so a bot stands still spamming an unreachable target -> 0 XP, 0 movement. When
                        // engine.movement is ON (default OFF => existing behaviour preserved), advance one
                        // hop toward an out-of-melee target so the fleet actually closes distance and farms.
                        boolean chasing = false;
                        if (config.isMovementEnabled() && System.currentTimeMillis() - lastChaseMs > CHASE_INTERVAL_MS)
                        {
                            PacketLogger.EntityInfo chaseTarget = logger.getEntity(targetId);
                            if (chaseTarget != null)
                            {
                                double cdx = chaseTarget.x - snapshot.x;
                                double cdy = chaseTarget.y - snapshot.y;
                                double cdz = chaseTarget.z - snapshot.z;
                                double cdist = Math.sqrt(cdx * cdx + cdy * cdy + cdz * cdz);
                                if (cdist > CHASE_REACH)
                                {
                                    lastChaseMs = System.currentTimeMillis();
                                    BotPlayController.Chase hop = BotPlayController.chaseStep(snapshot.x,
                                        snapshot.y, snapshot.z, chaseTarget.x, chaseTarget.y, chaseTarget.z,
                                        CHASE_HOP);
                                    telemetry.recordMove(account, snapshot.x, snapshot.y, snapshot.z,
                                        hop.x, hop.y, hop.z, "chase", "advance to target " + targetId);
                                    wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, hop.x, hop.y, hop.z);
                                    emit(EventRing.TYPE_MOVE, "to", hop.x + "," + hop.y, "route", "chase");
                                    info.state = "chase";
                                    info.thought = "closing on target " + targetId;
                                    chasing = true;
                                }
                            }
                        }
                        // Always send the combat frames even while chasing (fix for the no-kills
                        // stall): the server receives AttackRequest, routes the character to the
                        // target, and resolves melee range from SERVER-side positions. If we
                        // skipped attacks while chasing, a lagging local snapshot meant the bot
                        // chased forever and never landed a hit. Range checks are server-authoritative,
                        // so an out-of-range AttackRequest is simply routed, not rejected.
                        // USE_SKILL fallback (2026-08-17 fresh-bot fix): a level-1 fighter has no
                        // learned offensive skill, so CombatFramePlanner returns NO frames for
                        // USE_SKILL and executeCombat no-ops -> the bot stands still, takes hits,
                        // and STARVES (0 damage, 0 XP, STALE-TARGET abandon loop). Fall back to the
                        // proven plain melee attack frame so it actually deals damage and farms.
                        if (decision.getAction() == CombatDecision.Action.USE_SKILL)
                        {
                            decision = CombatDecision.attackTarget(String.valueOf(targetId));
                        }
                        wiring.executeCombat(decision, snapshot.x, snapshot.y, snapshot.z, targetId);
                    }
                    break;

                case FLEE:
                case RETREAT:
                    EntityInfo nearest = snapshot.findNearestHostile(2000, logger);
                    if (nearest != null)
                    {
                        BotSurvival.FleeHop hop = BotSurvival.fleeHop(snapshot.x, snapshot.y,
                            snapshot.z, nearest.x, nearest.y, nearest.z);
                        wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, hop.x, hop.y, hop.z);
                    }
                    // S6-T03: after a low-HP retreat, hold for regen before re-engaging.
                    regenHoldUntilMs = System.currentTimeMillis() + REGEN_HOLD_MS;
                    break;

                case IDLE:
                default:
                    info.state = "idle";
                    int newTarget = 0;
                    long nowMs = System.currentTimeMillis();
                    // S6-T03/T06/T07/T10: regen/death-loop/overwhelm guard — the DECISION is BotSurvival's
                    // (pure predicate); the session only applies the consequence (hold vs engage).
                    BotSurvival.Guard guard = BotSurvival.survivalGuard(snapshot, nowMs,
                        deathGuardUntilMs, regenHoldUntilMs, info.mobs, SURROUND_CAP);
                    if (guard.active)
                    {
                        // skip re-engaging so the bot recovers (or relocates) instead of farming to death.
                        info.state = "regen";
                        info.thought = guard.reason;
                    }
                    else
                    {
                        newTarget = targetSelector.selectTarget();
                    }
                    if (newTarget != 0)
                    {
                        info.state = "engage";
                        wiring.executeCombat(CombatDecision.attackTarget(String.valueOf(newTarget)),
                            snapshot.x, snapshot.y, snapshot.z, newTarget);
                    }
                    else if (config.isMovementEnabled())
                    {
                        // TIM-001 fix (flag OFF by default): proactive FAR travel when idle.
                        // The server rejects single moves > 9900u (MoveToLocation.java:156-163),
                        // so a route is sent as a sequence of <=4800u hops; a new hop is sent
                        // only after the server acked us near the previous one (ValidateLocation).
                        long now = System.currentTimeMillis();
                        // STEP 2: the controller authors the goal authoritatively, every tick (it is
                        // cheap and pure). If it says BYPASS at a quest NPC and the operator configured
                        // engine.quest.npcId, drive the dialog (click NPC -> read html -> send the
                        // single validated bypass) instead of routing; otherwise behave exactly as
                        // STEP 1 (MOVE_TO quest NPC / random far-travel fallback).
                        GoalDecision goal = BotPlayController.decide(buildPlayContext(snapshot, logger), cfg);
                        // EB-14: structured goal/sub-goal on the dashboard row (PlayerGoal + GoalAction names).
                        info.goal = goal != null && goal.goal != null ? goal.goal.name() : "";
                        info.subGoal = goal != null && goal.action != null ? goal.action.name() : "";
                        // ACQUIRE-failure cooldown: with an empty journal the planner keeps re-issuing the
                        // same ACQUIRE giver, but a geo-unreachable one (Wolf Hunt at Gludio, ~148k across the
                        // ocean) only produces abandoned routes. While the cooldown is armed, null the goal so
                        // the ZoneRouter.plan fallback below runs plain far-travel farming until the window
                        // expires, instead of re-launching the dead ocean-hop route every 300ms tick.
                        if (goal != null && goal.goal == PlayerGoal.ACQUIRE
                                && acquireCooldown.isSuppressed(System.currentTimeMillis()))
                        {
                            LOGGER.info("[FleetPlay] " + account + " ACQUIRE suppressed by cooldown until "
                                + acquireCooldown.cooldownUntilMs()
                                + "; plain farming while the giver is geo-unreachable");
                            goal = null;
                        }
                        // STEP 4: RETREAT is urgent — flee immediately via the controller's
                        // clamped retreat hop. Skip route planning (the destination is one hop).
                        if (goal != null && goal.action == GoalAction.RETREAT
                                && (goal.targetX != 0 || goal.targetY != 0))
                        {
                            telemetry.recordMove(account, snapshot.x, snapshot.y, snapshot.z,
                                goal.targetX, goal.targetY, goal.targetZ,
                                goal.label, goal.reason);
                            wiring.moveTo(snapshot.x, snapshot.y, snapshot.z,
                                goal.targetX, goal.targetY, goal.targetZ);
                            emit(EventRing.TYPE_MOVE, "to",
                                goal.targetX + "," + goal.targetY, "route", goal.label);
                            info.state = "retreat";
                            info.thought = goal.reason;
                            activeRoute = null;
                            pendingHop = null;
                            break; // skip the rest of this IDLE tick
                        }
                        // STEP 4: restock intent — log and hold; don't route (the bot stays put
                        // until inventory drops or the operator intervenes).
                        if (goal != null && goal.action == GoalAction.WAIT
                                && goal.reason != null && goal.reason.contains("restock"))
                        {
                            info.state = "restock";
                            info.thought = goal.reason;
                            activeRoute = null;
                            pendingHop = null;
                            break;
                        }
                        boolean dialogDriven = false;
                        PacketLogger.EntityInfo cfgGiver = questNpcId > 0
                            ? logger.findEntityByNpcId(questNpcId) : null;
                        boolean atCfgGiver = cfgGiver != null
                            && Math.hypot(snapshot.x - cfgGiver.x, snapshot.y - cfgGiver.y) <= cfg.talkRange;
                        if (questNpcId > 0 && activeRoute == null
                                && ((goal != null && goal.action == GoalAction.BYPASS) || atCfgGiver))
                        {
                            driveQuestDialog(snapshot, logger, wiring, goal, questNpcId, questNameProp);
                            activeRoute = null;
                            pendingHop = null;
                            lastRoute = now;
                            info.state = "quest-dialog:" + goal.questTargetId;
                            info.thought = "STEP2 talking to quest NPC " + questNpcId;
                            dialogDriven = true;
                        }
                        if (!dialogDriven && activeRoute == null && now - lastRoute > config.getMovementIdleRouteMs())
                        {
                            lastRoute = now;
                            // If the controller picked a concrete MOVE destination (quest NPC /
                            // acquire / hunt), route toward it via the same hop machinery; otherwise
                            // fall back to the proven random far-travel plan (REST hold still).
                            activeRoute = null;
                            if (goal != null && goal.action == GoalAction.MOVE_TO
                                    && goal.targetX != 0 && goal.targetY != 0)
                            {
                                int rx = goal.targetX, ry = goal.targetY, rz = goal.targetZ;
                                // CONFIGURED GIVER (S3-T01): when the operator set
                                // engine.quest.npcId and the goal is ACQUIRE, route to the REAL
                                // configured giver's tracked position — the engine's quest data
                                // (synthetic registry) can name a different NPC/zone than the
                                // server's actual quest giver.
                                if (goal.goal == PlayerGoal.ACQUIRE && questNpcId > 0)
                                {
                                    PacketLogger.EntityInfo giver = logger.findEntityByNpcId(questNpcId);
                                    if (giver != null)
                                    {
                                        rx = giver.x;
                                        ry = giver.y;
                                        rz = giver.z;
                                    }
                                }
                                activeRoute = ZoneRouter.routeTo(snapshot.x, snapshot.y, snapshot.z,
                                    rx, ry, rz,
                                    "goal:" + (goal.label != null ? goal.label : goal.goal.toString()),
                                    goal.reason);
                            }
                            if (activeRoute == null)
                            {
                                // STEP 6: pick an idle-relocation aim. When frozen (previous route
                                // abandoned = zero server movement) this routes BACK toward the last-XP
                                // spot or nearest fleet mate instead of a random far point; otherwise it
                                // prefers a real guide-map landmark. The consecutive-abandon escape gate
                                // returns null -> stay put (no doomed far move re-issued every tick).
                                java.util.List<int[]> mates = new java.util.ArrayList<>();
                                for (BotInfo b : bots.values())
                                {
                                    if (b == null || b.account == null || b.account.equals(account)
                                            || !b.connected || !b.loggedIn || (b.x == 0 && b.y == 0))
                                    {
                                        continue; // skip self / offline / not-in-world mates
                                    }
                                    mates.add(new int[] { b.x, b.y, b.z });
                                }
                                // Route toward THIS bot's race's guide landmark, so an Orc/Dwarf/Elf
                                // relocates to its OWN village zone (never drifts to Talking Island).
                                // S5-T07: per-race max relocation radius (Elves/Dwarf/Orc wander less
                                // than Humans, whose farms are farther apart).
                                double maxR = config.getMovementMaxRadius() * raceRadiusFactor(cfg.race);
                                RelocationPlanner.Target reloc = relocation.choose(snapshot.level,
                                    snapshot.x, snapshot.y, snapshot.z,
                                    relocation.isFrozen(), mates, race,
                                    config.getMovementMinRadius(), maxR);
                                if (reloc != null)
                                {
                                    // EB-07: for a town/zone relocation aim (guide landmark), consult
                                    // the pure TravelPlanner: when the trip is far AND a gatekeeper leg is
                                    // affordable, prefer teleport (marked as thought; the actual gatekeeper
                                    // interaction is a later integration task); otherwise walk-hop as today.
                                    String aim = reloc.label != null && reloc.label.startsWith("reloc:zone:")
                                        ? reloc.label.substring("reloc:zone:".length()) : null;
                                    boolean teleportIntent = false;
                                    if (aim != null && !aim.isEmpty())
                                    {
                                        TravelPlanner.Plan tp = TravelPlanner.plan(aim, "",
                                            snapshot.x, snapshot.y, snapshot.z,
                                            reloc.x, reloc.y, reloc.z,
                                            info.adena, snapshot.level, config.isTeleportEnabled());
                                        teleportIntent = tp.shouldTeleport();
                                        if (teleportIntent)
                                        {
                                            info.thought = "travel: teleport to " + aim + " via gatekeeper";
                                        }
                                    }
                                    if (!teleportIntent)
                                    {
                                        activeRoute = ZoneRouter.routeTo(snapshot.x, snapshot.y, snapshot.z,
                                            reloc.x, reloc.y, reloc.z, reloc.label, reloc.reason);
                                        if (activeRoute != null)
                                        {
                                            info.thought = reloc.reason;
                                        }
                                    }
                                }
                                else if (relocation.escapeHoldActive())
                                {
                                    // S5-T04: instead of freezing for the whole hold, take ONE short
                                    // nudge step (short moves persist server-side far better than far hops)
                                    // so a genuinely stuck bot can wiggle off the bad tile.
                                    RelocationPlanner.Target n = relocation.nudge(
                                        snapshot.x, snapshot.y, snapshot.z);
                                    wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, n.x, n.y, n.z);
                                    info.state = "regen";
                                    info.thought = "escape-hold nudge (short step to break the freeze)";
                                }
                            }
                            if (activeRoute != null)
                            {
                                info.state = "travel:" + activeRoute.label;
                                info.thought = activeRoute.reason;
                            }
                        }

                        // Pull the next hop whenever nothing is in flight.
                        if (activeRoute != null && pendingHop == null && activeRoute.hasMoreHops())
                        {
                            pendingHop = activeRoute.nextHop();
                            hopSentAtMs = 0; // "not sent yet" trigger below
                            hopTimeouts = 0; // fresh hop: reset the stuck-hop counter
                        }

                        final int arriveDist = 150;
                        if (pendingHop != null)
                        {
                            boolean nearHop = Math.hypot(pendingHop[0] - snapshot.x,
                                pendingHop[1] - snapshot.y) <= arriveDist;
                            // Audit 46 P0 #2: the send/advance/resend decision is extracted into
                            // the pure HopGate helper so it is unit-testable; only the stuck-hop
                            // abandonment (timeout counter + MAX_HOP_TIMEOUTS) stays here.
                            HopGate.Action hopAction = HopGate.nextAction(now, nearHop, hopSentAtMs);

                            if (hopAction == HopGate.Action.ADVANCE)
                            {
                                // Arrived at this hop: complete the route, or advance to the next one.
                                hopTimeouts = 0; // TIM-001: a reached hop is not stuck
                                relocation.noteProgress(); // STEP 6: server moved us — not frozen
                                telemetry.recordHopResult(account, true); // S5-T06
                                if (activeRoute.hasMoreHops())
                                {
                                    pendingHop = activeRoute.nextHop();
                                    hopSentAtMs = 0;
                                    hopTimeouts = 0;
                                }
                                else
                                {
                                    activeRoute = null;
                                    pendingHop = null;
                                    info.state = "idle";
                                }
                            }
                            else if (hopAction == HopGate.Action.SEND || hopAction == HopGate.Action.RESEND)
                            {
                                // TIM-001 stuck-hop recovery: a waypoint the server never walks us
                                // toward would otherwise resend forever every 45s (the movesSent=2
                                // in 2 min stall). Resend up to ZoneRouter.MAX_HOP_TIMEOUTS, then
                                // abandon the route so the bot re-plans a fresh far point.
                                boolean resend = hopAction == HopGate.Action.SEND; // never sent -> send fresh
                                if (hopAction == HopGate.Action.RESEND)
                                {
                                    hopTimeouts++;
                                    telemetry.recordHopResult(account, false); // S5-T06
                                    resend = !ZoneRouter.isRouteStuck(hopTimeouts);
                                    if (!resend)
                                    {
                                        LOGGER.warning("[FleetPlay] " + account + " hop unreachable after "
                                            + hopTimeouts + " timeouts -> abandoning route "
                                            + activeRoute.label + " (" + pendingHop[0] + "," + pendingHop[1] + ")");
                                        // Remember this destination as geo-unreachable so ZoneRouter.plan
                                        // won't deterministically re-select the SAME zone/far point on the
                                        // next re-plan (post-respawn 54-HOP / 27-abandon loop fix).
                                        if (activeRoute != null)
                                        {
                                            zoneRouter.noteUnreachableDestination(activeRoute.destX, activeRoute.destY);
                                        }
                                        // STEP 6: count this consecutive frozen-route abandon so the
                                        // escape gate eventually holds the bot still (breaks the churn).
                                        relocation.noteAbandonedRoute();
                                        // S5-T01 (root cause: server rejects moves while isOutOfControl()
                                        // — MoveToLocation sends ActionFailed, no ValidateLocation). If
                                        // hostiles are near the bot is likely CC'd (stunned/rooted): hold
                                        // to recover instead of churning far re-plans.
                                        if (info.mobs > 0)
                                        {
                                            regenHoldUntilMs = now + REGEN_HOLD_MS;
                                            info.state = "regen";
                                            info.thought = "route abandoned near hostiles (CC?) — holding";
                                        }
                                        // ACQUIRE-failure cooldown: an abandoned goal:acquire:* route means the
                                        // giver is geo-unreachable from here (empty journal + ~148k-unit ocean hop
                                        // the server never walks the char toward). Count the abort; once the
                                        // threshold is hit, ACQUIRE is suppressed below so the ZoneRouter.plan
                                        // fallback runs plain far-travel farming instead of re-issuing this route.
                                        if (activeRoute.label != null
                                                && activeRoute.label.startsWith("goal:acquire:")
                                                && info.mobs == 0)
                                        {
                                            // Only a route abandoned with NO hostiles near is truly
                                            // geo-unreachable (the ocean-hop loop). A combat
                                            // interruption is NOT unreachable — the bot just needs
                                            // to get back to the giver; don't arm the cooldown.
                                            acquireCooldown.recordUnreachableAbort(now);
                                            if (acquireCooldown.isSuppressed(now))
                                            {
                                                LOGGER.warning("[FleetPlay] " + account + " ACQUIRE cooldown armed: "
                                                    + acquireCooldown.unreachableCount() + "/"
                                                    + acquireCooldown.maxUnreachable()
                                                    + " unreachable abandons; falling back to plain farming");
                                            }
                                            else
                                            {
                                                LOGGER.info("[FleetPlay] " + account + " ACQUIRE unreachable abort "
                                                    + acquireCooldown.unreachableCount() + "/"
                                                    + acquireCooldown.maxUnreachable()
                                                    + "; one more abort suppresses the ocean-hop goal");
                                            }
                                        }
                                        activeRoute = null;
                                        pendingHop = null;
                                        hopSentAtMs = 0;
                                        hopTimeouts = 0;
                                        lastRoute = now; // re-plan on the next idle tick
                                        break; // end this IDLE tick; don't resend the dead hop
                                    }
                                }
                                if (resend)
                                {
                                    hopSentAtMs = now;
                                    telemetry.recordMove(account, snapshot.x, snapshot.y, snapshot.z,
                                        pendingHop[0], pendingHop[1], pendingHop[2],
                                        activeRoute.label, activeRoute.reason);
                                    wiring.moveTo(snapshot.x, snapshot.y, snapshot.z,
                                        pendingHop[0], pendingHop[1], pendingHop[2]);
                                    emit(EventRing.TYPE_MOVE, "to", pendingHop[0] + "," + pendingHop[1],
                                        "route", activeRoute.label);
                                    info.state = "travel:" + activeRoute.label;
                                    info.thought = activeRoute.reason;
                                    LOGGER.info("[FleetPlay] " + account + " HOP -> " + activeRoute.label
                                        + " (" + pendingHop[0] + "," + pendingHop[1] + "," + pendingHop[2] + ") "
                                        + activeRoute.reason);
                                }
                            }
                        }
                    }
                    else if (System.currentTimeMillis() - lastWander > WANDER_INTERVAL_MS)
                    {
                        lastWander = System.currentTimeMillis();
                        int nx = snapshot.x + span(WANDER_RADIUS);
                        int ny = snapshot.y + span(WANDER_RADIUS);
                        info.state = "wander";
                        emit(EventRing.TYPE_MOVE, "to", nx + "," + ny, "route", "wander");
                        wiring.moveTo(snapshot.x, snapshot.y, snapshot.z, nx, ny, snapshot.z);
                    }
                    break;
            }

            // EB-05: real humanize knob in the LIVE loop — reaction delay is consumed every tick
            // (was decorative: HumanReactionSimulator/AntiDetectionEngine were only in tests).
            long react = humanReaction.getHumanDelay();
            Thread.sleep(TICK_MS + Math.abs(rng.nextInt() % 150) + react);
        }
    }

    private int span(int radius)
    {
        return rng.nextInt(radius * 2 + 1) - radius;
    }

    /** WPT-03: push a typed event into the shared ring (thread-safe write from the bot thread). */
    private void emit(String type, Object... kv)
    {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2)
        {
            data.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        events.add(type, account, data);
    }

    /** STEP 1C: build the controller's pure input straight from the live snapshot + packet logger. */
    private PlayContext buildPlayContext(BotSnapshot s, PacketLogger logger)
    {
        java.util.List<Hostile> hostiles = new java.util.ArrayList<>();
        if (s.hostileEntities != null)
        {
            for (EntityInfo e : s.hostileEntities)
            {
                if (e == null)
                {
                    continue;
                }
                hostiles.add(new Hostile(e.objectId, e.x, e.y, e.z));
            }
        }
        java.util.List<int[]> journal = logger.getActiveQuestList();
        // ACQUIRE-failure cooldown: a non-empty journal is real quest progress (an accept/turn-in the
        // server actually recorded) — clear any suppression so the planner authors ACQUIRE/QUEST freely.
        if (journal != null && !journal.isEmpty())
        {
            acquireCooldown.reset();
        }
        // S3-T05: keep the per-bot tracker in sync with the real journal and feed its stepIndex so
        // the planner resumes the CURRENT step across ticks/sessions (persists step progress).
        if (journal != null)
        {
            for (int[] q : journal)
            {
                if (q != null && q.length > 0 && questTracker.getActiveState(q[0]) == null)
                {
                    questTracker.startQuest(q[0]);
                }
            }
        }
        int stepIndex = 0;
        Integer priorityQuest = questTracker.getCurrentPriorityQuest();
        if (priorityQuest != null)
        {
            QuestProgressTracker.ActiveQuestState st = questTracker.getActiveState(priorityQuest);
            if (st != null)
            {
                stepIndex = st.currentStepIndex;
            }
        }
        int soulshots = -1;
        int potions = -1;
        java.util.List<BotSurvival.Item> inv = survivalInventory(logger);
        if (!inv.isEmpty())
        {
            long ss = 0;
            long hp = 0;
            for (BotSurvival.Item it : inv)
            {
                if (it == null)
                {
                    continue;
                }
                if (it.getItemId() == BotSurvival.HP_POTION_ID)
                {
                    hp += it.getCount();
                }
                // Interlude Soulshot itemId 1835 (farming ammo) - same id RestockPlanner uses.
                else if (it.getItemId() == 1835)
                {
                    ss += it.getCount();
                }
            }
            soulshots = (int) Math.min(Integer.MAX_VALUE, ss);
            potions = (int) Math.min(Integer.MAX_VALUE, hp);
        }
        return new PlayContext(s.level, s.x, s.y, s.z, s.hpCurrent, s.hpMax,
            journal != null ? journal : java.util.Collections.<int[]>emptyList(),
            hostiles, stepIndex, s.inventoryUsagePercent, soulshots, potions,
            // GK-8: real class + purse so the restock trip can carry a GearGuide weapon order.
            logger.getCharSelectClassId(), logger.getAdena());
    }

    private int parseObjId(String targetId)
    {
        if (targetId == null)
        {
            return 0;
        }
        String prefix = "objId=";
        int idx = targetId.indexOf(prefix);
        if (idx < 0)
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(targetId.substring(idx + prefix.length()));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

}
