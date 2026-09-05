/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

/**
 * Stage 51 (roadmap item 25 / D-DRIFT-2): the single source of truth for the checkpoint
 * state format version. Lives in core so both the {@code EpochManifest} field semantics
 * and the runtime serialization envelope ({@code CheckpointSerDe.FORMAT_VERSION_KEY})
 * reference the same constant — a second independent version number is structurally
 * impossible. Runtime aliases these constants; core never depends on runtime.
 *
 * <p>{@code manifest.stateFormatVersion} on the write path is stamped at the
 * serialization choke point and always equals {@link #CURRENT_FORMAT_VERSION}; on
 * restore, a value of {@code 0} (or an absent key) means the field was not set
 * (legacy manifest written before Stage 51).
 */
public final class CheckpointFormatVersions {

    /** Current checkpoint state format version. {@code 2} = explicit envelope present. */
    public static final int CURRENT_FORMAT_VERSION = 2;

    /** Legacy format: no explicit {@code formatVersion} marker in JSON, treated as v1 on read. */
    public static final int LEGACY_FORMAT_VERSION = 1;

    /** Sentinel for "field not set" on the {@code EpochManifest.stateFormatVersion} int field. */
    public static final int UNSET_FORMAT_VERSION = 0;

    private CheckpointFormatVersions() {
    }
}
