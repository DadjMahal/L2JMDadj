package com.aiplayer.phase0.town;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.phase0.town.VendorDatabase.VendorInfo;
import com.aiplayer.phase0.town.VendorDatabase.VendorType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles gatekeeper teleportation.
 * Knows teleport routes, costs, and level requirements.
 * Decides when teleporting is worth the adena vs running.
 *
 * Phase 0: Hardcoded core Interlude teleport routes.
 * Phase 1: Parse gatekeeper multisell/teleport lists dynamically.
 */
public final class TeleportManager {

    private static final long TELEPORT_INTERACT_DELAY_MS = 1200;
    private static final long TELEPORT_CONFIRM_DELAY_MS = 2000;
    private static final int WALK_COST_THRESHOLD = 5000; // Adena threshold: if walk would take >5min, teleport if cost < this

    private final String accountName;
    private final L2JProtocol protocol;
    private final TownNavigator navigator;

    private volatile TeleportState state = TeleportState.IDLE;
    private volatile VendorInfo currentGatekeeper = null;
    private volatile TeleportRoute pendingRoute = null;
    private volatile long nextActionTime = 0;

    enum TeleportState {
        IDLE, NAVIGATING, INTERACTING, SELECTING_DESTINATION, CONFIRMING, DONE
    }

    public static final class TeleportRoute {
        public final String fromTown;
        public final String toTown;
        public final int cost;
        public final int requiredLevel;
        public final int destinationX;
        public final int destinationY;
        public final int destinationZ;
        public final String description;

        public TeleportRoute(String fromTown, String toTown, int cost, int requiredLevel,
                             int destinationX, int destinationY, int destinationZ,
                             String description) {
            this.fromTown = fromTown;
            this.toTown = toTown;
            this.cost = cost;
            this.requiredLevel = requiredLevel;
            this.destinationX = destinationX;
            this.destinationY = destinationY;
            this.destinationZ = destinationZ;
            this.description = description;
        }
    }

    // Hardcoded core Interlude teleport routes
    private static final Map<String, List<TeleportRoute>> ROUTES = new HashMap<>();

    static {
        initRoutes();
    }

    private static void initRoutes() {
        // From Giran
        addRoute("Giran", new TeleportRoute("Giran", "Aden", 9200, 20, 147450, 27030, -2208, "Town of Aden"));
        addRoute("Giran", new TeleportRoute("Giran", "Dion", 3400, 20, 15671, 142983, -2704, "Town of Dion"));
        addRoute("Giran", new TeleportRoute("Giran", "Gludio", 3700, 20, -14608, 123920, -3120, "Town of Gludio"));
        addRoute("Giran", new TeleportRoute("Giran", "Oren", 5900, 20, 82956, 53162, -1496, "Town of Oren"));
        addRoute("Giran", new TeleportRoute("Giran", "HuntersVillage", 4400, 20, 116819, 76966, -2714, "Hunters Village"));
        addRoute("Giran", new TeleportRoute("Giran", "GiranHarbor", 1000, 20, 48400, 186200, -3600, "Giran Harbor"));

        // From Aden
        addRoute("Aden", new TeleportRoute("Aden", "Giran", 9200, 20, 83358, 147934, -3400, "Town of Giran"));
        addRoute("Aden", new TeleportRoute("Aden", "Dion", 7100, 20, 15671, 142983, -2704, "Town of Dion"));
        addRoute("Aden", new TeleportRoute("Aden", "Gludio", 7600, 20, -14608, 123920, -3120, "Town of Gludio"));
        addRoute("Aden", new TeleportRoute("Aden", "Oren", 6300, 20, 82956, 53162, -1496, "Town of Oren"));
        addRoute("Aden", new TeleportRoute("Aden", "HuntersVillage", 5600, 20, 116819, 76966, -2714, "Hunters Village"));

        // From Dion
        addRoute("Dion", new TeleportRoute("Dion", "Giran", 3400, 20, 83358, 147934, -3400, "Town of Giran"));
        addRoute("Dion", new TeleportRoute("Dion", "Aden", 7100, 20, 147450, 27030, -2208, "Town of Aden"));
        addRoute("Dion", new TeleportRoute("Dion", "Gludio", 1800, 20, -14608, 123920, -3120, "Town of Gludio"));
        addRoute("Dion", new TeleportRoute("Dion", "Oren", 4800, 20, 82956, 53162, -1496, "Town of Oren"));

        // From Gludio
        addRoute("Gludio", new TeleportRoute("Gludio", "Giran", 3700, 20, 83358, 147934, -3400, "Town of Giran"));
        addRoute("Gludio", new TeleportRoute("Gludio", "Aden", 7600, 20, 147450, 27030, -2208, "Town of Aden"));
        addRoute("Gludio", new TeleportRoute("Gludio", "Dion", 1800, 20, 15671, 142983, -2704, "Town of Dion"));
        addRoute("Gludio", new TeleportRoute("Gludio", "Oren", 5300, 20, 82956, 53162, -1496, "Town of Oren"));

        // From Oren
        addRoute("Oren", new TeleportRoute("Oren", "Giran", 5900, 20, 83358, 147934, -3400, "Town of Giran"));
        addRoute("Oren", new TeleportRoute("Oren", "Aden", 6300, 20, 147450, 27030, -2208, "Town of Aden"));
        addRoute("Oren", new TeleportRoute("Oren", "Dion", 4800, 20, 15671, 142983, -2704, "Town of Dion"));
        addRoute("Oren", new TeleportRoute("Oren", "Gludio", 5300, 20, -14608, 123920, -3120, "Town of Gludio"));
        addRoute("Oren", new TeleportRoute("Oren", "HuntersVillage", 3400, 20, 116819, 76966, -2714, "Hunters Village"));

        // From Hunters Village
        addRoute("HuntersVillage", new TeleportRoute("HuntersVillage", "Giran", 4400, 20, 83358, 147934, -3400, "Town of Giran"));
        addRoute("HuntersVillage", new TeleportRoute("HuntersVillage", "Aden", 5600, 20, 147450, 27030, -2208, "Town of Aden"));
        addRoute("HuntersVillage", new TeleportRoute("HuntersVillage", "Oren", 3400, 20, 82956, 53162, -1496, "Town of Oren"));
    }

    private static void addRoute(String from, TeleportRoute route) {
        ROUTES.computeIfAbsent(from, k -> new ArrayList<>()).add(route);
    }

    public TeleportManager(String accountName, L2JProtocol protocol, TownNavigator navigator) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.navigator = navigator;
    }

    /**
     * Request teleport to a destination town.
     */
    public void teleportTo(String destinationTown) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) {
            state = TeleportState.DONE;
            return;
        }

        String currentTown = VendorDatabase.detectTown(self.x, self.y, self.z);
        if (currentTown == null) {
            state = TeleportState.IDLE;
            return;
        }

        if (currentTown.equals(destinationTown)) {
            state = TeleportState.DONE;
            return;
        }

        TeleportRoute route = findRoute(currentTown, destinationTown);
        if (route == null) {
            state = TeleportState.IDLE;
            return;
        }

        if (self.level < route.requiredLevel) {
            state = TeleportState.IDLE;
            return;
        }

        if (self.adena < route.cost) {
            state = TeleportState.IDLE;
            return;
        }

        this.pendingRoute = route;
        this.state = TeleportState.NAVIGATING;
        this.currentGatekeeper = null;
    }

    /**
     * Main tick — call every 500ms while teleporting.
     */
    public void tick() {
        if (state == TeleportState.IDLE || state == TeleportState.DONE) return;

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        switch (state) {
            case NAVIGATING:
                tickNavigate(self);
                break;
            case INTERACTING:
                tickInteract(self);
                break;
            case SELECTING_DESTINATION:
                tickSelect(self);
                break;
            case CONFIRMING:
                tickConfirm(self);
                break;
            default:
                break;
        }
    }

    private void tickNavigate(BotStateSnapshot self) {
        String town = VendorDatabase.detectTown(self.x, self.y, self.z);
        if (town == null || pendingRoute == null) {
            state = TeleportState.IDLE;
            return;
        }

        VendorInfo target = VendorDatabase.findNearestVendor(town, VendorType.GATEKEEPER, self.x, self.y, self.z);
        if (target == null) {
            state = TeleportState.IDLE;
            return;
        }

        this.currentGatekeeper = target;

        double distSq = distSq(self.x, self.y, self.z, target.x, target.y, target.z);
        if (distSq <= target.interactRange * target.interactRange) {
            state = TeleportState.INTERACTING;
            nextActionTime = System.currentTimeMillis() + jitter(500, 900);
        } else {
            navigator.moveTo(target.x, target.y, target.z);
        }
    }

    private void tickInteract(BotStateSnapshot self) {
        if (currentGatekeeper == null || pendingRoute == null) {
            state = TeleportState.IDLE;
            return;
        }
        try {
            protocol.sendNpcAction(currentGatekeeper.npcId);
        } catch (java.io.IOException e) {
            // best-effort
        }
        state = TeleportState.SELECTING_DESTINATION;
        nextActionTime = System.currentTimeMillis() + jitter(TELEPORT_INTERACT_DELAY_MS, TELEPORT_INTERACT_DELAY_MS + 600);
    }

    private void tickSelect(BotStateSnapshot self) {
        if (pendingRoute == null) {
            state = TeleportState.IDLE;
            return;
        }
        // Send teleport request with destination index
        // Phase 0: Use destination string/ID mapping
        try {
            protocol.sendTeleportRequest(pendingRoute.toTown);
        } catch (java.io.IOException e) {
            // best-effort
        }
        state = TeleportState.CONFIRMING;
        nextActionTime = System.currentTimeMillis() + jitter(TELEPORT_CONFIRM_DELAY_MS, TELEPORT_CONFIRM_DELAY_MS + 1000);
    }

    private void tickConfirm(BotStateSnapshot self) {
        // Confirm teleport (some servers require confirmation packet)
        if (pendingRoute != null) {
            try {
                protocol.sendTeleportConfirm();
            } catch (java.io.IOException e) {
                // best-effort
            }
        }
        state = TeleportState.DONE;
    }

    /**
     * Decide if teleporting is worth it vs walking.
     */
    public boolean shouldTeleport(String destinationTown, int estimatedWalkSeconds) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        String currentTown = VendorDatabase.detectTown(self.x, self.y, self.z);
        if (currentTown == null) return false;

        TeleportRoute route = findRoute(currentTown, destinationTown);
        if (route == null) return false;

        if (self.adena < route.cost) return false;
        if (self.level < route.requiredLevel) return false;

        // Heuristic: teleport if cost is low relative to adena or walk is very long
        boolean cheap = route.cost < self.adena * 0.05; // Less than 5% of adena
        boolean longWalk = estimatedWalkSeconds > 300; // More than 5 minutes
        boolean moderateWalkExpensive = estimatedWalkSeconds > 120 && route.cost < 5000;

        return cheap || longWalk || moderateWalkExpensive;
    }

    public boolean isDone() {
        return state == TeleportState.DONE;
    }

    public boolean isIdle() {
        return state == TeleportState.IDLE;
    }

    public void reset() {
        state = TeleportState.IDLE;
        currentGatekeeper = null;
        pendingRoute = null;
    }

    public TeleportRoute getPendingRoute() {
        return pendingRoute;
    }

    // ------------------------------------------------------------------

    private TeleportRoute findRoute(String fromTown, String toTown) {
        List<TeleportRoute> routes = ROUTES.get(fromTown);
        if (routes == null) return null;
        for (TeleportRoute r : routes) {
            if (r.toTown.equals(toTown)) {
                return r;
            }
        }
        return null;
    }

    private static double distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        long dz = (long) z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private static long jitter(long base, long max) {
        return ThreadLocalRandom.current().nextLong(base, max + 1);
    }
}