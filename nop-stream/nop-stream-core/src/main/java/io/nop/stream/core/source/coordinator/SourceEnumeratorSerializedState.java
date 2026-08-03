/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source.coordinator;

import java.io.Serializable;

/**
 * Stage 49 D2: serialized enumerator state for storage in
 * {@code EpochManifest.sourceEnumeratorSnapshots}. Carries the {@code version} prefix that
 * the matching {@code SimpleVersionedSerializer} wrote into the bytes, so restore can
 * fail-fast on incompatible state instead of silently misinterpreting it (no silent no-op,
 * plan guide #24).
 */
public final class SourceEnumeratorSerializedState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int version;
    private final byte[] stateBytes;

    public SourceEnumeratorSerializedState(int version, byte[] stateBytes) {
        this.version = version;
        this.stateBytes = stateBytes;
    }

    public int getVersion() {
        return version;
    }

    public byte[] getStateBytes() {
        return stateBytes;
    }
}
