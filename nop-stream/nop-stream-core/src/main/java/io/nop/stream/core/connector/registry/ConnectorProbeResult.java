/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import java.io.Serializable;

/**
 * Outcome of one {@link StreamConnectorCatalog} probe: registry resolution → factory
 * construction → capability alignment check → endpoint close (when {@code AutoCloseable}).
 * All failures are reported as typed exceptions thrown by the probe, never swallowed.
 */
public final class ConnectorProbeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String typeName;
    private final ConnectorDirection direction;
    private final String endpointClass;
    private final String deliverySemantic;
    private final boolean instanceConsistencyAligned;
    private final boolean autoClosed;

    public ConnectorProbeResult(String typeName, ConnectorDirection direction, String endpointClass,
                                String deliverySemantic, boolean instanceConsistencyAligned, boolean autoClosed) {
        this.typeName = typeName;
        this.direction = direction;
        this.endpointClass = endpointClass;
        this.deliverySemantic = deliverySemantic;
        this.instanceConsistencyAligned = instanceConsistencyAligned;
        this.autoClosed = autoClosed;
    }

    public String getTypeName() {
        return typeName;
    }

    public ConnectorDirection getDirection() {
        return direction;
    }

    /** FQCN of the constructed endpoint instance. */
    public String getEndpointClass() {
        return endpointClass;
    }

    /** Descriptor-declared delivery semantic of the probed connector. */
    public String getDeliverySemantic() {
        return deliverySemantic;
    }

    /**
     * Whether the constructed endpoint's instance-level consistency declaration was compared
     * against the descriptor (false for split sources, which have no instance-level accessor).
     */
    public boolean isInstanceConsistencyAligned() {
        return instanceConsistencyAligned;
    }

    /** Whether the endpoint implemented {@code AutoCloseable} and was closed by the probe. */
    public boolean isAutoClosed() {
        return autoClosed;
    }
}
