package io.nop.stream.core.common.state.backend.memory;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 0830-3 Phase 2 (item 24 / W-4): restore-path defensive checks land as
 * typed {@link StreamException}s (error code + locating params) on the single
 * converged choke point. Each guard gets one bad-data case (asserting the
 * error code and the locating context) and one good-data case proving valid
 * snapshots are unaffected.
 */
class TestMemoryStateSerdeRestoreGuards {

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

    @SuppressWarnings("unchecked")
    private static void restoreJson(String json) throws Exception {
        Map<String, Object> data = (Map<String, Object>) JsonTool.parseNonStrict(json);
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.restoreState(new StateSnapshot(data));
    }

    private static StreamException restoreExpectingTypedError(String json) throws Exception {
        // restoreJson wraps nothing: the StreamException must propagate as-is
        try {
            restoreJson(json);
            throw new AssertionError("expected StreamException for corrupt snapshot");
        } catch (StreamException e) {
            return e;
        }
    }

    // ==================== guard 1: TimeWindow namespace field validation ====================

    @Test
    void corruptTimeWindowNamespaceFailsFastWithTypeAndStateName() throws Exception {
        String json = VALUE_STATE_JSON.replace("NAMESPACE",
                "{\"@type\":\"TimeWindow\",\"start\":100}");
        StreamException e = restoreExpectingTypedError(json);
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        assertEquals("vs", e.getParam(io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME));
        assertTrue(e.getMessage().contains("TimeWindow"), "error must name the corrupt namespace, got: " + e.getMessage());
    }

    @Test
    void validTimeWindowNamespaceStillRestores() throws Exception {
        String json = VALUE_STATE_JSON.replace("NAMESPACE",
                "{\"@type\":\"TimeWindow\",\"start\":100,\"end\":200}");
        Map<String, Object> data = (Map<String, Object>) JsonTool.parseNonStrict(json);
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.restoreState(new StateSnapshot(data));
        backend.setCurrentKey("k1");
        backend.setTypedNamespace(new io.nop.stream.core.windowing.windows.TimeWindow(100, 200));
        assertEquals(Long.valueOf(10L), backend.getState(new ValueStateDescriptor<>("vs", Long.class)).value());
    }

    // ==================== guard 2: per-pair mapValue validation ====================

    @Test
    void corruptMapValuePairFailsFastWithStateNameAndIndex() throws Exception {
        String json = MAP_STATE_JSON.replace("MAP_ENTRIES",
                "[[\"a\",1],\"corrupt-pair\"]");
        StreamException e = restoreExpectingTypedError(json);
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("ms"), "error must name the state, got: " + e.getMessage());
        assertTrue(e.getMessage().contains("#1"), "error must locate the corrupt pair index, got: " + e.getMessage());
    }

    @Test
    void nonListMapValueFailsFastWithStateName() throws Exception {
        String json = MAP_STATE_JSON.replace("MAP_ENTRIES", "\"oops\"");
        StreamException e = restoreExpectingTypedError(json);
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("ms"));
    }

    @Test
    void validMapValuePairsStillRestore() throws Exception {
        String json = MAP_STATE_JSON.replace("MAP_ENTRIES", "[[\"a\",1],[\"b\",2]]");
        Map<String, Object> data = (Map<String, Object>) JsonTool.parseNonStrict(json);
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.restoreState(new StateSnapshot(data));
        backend.setCurrentKey("k1");
        MapStateDescriptor<String, Long> desc = new MapStateDescriptor<>("ms", String.class, Long.class);
        assertEquals(Long.valueOf(1L), backend.getMapState(desc).get("a"));
        assertEquals(Long.valueOf(2L), backend.getMapState(desc).get("b"));
    }
}
