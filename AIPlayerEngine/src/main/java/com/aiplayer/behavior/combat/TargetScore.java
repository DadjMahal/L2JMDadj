package com.aiplayer.behavior.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Immutable target evaluation score.
 * Higher = better target.
 */
public final class TargetScore implements Comparable<TargetScore> {
    public final int targetObjId;
    public final double totalScore;
    public final double levelScore;
    public final double distanceScore;
    public final double aggroScore;
    public final double threatScore;

    public TargetScore(int targetObjId, double levelScore, double distanceScore,
                       double aggroScore, double threatScore) {
        this.targetObjId = targetObjId;
        this.levelScore = levelScore;
        this.distanceScore = distanceScore;
        this.aggroScore = aggroScore;
        this.threatScore = threatScore;
        this.totalScore = levelScore * 1.5 + distanceScore * 1.0 + aggroScore * 2.0 + threatScore * 1.2;
    }

    @Override
    public int compareTo(TargetScore o) {
        return Double.compare(o.totalScore, this.totalScore); // descending
    }

    @Override
    public String toString() {
        return "Target[" + targetObjId + "] score=" + String.format("%.2f", totalScore);
    }
}
