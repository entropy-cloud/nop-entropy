/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.*;

@DataBean
public class SourceEnumeratorState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<String> discoveredSplits;
    private final List<String> unassignedSplits;
    private final Map<String, String> assignedSplits;
    private final Set<String> finishedSplits;
    private final Set<String> pendingAcknowledgements;
    private final Object discoveryCursor;

    public SourceEnumeratorState(List<String> discoveredSplits,
                                 List<String> unassignedSplits,
                                 Map<String, String> assignedSplits,
                                 Set<String> finishedSplits,
                                 Set<String> pendingAcknowledgements,
                                 Object discoveryCursor) {
        this.discoveredSplits = discoveredSplits != null
                ? Collections.unmodifiableList(new ArrayList<>(discoveredSplits)) : Collections.emptyList();
        this.unassignedSplits = unassignedSplits != null
                ? Collections.unmodifiableList(new ArrayList<>(unassignedSplits)) : Collections.emptyList();
        this.assignedSplits = assignedSplits != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(assignedSplits)) : Collections.emptyMap();
        this.finishedSplits = finishedSplits != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(finishedSplits)) : Collections.emptySet();
        this.pendingAcknowledgements = pendingAcknowledgements != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(pendingAcknowledgements)) : Collections.emptySet();
        this.discoveryCursor = discoveryCursor;
    }

    public SourceEnumeratorState() {
        this(null, null, null, null, null, null);
    }

    public List<String> getDiscoveredSplits() { return discoveredSplits; }
    public List<String> getUnassignedSplits() { return unassignedSplits; }
    public Map<String, String> getAssignedSplits() { return assignedSplits; }
    public Set<String> getFinishedSplits() { return finishedSplits; }
    public Set<String> getPendingAcknowledgements() { return pendingAcknowledgements; }
    public Object getDiscoveryCursor() { return discoveryCursor; }
}
