/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;
import java.util.Objects;

/**
 * Declares the cleanup mechanism active for a TTL-enabled state.
 *
 * <p>Cleanup is lazy-only: expired entries are detected and removed on access
 * (read/write eviction; {@code lazyEviction} is {@code true} by default). The
 * RocksDB backend additionally exposes an explicit on-demand sweep
 * ({@code RocksDBKeyedStateBackend.cleanupExpiredEntries()}) as a pure-Java
 * substitute for a compaction filter, but it is caller-driven — there is no
 * automatic background cleanup thread (see
 * {@code ai-dev/design/nop-stream/state-management-design.md} TTL section).
 *
 * <p>In addition, expired entries are always excluded from checkpoint snapshots
 * regardless of this flag (snapshot exclusion is not optional).
 */
public final class TtlCleanupStrategy implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final TtlCleanupStrategy DEFAULT = new TtlCleanupStrategy(true);

    private final boolean lazyEviction;

    public TtlCleanupStrategy(boolean lazyEviction) {
        this.lazyEviction = lazyEviction;
    }

    public boolean isLazyEviction() {
        return lazyEviction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TtlCleanupStrategy)) return false;
        TtlCleanupStrategy that = (TtlCleanupStrategy) o;
        return lazyEviction == that.lazyEviction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lazyEviction);
    }
}
