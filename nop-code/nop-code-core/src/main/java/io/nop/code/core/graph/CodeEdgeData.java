package io.nop.code.core.graph;

/**
 * Typed edge data carried by {@link CodeRelationGraph}.
 *
 * edgeType is the mandatory edge family (CALLS / INHERITANCE / ANNOTATION / SEMANTIC);
 * relationType is the optional refinement (EXTENDS / IMPLEMENTS / SEMANTICALLY_SIMILAR_TO / ...).
 *
 * confidence is an {@link io.nop.code.core.semantic.EdgeConfidence} enum name, provenance an
 * {@link io.nop.code.core.model.EdgeProvenance} enum name — both nullable. Nullable fields are
 * omitted from the {@link io.nop.graph.api.Edge} attrs map by the projection when absent.
 */
public class CodeEdgeData {
    private final String edgeType;
    private final String sourceId;
    private final String targetId;
    private final String relationType;
    private final String confidence;
    private final String provenance;
    private final boolean directed;
    private final String sourceFilePath;
    private final String targetFilePath;
    private final double weight;

    private CodeEdgeData(Builder builder) {
        this.edgeType = builder.edgeType;
        this.sourceId = builder.sourceId;
        this.targetId = builder.targetId;
        this.relationType = builder.relationType;
        this.confidence = builder.confidence;
        this.provenance = builder.provenance;
        this.directed = builder.directed;
        this.sourceFilePath = builder.sourceFilePath;
        this.targetFilePath = builder.targetFilePath;
        this.weight = builder.weight;
    }

    public String getEdgeType() {
        return edgeType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getRelationType() {
        return relationType;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getProvenance() {
        return provenance;
    }

    public boolean isDirected() {
        return directed;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public String getTargetFilePath() {
        return targetFilePath;
    }

    public double getWeight() {
        return weight;
    }

    public static Builder of(String edgeType, String sourceId, String targetId) {
        return new Builder(edgeType, sourceId, targetId);
    }

    public static final class Builder {
        private final String edgeType;
        private final String sourceId;
        private final String targetId;
        private String relationType;
        private String confidence;
        private String provenance;
        private boolean directed = true;
        private String sourceFilePath;
        private String targetFilePath;
        private double weight = 1.0;

        private Builder(String edgeType, String sourceId, String targetId) {
            this.edgeType = edgeType;
            this.sourceId = sourceId;
            this.targetId = targetId;
        }

        public Builder relationType(String relationType) {
            this.relationType = relationType;
            return this;
        }

        public Builder confidence(String confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder provenance(String provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder directed(boolean directed) {
            this.directed = directed;
            return this;
        }

        public Builder sourceFilePath(String sourceFilePath) {
            this.sourceFilePath = sourceFilePath;
            return this;
        }

        public Builder targetFilePath(String targetFilePath) {
            this.targetFilePath = targetFilePath;
            return this;
        }

        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        public CodeEdgeData build() {
            if (edgeType == null || edgeType.isEmpty())
                throw new IllegalArgumentException("edgeType is required: edgeType=" + edgeType
                        + " source=" + sourceId + " target=" + targetId);
            if (sourceId == null || sourceId.isEmpty())
                throw new IllegalArgumentException("sourceId is required: edgeType=" + edgeType
                        + " target=" + targetId);
            if (targetId == null || targetId.isEmpty())
                throw new IllegalArgumentException("targetId is required: edgeType=" + edgeType
                        + " source=" + sourceId);
            return new CodeEdgeData(this);
        }
    }
}
