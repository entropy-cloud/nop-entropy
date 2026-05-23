/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@DataBean
public class StreamBackendCapability implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Set<StreamRequirement> supportedRequirements;
    private final boolean supportsDistributedExecution;
    private final boolean supportsRemoteStateService;
    private final boolean supportsRescale;

    public StreamBackendCapability(Set<StreamRequirement> supportedRequirements,
                                   boolean supportsDistributedExecution,
                                   boolean supportsRemoteStateService,
                                   boolean supportsRescale) {
        this.supportedRequirements = supportedRequirements != null
                ? Collections.unmodifiableSet(new HashSet<>(supportedRequirements))
                : Collections.emptySet();
        this.supportsDistributedExecution = supportsDistributedExecution;
        this.supportsRemoteStateService = supportsRemoteStateService;
        this.supportsRescale = supportsRescale;
    }

    public StreamBackendCapability() {
        this(Collections.emptySet(), false, false, false);
    }

    public Set<StreamRequirement> getSupportedRequirements() {
        return supportedRequirements;
    }

    public boolean isSupportsDistributedExecution() {
        return supportsDistributedExecution;
    }

    public boolean isSupportsRemoteStateService() {
        return supportsRemoteStateService;
    }

    public boolean isSupportsRescale() {
        return supportsRescale;
    }

    public boolean supports(StreamRequirement requirement) {
        return supportedRequirements.contains(requirement);
    }

    public static StreamBackendCapability localRuntime() {
        Set<StreamRequirement> supported = new HashSet<>();
        supported.add(StreamRequirement.STATEFUL_PROCESSING);
        supported.add(StreamRequirement.KEYED_STATE_PROCESSING);
        supported.add(StreamRequirement.DURABLE_CHECKPOINT);
        supported.add(StreamRequirement.TWO_PHASE_COMMIT_SINK);
        supported.add(StreamRequirement.AT_LEAST_ONCE);
        return new StreamBackendCapability(supported, false, false, false);
    }
}
