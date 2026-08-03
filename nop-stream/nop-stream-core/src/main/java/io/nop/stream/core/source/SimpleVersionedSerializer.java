/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import java.io.Serializable;

/**
 * A versioned serializer for source split and enumerator-state objects, used to round-trip
 * them through {@code EpochManifest.sourceEnumeratorSnapshots} (Stage 49 D2) and per-split
 * cursor state in {@code TaskEpochSnapshot}.
 *
 * <p>The {@code version} prefix lets the framework reject incompatible state on restore
 * rather than silently misinterpreting it (no silent no-op, see plan guide #24).
 */
public interface SimpleVersionedSerializer<T> extends Serializable {

    /**
     * Serializes the given value together with the current {@link #getVersion() format version}.
     *
     * @param obj the value to serialize; must not be {@code null}
     * @return a byte array whose first bytes encode {@link #getVersion()}
     */
    byte[] serialize(T obj) throws Exception;

    /**
     * Deserializes a value that was produced by some version of this serializer. If the
     * version prefix does not match {@link #getVersion()}, implementations should either
     * migrate the bytes or throw a clear exception — silently interpreting incompatible
     * bytes is forbidden.
     *
     * @param version the version prefix carried in the serialized bytes
     * @param bytes   the value bytes (after the version prefix)
     */
    T deserialize(int version, byte[] bytes) throws Exception;

    /**
     * Returns the current format version. Bumped whenever the binary layout changes.
     */
    int getVersion();
}
