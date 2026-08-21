package com.aiplayer.behavior;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */


import com.aiplayer.core.BotProfile;
import java.util.logging.Logger;
import com.aiplayer.behavior.humanize.Humanization.ImperfectionInjector;

/**
 * Phase 0 Brain — replaces the over-engineered AIBrain for bootstrap.
 * Uses FSM + class presets. Zero neural networks. Zero LLM.
 */
public class BotBrain {
    private static final Logger LOGGER = Logger.getLogger(BotBrain.class.getName());

    private final String accountName;
    private final java.util.Random random; // deterministic per-bot, not random.nextDouble()
    private final BotProfile profile;
    private final StateMachine fsm;
    private final ClassPreset combatPreset;
    private final ImperfectionInjector imperfections;
    private final ProfileStore cabinet;

    private int hpPercent = 100;
    private int mpPercent = 100;
    private double targetDistance = 9999;
    private boolean targetAlive = false;
    private boolean targetInRange = false;
    private boolean chatPending = false;
    private boolean partyInvitePending = false;
    private boolean isDead = false;

    public BotBrain(String accountName) {
        this.accountName = accountName;
        this.random = new java.util.Random(accountName.hashCode());
        this.cabinet = ProfileStore.getInstance();
        this.profile = cabinet.loadProfile(accountName);
        this.fsm = new StateMachine();
        this.combatPreset = ClassPreset.forProfile(profile);
        this.imperfections = new ImperfectionInjector();
    }

    public String think() {
        if (profile == null) return "IDLE";
        if (imperfections.isAFK()) return "IDLE";
        if (imperfections.shouldDelay()) return "IDLE";

        BotState state = fsm.tick(profile, hpPercent, mpPercent, targetInRange, targetAlive,
                                 chatPending, partyInvitePending, isDead);

        switch (state) {
            case IDLE:  return doIdle();
            case FARM:  return doFarm();
            case COMBAT: return doCombat();
            case RETREAT: return doRetreat();
            case SOCIAL: return doSocial();
            case DEATH: return doDeath();
            default: return "IDLE";
        }
    }

    private String doIdle() {
        if (random.nextDouble() < 0.1) {
            int dx = (int)(random.nextDouble() * 200 - 100);
            int dy = (int)(random.nextDouble() * 200 - 100);
            return "MOVE_REL " + dx + " " + dy + " 0";
        }
        return "IDLE";
    }

    private String doFarm() {
        if (targetInRange && targetAlive) {
            fsm.transition(BotState.COMBAT);
            return doCombat();
        }
        int dx = (int)(random.nextDouble() * 600 - 300);
        int dy = (int)(random.nextDouble() * 600 - 300);
        return "MOVE_REL " + dx + " " + dy + " 0";
    }

    private String doCombat() {
        if (!targetAlive) return "LOOT";
        int skill = combatPreset.selectSkill(hpPercent, mpPercent, targetDistance, true);
        if (skill > 0) return "SKILL " + skill + " TARGET";
        return "ATTACK TARGET";
    }

    private String doRetreat() {
        if (random.nextDouble() < 0.3) return "USE_ITEM RETURN_SCROLL";
        int dx = (int)(random.nextDouble() * 800 + 200);
        int dy = (int)(random.nextDouble() * 800 + 200);
        return "MOVE_REL " + dx + " " + dy + " 0";
    }

    private String doSocial() {
        if (fsm.getTimeInState() > 5000) {
            chatPending = false;
            partyInvitePending = false;
            fsm.transition(BotState.IDLE);
        }
        return "IDLE";
    }

    private String doDeath() {
        if (random.nextDouble() < 0.05) return "RESPAWN";
        return "IDLE";
    }

    public void updateHP(int percent) { this.hpPercent = percent; }
    public void updateMP(int percent) { this.mpPercent = percent; }
    public void updateTarget(boolean inRange, boolean alive, double distance) {
        this.targetInRange = inRange;
        this.targetAlive = alive;
        this.targetDistance = distance;
    }
    public void onChatReceived() { this.chatPending = true; }
    public void onPartyInvite() { this.partyInvitePending = true; }
    public void onDeath() { this.isDead = true; }
    public void onRespawn() { this.isDead = false; this.hpPercent = 100; fsm.transition(BotState.IDLE); }

    public BotProfile getProfile() { return profile; }
    public BotState getState() { return fsm.getState(); }
    public String getAccountName() { return accountName; }
}
