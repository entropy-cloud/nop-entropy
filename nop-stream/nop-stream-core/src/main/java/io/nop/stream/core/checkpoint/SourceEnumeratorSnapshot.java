/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Stage 49 D2: a per-source-vertex enumerator-state entry stored inside
 * {@link EpochManifest#getSourceEnumeratorSnapshots()}. Carries the {@code version} prefix
 * that the matching {@code SimpleVersionedSerializer} wrote into the bytes, so restore can
 * fail-fast on incompatible state instead of silently misinterpreting it (no silent no-op,
 * plan guide #24).
 *
 * <p>Value type is intentionally opaque ({@code byte[]} + version int) so that
 * {@code EpochManifest} stays generic over enumerator state shapes — the
 * {@code Source.getEnumeratorStateSerializer()} of each source vertex is responsible for
 * (de)serializing its own state type.
 */
@DataBean
public class SourceEnumeratorSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Serializer version prefix carried inside the serialized bytes. */
    private final int version;

    /** Opaque enumerator state bytes (already include version prefix where appropriate). */
    private final byte[] stateBytes;

    public SourceEnumeratorSnapshot(int version, byte[] stateBytes) {
        this.version = version;
        this.stateBytes = stateBytes;
    }

    public SourceEnumeratorSnapshot() {
        this(0, null);
    }

    public int getVersion() {
        return version;
    }

    public byte[] getStateBytes() {
        return stateBytes;
    }
}
