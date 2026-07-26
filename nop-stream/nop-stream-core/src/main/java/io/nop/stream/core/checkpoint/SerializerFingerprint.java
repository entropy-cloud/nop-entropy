/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.Objects;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Per-state schema fingerprint embedded as checkpoint-internal metadata.
 *
 * <p>It captures the type signature of a keyed state ({@code stateType} +
 * class FQNs of the value type and any sub-types) as a stable checksum, so
 * that restore-time compatibility can be detected when code-declared state
 * types diverge from checkpointed state types.
 *
 * <p>This is checkpoint-internal metadata. Operators and users never touch
 * it. It is auto-computed by the state backend, persisted inside the per-state
 * info map, and compared at {@code getState()} time.
 *
 * <p>Stage 29 uses type-signature-level checksum only. {@code schemaVersion}
 * always defaults to {@code 1}; it is persisted as forward-looking metadata
 * for Stage 33 (state migration) which may introduce version-based branching.
 */
@DataBean
public class SerializerFingerprint implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_SCHEMA_VERSION = 1;

    private final String stateName;
    private final int schemaVersion;
    private final String schemaChecksum;

    public SerializerFingerprint() {
        this(null, DEFAULT_SCHEMA_VERSION, null);
    }

    public SerializerFingerprint(String stateName, int schemaVersion, String schemaChecksum) {
        this.stateName = stateName;
        this.schemaVersion = schemaVersion > 0 ? schemaVersion : DEFAULT_SCHEMA_VERSION;
        this.schemaChecksum = schemaChecksum;
    }

    public String getStateName() {
        return stateName;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getSchemaChecksum() {
        return schemaChecksum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerializerFingerprint that = (SerializerFingerprint) o;
        return schemaVersion == that.schemaVersion
                && Objects.equals(stateName, that.stateName)
                && Objects.equals(schemaChecksum, that.schemaChecksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateName, schemaVersion, schemaChecksum);
    }

    @Override
    public String toString() {
        return "SerializerFingerprint{stateName='" + stateName + '\''
                + ", schemaVersion=" + schemaVersion
                + ", schemaChecksum='" + schemaChecksum + '\'' + '}';
    }
}
