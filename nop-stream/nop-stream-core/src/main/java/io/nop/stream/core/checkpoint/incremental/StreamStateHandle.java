/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logical state reference produced by a task-side keyed state backend: a named
 * state ({@code stateName}) owned by an operator ({@code operatorId}) maps to
 * the set of {@link SharedStateHandle SST files} that physically hold its
 * content. The coordinator resolves these handles against
 * {@link SharedStateRegistry} for cross-checkpoint de-duplication.
 */
public final class StreamStateHandle implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String operatorId;
    private final String stateName;
    private final List<SharedStateHandle> sstHandles;

    public StreamStateHandle(String operatorId, String stateName, List<SharedStateHandle> sstHandles) {
        this.operatorId = operatorId;
        this.stateName = stateName;
        this.sstHandles = sstHandles != null
                ? Collections.unmodifiableList(new ArrayList<>(sstHandles))
                : Collections.emptyList();
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getStateName() {
        return stateName;
    }

    public List<SharedStateHandle> getSstHandles() {
        return sstHandles;
    }

    @Override
    public String toString() {
        return "StreamStateHandle{operatorId=" + operatorId + ", stateName=" + stateName
                + ", sstCount=" + sstHandles.size() + "}";
    }
}
