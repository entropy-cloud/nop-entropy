/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;
import java.util.Objects;

/**
 * Declares which cleanup mechanisms are active for a TTL-enabled state.
 *
 * <p>Cleanup is layered:
 * <ul>
 *   <li><b>Lazy eviction</b> ({@code true} by default) — expired entries are detected and
 *       removed on access (read). Always available on every backend.</li>
 *   <li><b>Background cleanup</b> ({@code true} by default where applicable) — expired
 *       entries are reclaimed in bulk without relying on access. For the RocksDB backend
 *       this is realized by an explicit expired-entry sweep (the {@code rocksdbjni}
 *       binding does not expose a pure-Java compaction-filter callback, so a native JNI
 *       compaction filter is deferred — see
 *       {@code ai-dev/design/nop-stream/state-management-design.md} TTL section).</li>
 * </ul>
 *
 * <p>In addition, expired entries are always excluded from checkpoint snapshots regardless
 * of these flags (snapshot exclusion is not optional).
 */
public final class TtlCleanupStrategy implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final TtlCleanupStrategy DEFAULT = new TtlCleanupStrategy(true, true);

    private final boolean lazyEviction;
    private final boolean backgroundCleanup;

    public TtlCleanupStrategy(boolean lazyEviction, boolean backgroundCleanup) {
        this.lazyEviction = lazyEviction;
        this.backgroundCleanup = backgroundCleanup;
    }

    public boolean isLazyEviction() {
        return lazyEviction;
    }

    public boolean isBackgroundCleanup() {
        return backgroundCleanup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TtlCleanupStrategy)) return false;
        TtlCleanupStrategy that = (TtlCleanupStrategy) o;
        return lazyEviction == that.lazyEviction && backgroundCleanup == that.backgroundCleanup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lazyEviction, backgroundCleanup);
    }
}
