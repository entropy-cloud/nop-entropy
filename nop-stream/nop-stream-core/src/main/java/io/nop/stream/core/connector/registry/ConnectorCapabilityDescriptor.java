/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Immutable capability declaration of a registered connector endpoint (item 19 / P-REQ-28).
 * Field set is aligned with the connector capability matrix columns
 * ({@code docs-for-ai/03-modules/nop-stream-connectors.md}): direction, delivery semantic,
 * parallelism, recovery semantic, plus the construction parameter spec list.
 *
 * <p>Single fact source rule (connector-design.md §8.4): the declared consistency value must
 * equal the constructed endpoint's {@code getSourceConsistency()} / {@code getSinkConsistency()},
 * and {@link ConnectorParallelism#PLANNING_GATE_PARALLELISM_1} must be declared if and only if
 * the endpoint is a {@code TwoPhaseCommitSinkFunction}. Both invariants are pinned by the
 * registration discovery tests and re-checked at runtime by catalog probes.
 */
public final class ConnectorCapabilityDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String typeName;
    private final List<String> aliases;
    private final ConnectorDirection direction;
    private final String componentClass;
    private final SourceConsistencyCapability sourceConsistency;
    private final SinkConsistencyCapability sinkConsistency;
    private final ConnectorParallelism parallelism;
    private final ConnectorRecoverySemantic recoverySemantic;
    private final List<ConnectorParamDescriptor> params;

    private ConnectorCapabilityDescriptor(String typeName, List<String> aliases, ConnectorDirection direction,
                                          String componentClass, SourceConsistencyCapability sourceConsistency,
                                          SinkConsistencyCapability sinkConsistency, ConnectorParallelism parallelism,
                                          ConnectorRecoverySemantic recoverySemantic,
                                          List<ConnectorParamDescriptor> params) {
        if (typeName == null || typeName.isEmpty()) {
            throw new IllegalArgumentException("typeName must not be null or empty");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null: " + typeName);
        }
        if (componentClass == null || componentClass.isEmpty()) {
            throw new IllegalArgumentException("componentClass must not be null or empty: " + typeName);
        }
        if (direction == ConnectorDirection.SOURCE && sourceConsistency == null) {
            throw new IllegalArgumentException("sourceConsistency must not be null for SOURCE: " + typeName);
        }
        if (direction == ConnectorDirection.SOURCE && sinkConsistency != null) {
            throw new IllegalArgumentException("sinkConsistency must be null for SOURCE: " + typeName);
        }
        if (direction == ConnectorDirection.SINK && sinkConsistency == null) {
            throw new IllegalArgumentException("sinkConsistency must not be null for SINK: " + typeName);
        }
        if (direction == ConnectorDirection.SINK && sourceConsistency != null) {
            throw new IllegalArgumentException("sourceConsistency must be null for SINK: " + typeName);
        }
        if (parallelism == null) {
            throw new IllegalArgumentException("parallelism must not be null: " + typeName);
        }
        if (recoverySemantic == null) {
            throw new IllegalArgumentException("recoverySemantic must not be null: " + typeName);
        }
        this.typeName = typeName;
        this.aliases = aliases == null ? Collections.emptyList() : List.copyOf(aliases);
        this.direction = direction;
        this.componentClass = componentClass;
        this.sourceConsistency = sourceConsistency;
        this.sinkConsistency = sinkConsistency;
        this.parallelism = parallelism;
        this.recoverySemantic = recoverySemantic;
        this.params = params == null ? Collections.emptyList() : List.copyOf(params);
    }

    public static Builder source(String typeName, String componentClass) {
        return new Builder(typeName, ConnectorDirection.SOURCE, componentClass);
    }

    public static Builder sink(String typeName, String componentClass) {
        return new Builder(typeName, ConnectorDirection.SINK, componentClass);
    }

    /** Registered connector type name (direction-scoped namespace). */
    public String getTypeName() {
        return typeName;
    }

    /** Alias names resolvable to this type; empty when the factory declares none. */
    public List<String> getAliases() {
        return aliases;
    }

    public ConnectorDirection getDirection() {
        return direction;
    }

    /** FQCN of the endpoint implementation class this factory constructs. */
    public String getComponentClass() {
        return componentClass;
    }

    /** Delivery semantic for SOURCE registrations; null for SINK. */
    public SourceConsistencyCapability getSourceConsistency() {
        return sourceConsistency;
    }

    /** Delivery semantic for SINK registrations; null for SOURCE. */
    public SinkConsistencyCapability getSinkConsistency() {
        return sinkConsistency;
    }

    public ConnectorParallelism getParallelism() {
        return parallelism;
    }

    public ConnectorRecoverySemantic getRecoverySemantic() {
        return recoverySemantic;
    }

    /** Construction parameter specs (required and optional). */
    public List<ConnectorParamDescriptor> getParams() {
        return params;
    }

    /** Delivery semantic formatted for diagnostics; direction decides which field is read. */
    public String consistencyValue() {
        return direction == ConnectorDirection.SOURCE
                ? String.valueOf(sourceConsistency)
                : String.valueOf(sinkConsistency);
    }

    public static final class Builder {
        private final String typeName;
        private final ConnectorDirection direction;
        private final String componentClass;
        private List<String> aliases;
        private SourceConsistencyCapability sourceConsistency;
        private SinkConsistencyCapability sinkConsistency;
        private ConnectorParallelism parallelism;
        private ConnectorRecoverySemantic recoverySemantic;
        private List<ConnectorParamDescriptor> params;

        private Builder(String typeName, ConnectorDirection direction, String componentClass) {
            this.typeName = typeName;
            this.direction = direction;
            this.componentClass = componentClass;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder sourceConsistency(SourceConsistencyCapability consistency) {
            this.sourceConsistency = consistency;
            return this;
        }

        public Builder sinkConsistency(SinkConsistencyCapability consistency) {
            this.sinkConsistency = consistency;
            return this;
        }

        public Builder parallelism(ConnectorParallelism parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Builder recoverySemantic(ConnectorRecoverySemantic recoverySemantic) {
            this.recoverySemantic = recoverySemantic;
            return this;
        }

        public Builder params(List<ConnectorParamDescriptor> params) {
            this.params = params;
            return this;
        }

        public ConnectorCapabilityDescriptor build() {
            return new ConnectorCapabilityDescriptor(typeName, aliases, direction, componentClass,
                    sourceConsistency, sinkConsistency, parallelism, recoverySemantic, params);
        }
    }
}
