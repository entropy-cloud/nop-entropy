/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.SerializerFingerprint;

/**
 * Stage 33: lookup contract for registered {@link StateMigrationFunction}s.
 *
 * <p>Implemented by {@code StreamComponents} (per {@code checkpoint-design.md}
 * §8.4.1) so the state backend can query for a matching migration function when
 * a restored state's {@link SerializerFingerprint} differs from the current
 * descriptor's fingerprint.
 *
 * <p><b>No silent default</b>: implementations return {@code null} when no
 * matching function is registered, so the caller fails fast with
 * {@code ERR_STREAM_STATE_SCHEMA_MISMATCH} rather than silently degrading.
 */
@Internal
public interface StateMigrationRegistry {

    /**
     * Find a registered migration function matching the given source and target
     * fingerprints for the named state.
     *
     * @param stateName    keyed state name (matches {@code StateDescriptor.name})
     * @param source       restored-state fingerprint; must equal
     *                     {@link StateMigrationFunction#sourceFingerprint()}
     * @param target       current-descriptor fingerprint; must equal
     *                     {@link StateMigrationFunction#targetFingerprint()}
     * @return the matching migration function, or {@code null} if none registered
     *         for this {@code (stateName, source, target)} triple
     */
    StateMigrationFunction<?, ?> findMigration(String stateName,
                                               SerializerFingerprint source,
                                               SerializerFingerprint target);
}
