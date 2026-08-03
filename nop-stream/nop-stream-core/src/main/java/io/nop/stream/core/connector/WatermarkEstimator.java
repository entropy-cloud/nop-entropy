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
 * Stage 49 D1 (FLIP-27 范式裁定) WatermarkEstimator defer（v1 Non-Goal successor）。
 *
 * @apiNote source 侧 watermark estimation 是独立的 watermark 推进模型，与现有
 *           {@code TimestampsAndWatermarksOperator} 路径不重叠；v1 不引入以避免两套
 *           watermark 路径并存（参见 {@code connector-design.md} §4.0 D1）。
 *           保留接口以便 successor plan 实现时不破坏既有类查找。
 * @deprecated Stage 49 D1 defer — successor scope (source-side watermark estimation).
 */
@Internal
@Deprecated
public interface WatermarkEstimator extends Serializable {

    void observe(long timestamp);

    long getCurrentWatermark();

    Object snapshotState();

    void restoreState(Object state);
}
