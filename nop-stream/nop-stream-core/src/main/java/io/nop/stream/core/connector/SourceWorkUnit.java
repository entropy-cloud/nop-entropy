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

/**
 * Stage 49 D1 (FLIP-27 范式裁定) SourceWorkUnit superseded — by new
 * {@link io.nop.stream.core.source.Source} / {@link io.nop.stream.core.source.SourceSplit}
 * contract.
 *
 * @apiNote 旧 Beam-SDF 占位类（含 restriction/watermarkEstimatorState 字段）。新代码
 *           一律用 {@link io.nop.stream.core.source.Source}（FLIP-27 风格 whole-split
 *           assignment）。保留类仅为向后兼容已序列化的旧 savepoint（如有）。
 * @deprecated Stage 49 D1 superseded — use
 *             {@link io.nop.stream.core.source.Source} /
 *             {@link io.nop.stream.core.source.SimpleSourceSplit}.
 */
@Internal
@Deprecated
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
