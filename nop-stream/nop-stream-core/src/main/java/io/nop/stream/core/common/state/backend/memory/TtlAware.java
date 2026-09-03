/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.state.TtlContext;

/**
 * Marker implemented by every memory keyed-state class so that
 * {@link MemoryKeyedStateBackend} can bind a {@link TtlContext} without an 8-way
 * {@code instanceof} ladder. The key type is fixed to {@link TypedNamespaceAndKey}.
 *
 * <p>item 21 D-2 convergence: {@link #ttlContext()} lets the backend's
 * {@code applyTtl} keep an unchanged TTL context (RK-4 core twin) instead of
 * unconditionally rebinding a fresh sidecar.
 */
interface TtlAware {
    void bindTtl(TtlContext<TypedNamespaceAndKey> ctx);

    TtlContext<TypedNamespaceAndKey> ttlContext();
}
