package io.nop.code.service.graph;

/**
 * Configurable weights for the surprising-connection score
 * (graph-discovery-and-export-design.md §3.1 heuristic defaults).
 *
 * Evaluation order is fixed by the contract: confidence bonus, cross file kind (dormant —
 * single-source code corpus has no document nodes), cross top-level dir, cross community,
 * the semantically_similar_to multiplier (applied after all additive bonuses, before the
 * edge-to-hub bonus), then edge-to-hub.
 */
public class SurpriseConfig {

    private final int crossFileKindBonus;
    private final int crossDirBonus;
    private final int crossCommunityBonus;
    private final double similarMultiplier;
    private final int edgeHubBonus;
    private final int hubLowDegreeMax;
    private final int hubHighDegreeMin;

    private SurpriseConfig(Builder builder) {
        this.crossFileKindBonus = builder.crossFileKindBonus;
        this.crossDirBonus = builder.crossDirBonus;
        this.crossCommunityBonus = builder.crossCommunityBonus;
        this.similarMultiplier = builder.similarMultiplier;
        this.edgeHubBonus = builder.edgeHubBonus;
        this.hubLowDegreeMax = builder.hubLowDegreeMax;
        this.hubHighDegreeMin = builder.hubHighDegreeMin;
    }

    public int getCrossFileKindBonus() { return crossFileKindBonus; }
    public int getCrossDirBonus() { return crossDirBonus; }
    public int getCrossCommunityBonus() { return crossCommunityBonus; }
    public double getSimilarMultiplier() { return similarMultiplier; }
    public int getEdgeHubBonus() { return edgeHubBonus; }
    public int getHubLowDegreeMax() { return hubLowDegreeMax; }
    public int getHubHighDegreeMin() { return hubHighDegreeMin; }

    /** confBonus by EdgeConfidence name; unknown/absent names yield 0 (never a default). */
    public int confBonus(String confidence) {
        if (confidence == null) return 0;
        switch (confidence) {
            case "AMBIGUOUS": return 3;
            case "INFERRED": return 2;
            case "EXTRACTED": return 1;
            default: return 0;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SurpriseConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        private int crossFileKindBonus = 2;
        private int crossDirBonus = 2;
        private int crossCommunityBonus = 1;
        private double similarMultiplier = 1.5;
        private int edgeHubBonus = 1;
        private int hubLowDegreeMax = 2;
        private int hubHighDegreeMin = 5;

        public Builder crossFileKindBonus(int v) { this.crossFileKindBonus = v; return this; }
        public Builder crossDirBonus(int v) { this.crossDirBonus = v; return this; }
        public Builder crossCommunityBonus(int v) { this.crossCommunityBonus = v; return this; }
        public Builder similarMultiplier(double v) { this.similarMultiplier = v; return this; }
        public Builder edgeHubBonus(int v) { this.edgeHubBonus = v; return this; }
        public Builder hubLowDegreeMax(int v) { this.hubLowDegreeMax = v; return this; }
        public Builder hubHighDegreeMin(int v) { this.hubHighDegreeMin = v; return this; }

        public SurpriseConfig build() {
            return new SurpriseConfig(this);
        }
    }
}
