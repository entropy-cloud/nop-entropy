/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.util.Map;

import io.nop.api.core.annotations.core.Internal;
import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.util.ClassNameValidator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * ST-02: re-materializes a restored snapshot key back to the backend's
 * declared key class before any key-group computation.
 *
 * <p>JSON persistence (both the memory and the RocksDB full-snapshot formats)
 * degrades typed keys to JSON-native forms: {@code Long(123)} comes back as
 * {@code Integer(123)} (AR-01), a {@code Date} as a field map, a
 * {@code @DataBean} key as a {@code LinkedHashMap}. Computing the key-group id
 * from the degraded form hashes a different input than the live write path
 * (which hashes the typed key), so restored entries land in the wrong
 * key-group and become unreachable. Restores must therefore re-materialize
 * every raw snapshot key to the declared key class FIRST, and only then feed
 * it to {@link KeyGroupAssignment#assignToKeyGroup(Object, int)}.
 *
 * <p>The transformation is an identity for keys that already match the
 * declared type (String and primitive-wrapper keys), so callers may apply it
 * unconditionally. A failed re-materialization fails fast (no-silent-skip)
 * instead of returning the mismatched key.
 */
@Internal
public final class StateKeyRematerializer {

    /** Snapshot-header field carrying the job's key class name (both serdes write it). */
    public static final String KEY_TYPE_FIELD = "keyType";

    private StateKeyRematerializer() {
    }

    /**
     * Re-materialize {@code key} to {@code keyType}, mirroring the live-path
     * typed key so key-group hashing sees the same input as the write path.
     *
     * @param key     the raw snapshot key (JSON-native form after persistence)
     * @param keyType the backend's declared key class; {@code null} or
     *                {@code Object.class} leaves the key untouched
     * @return the typed key (same instance when no re-materialization applies)
     * @throws StreamException when the key cannot be re-materialized to
     *                         {@code keyType} (corrupt or foreign snapshot)
     */
    public static Object rematerializeKey(Object key, Class<?> keyType) {
        if (key == null) {
            return null;
        }
        if (keyType == null || keyType == Object.class || keyType.isInstance(key)) {
            return key;
        }
        String json = JsonTool.serialize(key, false);
        Object rematerialized;
        try {
            rematerialized = JsonTool.parseBeanFromText(json, keyType);
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to re-materialize state key " + json
                            + " as " + keyType.getName() + " during restore");
        }
        if (rematerialized == null || !keyType.isInstance(rematerialized)) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "Failed to re-materialize state key " + json
                            + " as " + keyType.getName() + " during restore");
        }
        return rematerialized;
    }

    /**
     * Resolve the key class a restore must re-materialize snapshot keys to:
     * the snapshot header's {@code keyType} field when present, otherwise the
     * given fallback (typically the restore backend's declared key class).
     *
     * @param stateData the keyed snapshot's top-level data map (may be null)
     * @param fallback  class to use when the header carries no usable keyType
     * @return the resolved key class, or {@code null} when neither source is usable
     */
    public static Class<?> resolveSnapshotKeyType(Map<String, Object> stateData, Class<?> fallback) {
        Object header = stateData == null ? null : stateData.get(KEY_TYPE_FIELD);
        if (header instanceof String && !((String) header).isEmpty()) {
            return loadKeyType((String) header);
        }
        return fallback;
    }

    /**
     * Load a snapshot-recorded class name (fail-fast on unknown classes).
     *
     * @throws StreamException when the name is not an allowed loadable class
     */
    @SuppressWarnings("unchecked")
    public static Class<Object> loadKeyType(String typeName) {
        ClassNameValidator.validateClassName(typeName);
        try {
            return (Class<Object>) Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to load snapshot keyType class " + typeName);
        }
    }
}
