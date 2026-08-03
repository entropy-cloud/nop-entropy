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

/**
 * Reserved for future Beam-SDF style restriction tracking.
 *
 * @apiNote Stage 49 D1 (FLIP-27 范式裁定): Beam-SDF 的 RestrictionTracker 被裁定为
 *           {@code reject}（FLIP-27 无 fraction-splitting，whole-split assignment 不需要
 *           restriction 内的位置声明）。本接口保留仅为向后兼容已序列化的 savepoint，
 *           新代码一律使用 {@link io.nop.stream.core.source.SourceReader}（pull 模型）。
 * @deprecated Stage 49 D1 reject — superseded by FLIP-27
 *             {@link io.nop.stream.core.source.SourceReader#pollNext()} (whole-split pull model).
 */
@Internal
@Deprecated
public interface RestrictionTracker<R> extends Serializable {

    boolean tryClaim(R restriction);

    R getRestriction();

    Object getProgress();

    Object snapshotWatermarkEstimatorState();
}
