package com.aiplayer.behavior.humanize;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */


import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.core.GameStateMirror;

/**
 * Generates human-like input variance for mouse, clicks, and camera.
 * Detection systems analyze input patterns for:
 * - Perfectly straight lines (bots snap to targets)
 * - Consistent click timing (humans have micro-variance)
 * - No overshoot/undershoot (humans correct themselves)
 * - Static camera (humans look around constantly)
 * - Instant 180 turns (humans turn gradually)
 *
 * This module adds realistic noise to all input channels while
 * respecting each AI Player's unique behavioral fingerprint.
 */
public final class InputRandomizer {

    // Mouse movement profiles
    public enum MouseProfile {
        STEADY,      // Smooth, controlled movements
        JITTERY,     // Slight hand tremor
        SWIFT,       // Fast with slight overshoot
        DELIBERATE,  // Slow and careful
        LAZY         // Sluggish, low effort
    }

    private final String accountName;
    private final BehavioralFingerprint fingerprint;

    // Current mouse profile (can shift based on context)
    private MouseProfile activeProfile = MouseProfile.STEADY;
    private long profileExpiry = 0;

    // Camera state
    private int cameraYaw = 0;
    private int cameraPitch = 0;
    private long lastCameraMove = 0;
    private int cameraIdleTargetYaw = 0;
    private boolean cameraHasTarget = false;

    public InputRandomizer(String accountName, BehavioralFingerprint fingerprint) {
        this.accountName = accountName;
        this.fingerprint = fingerprint;
    }

    // ================================================================
    // MOUSE MOVEMENT
    // ================================================================

    /**
     * Generate a human-like mouse path from (fromX, fromY) to (toX, toY).
     * Returns a list of waypoints that form a slightly curved, imperfect path.
     * Humans rarely move in perfect straight lines.
     */
    public List<int[]> generateMousePath(int fromX, int fromY, int toX, int toY) {
        List<int[]> waypoints = new ArrayList<>();
        waypoints.add(new int[]{fromX, fromY});

        double dist = Math.hypot(toX - fromX, toY - fromY);
        if (dist < 5) {
            waypoints.add(new int[]{toX, toY});
            return waypoints;
        }

        // Number of intermediate points based on distance
        int segments = Math.max(1, (int) (dist / 80));
        segments = Math.min(segments, 8);

        // Bezier control point offset (creates curve)
        double curveStrength = (1.0 - fingerprint.inputAccuracy) * 30 + 5;
        double perpX = -(toY - fromY) / dist;
        double perpY = (toX - fromX) / dist;
        double offset = HumanizedRandom.normal(0, curveStrength);

        int cpX = (int) ((fromX + toX) / 2.0 + perpX * offset);
        int cpY = (int) ((fromY + toY) / 2.0 + perpY * offset);

        // Generate quadratic bezier points
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) (segments + 1);
            double invT = 1.0 - t;

            // Quadratic bezier
            double x = invT * invT * fromX + 2 * invT * t * cpX + t * t * toX;
            double y = invT * invT * fromY + 2 * invT * t * cpY + t * t * toY;

            // Add micro-jitter based on profile
            double jitter = getProfileJitter();
            x += HumanizedRandom.normal(0, jitter);
            y += HumanizedRandom.normal(0, jitter);

            waypoints.add(new int[]{(int) Math.round(x), (int) Math.round(y)});
        }

        // Final point with possible overshoot (humans often overshoot small targets)
        if (dist < 200 && ThreadLocalRandom.current().nextDouble() < fingerprint.errorProneness * 0.3) {
            double overshoot = HumanizedRandom.normal(0, 8);
            waypoints.add(new int[]{
                (int) Math.round(toX + overshoot),
                (int) Math.round(toY + HumanizedRandom.normal(0, 8))
            });
            // Correction back to target
            waypoints.add(new int[]{toX, toY});
        } else {
            waypoints.add(new int[]{toX, toY});
        }

        return waypoints;
    }

    /**
     * Perturb a target coordinate with accuracy-based noise.
     * Less accurate players click farther from the true target.
     */
    public int[] perturbClick(int targetX, int targetY, int targetRadius) {
        double accuracy = fingerprint.inputAccuracy;
        // Even perfect players miss by a few pixels
        double maxMiss = (1.0 - accuracy) * targetRadius * 1.5 + 2;
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = HumanizedRandom.normal(0, maxMiss / 3.0);

        int x = targetX + (int) (Math.cos(angle) * distance);
        int y = targetY + (int) (Math.sin(angle) * distance);
        return new int[]{x, y};
    }

    /**
     * Sometimes humans double-click when they meant to single-click,
     * or hold slightly too long. Returns adjusted click count/duration.
     */
    public ClickResult humanizeClick(int intendedClicks, int intendedDurationMs) {
        Random rnd = ThreadLocalRandom.current();
        int clicks = intendedClicks;
        int duration = intendedDurationMs;

        // Occasional double-click instead of single
        if (intendedClicks == 1 && rnd.nextDouble() < fingerprint.errorProneness * 0.1) {
            clicks = 2;
            duration = (int) (duration * 0.6); // Faster second click
        }

        // Slight duration variance
        duration = (int) HumanizedRandom.normal(duration, duration * 0.15);
        duration = Math.max(20, duration);

        return new ClickResult(clicks, duration);
    }

    // ================================================================
    // CAMERA / VIEW ANGLE
    // ================================================================

    /**
     * Decide if camera should move this tick.
     * Humans constantly make small camera adjustments.
     */
    public boolean shouldMoveCamera() {
        long now = System.currentTimeMillis();
        if (now - lastCameraMove < 300) return false;

        double activity = fingerprint.cameraActivity;
        // Base chance per tick (500ms) to adjust camera
        double chance = 0.15 + activity * 0.35;
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /**
     * Generate next camera yaw/pitch adjustment.
     * Returns delta values (add to current).
     */
    public int[] nextCameraDelta() {
        lastCameraMove = System.currentTimeMillis();

        double activity = fingerprint.cameraActivity;

        // Small adjustments most of the time, occasional larger scans
        boolean largeScan = ThreadLocalRandom.current().nextDouble() < 0.08;
        int yawDelta, pitchDelta;

        if (largeScan) {
            yawDelta = (int) HumanizedRandom.skewedInt(-60, 60, activity > 0.5 ? 0.3 : -0.3);
            pitchDelta = (int) HumanizedRandom.normal(0, 15);
        } else {
            yawDelta = (int) HumanizedRandom.normal(0, 8 + activity * 12);
            pitchDelta = (int) HumanizedRandom.normal(0, 5 + activity * 5);
        }

        // Clamp pitch to reasonable range
        pitchDelta = Math.max(-20, Math.min(20, pitchDelta));

        return new int[]{yawDelta, pitchDelta};
    }

    /**
     * Set a camera look-at target for idle behavior.
     * Humans often idly look toward points of interest.
     */
    public void setCameraLookTarget(int targetYaw) {
        this.cameraIdleTargetYaw = targetYaw;
        this.cameraHasTarget = true;
    }

    public void clearCameraLookTarget() {
        this.cameraHasTarget = false;
    }

    /**
     * Get camera delta when idling (slow drift toward target).
     */
    public int[] getIdleCameraDelta(int currentYaw) {
        if (!cameraHasTarget) {
            return nextCameraDelta();
        }

        int diff = normalizeAngle(cameraIdleTargetYaw - currentYaw);
        if (Math.abs(diff) < 5) {
            cameraHasTarget = false;
            return new int[]{0, 0};
        }

        // Slow drift
        int yawDelta = (int) (diff * 0.15);
        yawDelta += (int) HumanizedRandom.normal(0, 2);
        return new int[]{yawDelta, (int) HumanizedRandom.normal(0, 3)};
    }

    // ================================================================
    // MOVEMENT INPUT
    // ================================================================

    /**
     * Perturb a movement destination to avoid robotic precision.
     * Humans often don't walk to the exact same spot.
     */
    public int[] perturbDestination(int x, int y, int z, int arrivalRadius) {
        double accuracy = fingerprint.inputAccuracy;
        double maxError = (1.0 - accuracy) * arrivalRadius * 0.8 + 3;

        int px = HumanizedRandom.perturb(x, (int) maxError);
        int py = HumanizedRandom.perturb(y, (int) maxError);
        int pz = z; // Keep Z exact for geo

        return new int[]{px, py, pz};
    }

    /**
     * Occasionally humans release W and re-press, or stutter-step.
     */
    public boolean shouldStutterStep() {
        return ThreadLocalRandom.current().nextDouble() < fingerprint.errorProneness * 0.15;
    }

    /**
     * Get duration of a stutter step in ms.
     */
    public int getStutterDuration() {
        return HumanizedRandom.skewedInt(80, 400, 0.5);
    }

    // ================================================================
    // PROFILE MANAGEMENT
    // ================================================================

    /**
     * Temporarily shift mouse profile based on context.
     * E.g., combat = SWIFT, inventory = DELIBERATE
     */
    public void setTemporaryProfile(MouseProfile profile, long durationMs) {
        this.activeProfile = profile;
        this.profileExpiry = System.currentTimeMillis() + durationMs;
    }

    private MouseProfile getActiveProfile() {
        if (System.currentTimeMillis() > profileExpiry) {
            activeProfile = MouseProfile.STEADY;
        }
        return activeProfile;
    }

    private double getProfileJitter() {
        switch (getActiveProfile()) {
            case STEADY:    return 1.5;
            case JITTERY:   return 4.0;
            case SWIFT:     return 2.5;
            case DELIBERATE:return 0.8;
            case LAZY:      return 1.0;
            default:        return 1.5;
        }
    }

    private static int normalizeAngle(int angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    // ------------------------------------------------------------------

    public static final class ClickResult {
        public final int clicks;
        public final int durationMs;

        public ClickResult(int clicks, int durationMs) {
            this.clicks = clicks;
            this.durationMs = durationMs;
        }
    }
}
