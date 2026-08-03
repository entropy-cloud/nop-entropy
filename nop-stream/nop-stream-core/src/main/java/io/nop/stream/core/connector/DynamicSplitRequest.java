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
 * Stage 49 D1 (FLIP-27 范式裁定) Beam-SDF DynamicSplit{fraction} reject.
 *
 * @apiNote FLIP-27 无 fraction-splitting；whole-split assignment 已满足 v1 scope
 *           （参见 {@code connector-design.md} §4.0 D1）。本接口保留仅为向后兼容，
 *           新代码用 {@link io.nop.stream.core.source.SplitEnumerator}。
 * @deprecated Stage 49 D1 reject — superseded by FLIP-27 whole-split assignment.
 */
@Internal
@Deprecated
public interface DynamicSplitRequest extends Serializable {
    double getFraction();
}
