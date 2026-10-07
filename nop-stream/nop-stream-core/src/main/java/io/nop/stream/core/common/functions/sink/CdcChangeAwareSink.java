/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.sink;

import io.nop.api.core.stream.RowKind;

/**
 * WI7 (plan 2336): marker for sinks that can correctly handle (or reject on their own
 * contract) records carrying non-INSERT {@link RowKind} change semantics. The engine
 * guard in {@code StreamSinkOperator.processElement} fails fast (typed) when such a
 * record reaches a sink that does NOT implement this marker — silently appending a
 * DELETE is data corruption presented as a feature.
 *
 * <p><strong>Contract premise</strong>: "aware" means the sink derives its handling
 * from its own value contract — e.g. the jdbc-2pc keyed modes derive the operation from
 * the ChangeEvent / opMapper, and its INSERT mode already rejects change events typed.
 * The engine guard sees the record-level metadata; the sink sees the value. A value
 * that is NOT a change event yet carries DELETE metadata has no production path in the
 * current wiring (metadata is stamped only from {@code ChangeSemanticView} values).
 */
public interface CdcChangeAwareSink {
}
