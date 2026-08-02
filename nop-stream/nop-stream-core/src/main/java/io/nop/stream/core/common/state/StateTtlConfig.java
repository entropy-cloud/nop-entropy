/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for keyed-state time-to-live (TTL). When attached to a
 * {@link StateDescriptor} via {@link StateDescriptor#setTtlConfig(StateTtlConfig)},
 * state entries automatically expire after {@link #ttl} has elapsed since their last
 * access timestamp (the meaning of "access" is governed by {@link #updateType}).
 *
 * <p>{@link #DISABLED} is the default and disables TTL entirely, preserving the
 * pre-TTL behaviour (state grows without bound). It is treated as a sentinel by the
 * state backends: descriptors carrying {@code DISABLED} (or a {@code null} config) do
 * not activate any TTL bookkeeping, so existing workloads pay no overhead.
 *
 * <p>TTL configuration is a <b>runtime behaviour</b>, not a schema contract. It is
 * intentionally excluded from {@link StateSchemaResolver} so that
 * {@code schemaChecksum} is unaffected by TTL settings — adding/removing TTL on a state
 * does not break checkpoint restore compatibility. After restore, the live descriptor
 * (user code) re-supplies the TTL config at {@code getState()} time, which rebinds the
 * TTL context to the restored state (see plan Phase 2 "TTL rebind on restore").
 */
public final class StateTtlConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Sentinel that disables TTL. {@code StateDescriptor.getTtlConfig()} resolves
     * {@code null} to this value, so callers can treat the result as always non-null.
     */
    public static final StateTtlConfig DISABLED =
            new StateTtlConfig(Duration.ZERO, StateTtlUpdateType.Disabled, TtlCleanupStrategy.DEFAULT);

    private final Duration ttl;
    private final StateTtlUpdateType updateType;
    private final TtlCleanupStrategy cleanupStrategy;

    private StateTtlConfig(Duration ttl, StateTtlUpdateType updateType, TtlCleanupStrategy cleanupStrategy) {
        this.ttl = ttl;
        this.updateType = updateType;
        this.cleanupStrategy = cleanupStrategy;
    }

    public Duration getTtl() {
        return ttl;
    }

    public StateTtlUpdateType getUpdateType() {
        return updateType;
    }

    public TtlCleanupStrategy getCleanupStrategy() {
        return cleanupStrategy;
    }

    /**
     * Whether TTL is actually active. A config is enabled only when the update type is
     * not {@link StateTtlUpdateType#Disabled} and the TTL duration is strictly positive.
     */
    public boolean isEnabled() {
        return !updateType.isDisabled() && !ttl.isZero() && !ttl.isNegative();
    }

    long ttlMillis() {
        return ttl.toMillis();
    }

    public static Builder newBuilder(Duration ttl) {
        return new Builder(ttl);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StateTtlConfig)) return false;
        StateTtlConfig that = (StateTtlConfig) o;
        return Objects.equals(ttl, that.ttl)
                && updateType == that.updateType
                && Objects.equals(cleanupStrategy, that.cleanupStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ttl, updateType, cleanupStrategy);
    }

    public static final class Builder {
        private final Duration ttl;
        private StateTtlUpdateType updateType = StateTtlUpdateType.OnCreateAndWrite;
        private TtlCleanupStrategy cleanupStrategy = TtlCleanupStrategy.DEFAULT;

        private Builder(Duration ttl) {
            if (ttl == null) {
                throw new IllegalArgumentException("ttl must not be null");
            }
            this.ttl = ttl;
        }

        public Builder setUpdateType(StateTtlUpdateType updateType) {
            if (updateType == null) {
                throw new IllegalArgumentException("updateType must not be null");
            }
            this.updateType = updateType;
            return this;
        }

        public Builder setCleanupStrategy(TtlCleanupStrategy cleanupStrategy) {
            if (cleanupStrategy == null) {
                throw new IllegalArgumentException("cleanupStrategy must not be null");
            }
            this.cleanupStrategy = cleanupStrategy;
            return this;
        }

        public StateTtlConfig build() {
            return new StateTtlConfig(ttl, updateType, cleanupStrategy);
        }
    }
}
