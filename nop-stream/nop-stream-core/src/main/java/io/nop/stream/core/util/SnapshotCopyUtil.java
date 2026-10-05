/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.util;

import io.nop.core.lang.json.JsonTool;

/**
 * R5 ST-01/ST-13: point-in-time defensive copies for checkpoint snapshots.
 *
 * <p>Checkpoint snapshots are built on the task thread but persisted
 * asynchronously; handing out live references lets post-barrier mutations leak
 * into (and tear) the persisted JSON. {@link #defensiveCopy(Object)} freezes a
 * value into the exact JSON-native shape that the persist thread and the
 * restore decoders already produce and consume, so the copy is
 * round-trip-compatible by construction.
 *
 * <p>Failure semantics are inherited from {@link JsonTool#serialize}: under the
 * {@code nop.core.json.serialize-only-data-bean} gate a non-DataBean value
 * fails with the same typed error here that the legacy persist-time
 * serialization would have raised, one checkpoint phase earlier. No fallback
 * bypass is introduced.
 */
public final class SnapshotCopyUtil {
    private SnapshotCopyUtil() {
    }

    public static Object defensiveCopy(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return JsonTool.parse(JsonTool.serialize(value, false));
    }

    /**
     * R5 ST-13: type-preserving freeze for operator-state entries restored
     * without a schema-driven rematerialization step. The copy keeps the
     * runtime class (a fresh instance re-parsed from JSON), so in-memory
     * restore paths that match on concrete types keep working while
     * post-snapshot mutations no longer leak. Failure semantics are
     * {@link JsonTool#serialize}'s own (only-data-bean gate included).
     */
    public static Object typedCopy(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return JsonTool.parseBeanFromText(JsonTool.serialize(value, false), value.getClass());
    }
}
