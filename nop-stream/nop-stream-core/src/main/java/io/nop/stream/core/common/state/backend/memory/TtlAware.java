/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.state.TtlContext;

/**
 * Marker implemented by every memory keyed-state class so that
 * {@link MemoryKeyedStateBackend} can bind a {@link TtlContext} without an 8-way
 * {@code instanceof} ladder. The key type is fixed to {@link TypedNamespaceAndKey}.
 */
interface TtlAware {
    void bindTtl(TtlContext<TypedNamespaceAndKey> ctx);
}
