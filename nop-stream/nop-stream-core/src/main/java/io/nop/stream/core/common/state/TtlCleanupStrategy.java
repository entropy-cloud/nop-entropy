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
 * (read/write eviction). The RocksDB backend additionally exposes an explicit
 * on-demand sweep ({@code RocksDBKeyedStateBackend.cleanupExpiredEntries()}) as
 * a pure-Java substitute for a compaction filter, but it is caller-driven —
 * there is no automatic background cleanup thread (see
 * {@code ai-dev/design/nop-stream/state-management-design.md} TTL section).
 *
 * <p><strong>{@code lazyEviction} is currently a documented no-op knob</strong>
 * (a reserved extension slot, not live behavior): {@code true} is the default and
 * the only supported value. {@code new TtlCleanupStrategy(false)} is accepted,
 * serialized into checkpoints and round-trips via equals/hashCode, but has
 * <em>no behavioral effect</em> — eviction is lazy either way, and expired
 * entries are always excluded from checkpoint snapshots regardless of this flag
 * (snapshot exclusion is not optional). A {@code false} implementation
 * (background/compaction cleanup) does not exist in the engine today; if you
 * need eager cleanup, call the RocksDB on-demand sweep explicitly.
 */
public final class TtlCleanupStrategy implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final TtlCleanupStrategy DEFAULT = new TtlCleanupStrategy(true);

    /**
     * Reserved knob, see the class javadoc: {@code true} (default) is the only
     * value with a defined meaning; {@code false} is accepted for forward
     * compatibility but changes nothing today.
     */
    private final boolean lazyEviction;

    public TtlCleanupStrategy(boolean lazyEviction) {
        this.lazyEviction = lazyEviction;
    }

    /**
     * Reserved knob, see the class javadoc — does not toggle any live
     * behavior in the current engine (cleanup is always lazy).
     */
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
