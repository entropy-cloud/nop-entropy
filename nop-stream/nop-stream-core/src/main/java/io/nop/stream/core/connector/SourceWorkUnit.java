/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

import java.io.Serializable;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.data.DataBean;

import io.nop.stream.core.checkpoint.TaskLocation;

@Internal
@DataBean
public class SourceWorkUnit implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String sourceId;
    private final String splitId;
    private final Object restriction;
    private final TaskLocation owner;
    private final long sizeEstimate;
    private final Object progress;
    private final Object watermarkEstimatorState;

    public SourceWorkUnit(String sourceId, String splitId, Object restriction,
                          TaskLocation owner, long sizeEstimate,
                          Object progress, Object watermarkEstimatorState) {
        this.sourceId = sourceId;
        this.splitId = splitId;
        this.restriction = restriction;
        this.owner = owner;
        this.sizeEstimate = sizeEstimate;
        this.progress = progress;
        this.watermarkEstimatorState = watermarkEstimatorState;
    }

    public SourceWorkUnit() {
        this(null, null, null, null, 0, null, null);
    }

    public String getSourceId() { return sourceId; }
    public String getSplitId() { return splitId; }
    public Object getRestriction() { return restriction; }
    public TaskLocation getOwner() { return owner; }
    public long getSizeEstimate() { return sizeEstimate; }
    public Object getProgress() { return progress; }
    public Object getWatermarkEstimatorState() { return watermarkEstimatorState; }
}
