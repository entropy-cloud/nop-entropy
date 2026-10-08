/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import java.util.List;

/**
 * WI17 (plan 2342, M-3 finding): normalizers for aggregate accumulator shapes after a
 * JSON round trip. {@code SnapshotCopyUtil.typedCopy} keeps the TOP-LEVEL runtime
 * class of a snapshot entry, but its nested arrays come back as {@link List} and
 * nested integral scalars come back as {@code Integer} regardless of the original
 * {@code Long} (JSON-native parsing). Every accumulator container is therefore
 * re-normalized before positional casts — without this, a continuous SQL GROUP BY
 * job fails with ClassCastException on its first record after an in-memory restore
 * (the composite accumulator row has always degraded this way; the M-3 restore test
 * was the first to exercise it).
 */
public final class SqlAccShapeUtil {

    private SqlAccShapeUtil() {
    }

    /** {@code Object[]} passthrough; a JSON-degraded {@link List} becomes an array copy. */
    public static Object[] asArray(Object acc) {
        if (acc instanceof Object[]) {
            return (Object[]) acc;
        }
        if (acc instanceof List) {
            return ((List<?>) acc).toArray();
        }
        throw new IllegalArgumentException("accumulator container must be Object[] or List, got "
                + (acc == null ? "null" : acc.getClass().getName()));
    }

    /** numeric read tolerant of Integer/Long degradation. */
    public static long asLong(Object v) {
        return ((Number) v).longValue();
    }

    /** numeric read tolerant of Float/Double degradation. */
    public static double asDouble(Object v) {
        return ((Number) v).doubleValue();
    }
}
