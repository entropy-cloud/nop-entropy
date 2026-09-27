/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import java.util.List;
import java.util.Map;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Single-point (plan 2278 Phase 2 convergence) validator/iterator for the
 * {@code mapValue} field of a MapState snapshot entry, shared by the core
 * {@code MemoryStateSerDe} and the rocksdb {@code RocksDBSnapshotSerDe} restore
 * paths — the two previously carried verbatim copies of this loop (item 24
 * parity guards).
 *
 * <p>Per-pair mapValue validation: a corrupt pair previously surfaced as a bare
 * ClassCastException/IndexOutOfBoundsException with no state context; bad data
 * fails fast with a typed, locatable error instead. Validation and pair delivery
 * are interleaved in ONE pass (validate pair i, then hand it to the sink) so the
 * failure point and any sink side effects occur in exactly the same order as the
 * pre-convergence per-side loops. Error message strings are part of the
 * observable contract and are kept verbatim.
 */
public final class MapValuePairValidator {

    /** Receives one validated [key, value] snapshot pair. */
    @FunctionalInterface
    public interface PairSink {
        void accept(Object keyObj, Object valueObj) throws Exception;
    }

    private MapValuePairValidator() {
    }

    /**
     * Validates the entry's {@code mapValue} payload and hands every valid
     * [key, value] pair to {@code sink}.
     *
     * @throws StreamException when the payload is not a list of key/value pairs
     */
    public static void forEachValidPair(Map<String, Object> entry, String stateName,
                                        PairSink sink) throws Exception {
        Object raw = entry.get("mapValue");
        if (raw != null && !(raw instanceof List)) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_STATE_NAME, stateName)
                    .param(ARG_ACTUAL_TYPE, raw.getClass().getName())
                    .param(ARG_DETAIL, "mapValue of state '" + stateName
                            + "' is not a list of key/value pairs; snapshot is corrupt or foreign");
        }
        @SuppressWarnings("unchecked")
        List<List<Object>> mapEntries = (List<List<Object>>) raw;
        if (mapEntries != null) {
            for (int i = 0; i < mapEntries.size(); i++) {
                Object pairObj = mapEntries.get(i);
                if (!(pairObj instanceof List) || ((List<?>) pairObj).size() < 2) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_STATE_NAME, stateName)
                            .param(ARG_DETAIL, "mapValue pair #" + i + " of state '" + stateName
                                    + "' is not a [key, value] pair (got: "
                                    + (pairObj == null ? "null" : pairObj.toString())
                                    + "); snapshot is corrupt or foreign");
                }
                List<Object> me = (List<Object>) pairObj;
                sink.accept(me.get(0), me.get(1));
            }
        }
    }
}
