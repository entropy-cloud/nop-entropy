package io.nop.stream.rocksdb;

import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 0830-3 Phase 4 (Phase 1 guard-parity adjudication): the rocksdb
 * full-JSON restore path decodes the same StateSnapshot structures as the
 * core backend, so the item 24 defensive checks land here with the same
 * semantics — corrupt data fails fast as a typed {@link StreamException}
 * with locating params, never as a bare NPE/CCE.
 */
class TestRocksDBRestoreGuards {

    @TempDir
    File tempDir;

    private static final String VALUE_STATE_JSON = "{"
            + "\"keyType\":\"java.lang.String\","
            + "\"states\":{\"vs\":{"
            + "\"stateType\":\"ValueState\","
            + "\"valueType\":\"java.lang.Long\","
            + "\"entries\":["
            + "{\"namespace\":NAMESPACE,\"key\":\"k1\",\"value\":10}"
            + "]}}}";

    private static final String MAP_STATE_JSON = "{"
            + "\"keyType\":\"java.lang.String\","
            + "\"states\":{\"ms\":{"
            + "\"stateType\":\"MapState\","
            + "\"valueType\":\"java.lang.Long\","
            + "\"mapKeyType\":\"java.lang.String\","
            + "\"entries\":["
            + "{\"namespace\":\"_default_\",\"key\":\"k1\",\"mapValue\":MAP_ENTRIES}"
            + "]}}}";

    private RocksDBKeyedStateBackend<String> newBackend() {
        return new RocksDBKeyedStateBackend<>(tempDir.getAbsolutePath(), String.class, 1, null);
    }

    @SuppressWarnings("unchecked")
    private void restoreJson(String json) throws Exception {
        Map<String, Object> data = (Map<String, Object>) io.nop.core.lang.json.JsonTool.parseNonStrict(json);
        RocksDBKeyedStateBackend<String> backend = newBackend();
        try {
            backend.restoreState(new StateSnapshot(data));
        } finally {
            backend.close();
        }
    }

    // ==================== guard 1: TimeWindow namespace field validation (parity) ====================

    @Test
    void corruptTimeWindowNamespaceFailsFastAsTypedError() throws Exception {
        String json = VALUE_STATE_JSON.replace("NAMESPACE",
                "{\"@type\":\"TimeWindow\",\"start\":100}"); // end missing
        StreamException e = assertThrows(StreamException.class, () -> restoreJson(json));
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("TimeWindow"), "error must name the corrupt namespace: " + e.getMessage());
    }

    @Test
    void validTimeWindowNamespaceStillRestores() throws Exception {
        // public states address the default namespace only; the TimeWindow-decode
        // path is exercised through the internal list state (typed namespace)
        String json = "{"
                + "\"keyType\":\"java.lang.String\","
                + "\"states\":{\"ils\":{"
                + "\"stateType\":\"InternalListState\","
                + "\"valueType\":\"java.lang.Long\","
                + "\"entries\":["
                + "{\"namespace\":{\"@type\":\"TimeWindow\",\"start\":100,\"end\":200},\"key\":\"k1\",\"listValue\":[4,5]}"
                + "]}}}";
        Map<String, Object> data = (Map<String, Object>) io.nop.core.lang.json.JsonTool.parseNonStrict(json);
        RocksDBKeyedStateBackend<String> backend = newBackend();
        try {
            backend.restoreState(new StateSnapshot(data));
            backend.setCurrentKey("k1");
            InternalListState<String, TimeWindow, Long> restored =
                    backend.getInternalListState(new ListStateDescriptor<>("ils", Long.class));
            restored.setCurrentNamespace(new TimeWindow(100, 200));
            int n = 0;
            for (Long ignored : restored.get()) {
                n++;
            }
            assertEquals(2, n);
        } finally {
            backend.close();
        }
    }

    // ==================== guard 2: per-pair mapValue validation (parity) ====================

    @Test
    void corruptMapValuePairFailsFastWithStateNameAndIndex() throws Exception {
        String json = MAP_STATE_JSON.replace("MAP_ENTRIES", "[[\"a\",1],42]");
        StreamException e = assertThrows(StreamException.class, () -> restoreJson(json));
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("ms"), "error must name the state: " + e.getMessage());
        assertTrue(e.getMessage().contains("#1"), "error must locate the corrupt pair index: " + e.getMessage());
    }

    @Test
    void nonListMapValueFailsFastWithStateName() throws Exception {
        String json = MAP_STATE_JSON.replace("MAP_ENTRIES", "\"oops\"");
        StreamException e = assertThrows(StreamException.class, () -> restoreJson(json));
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("ms"));
    }
}
