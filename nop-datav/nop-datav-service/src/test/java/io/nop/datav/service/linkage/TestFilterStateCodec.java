package io.nop.datav.service.linkage;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.biz.FilterState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_FILTER_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * filter_state 编解码器单元测试（D2-3 Phase 3）。
 *
 * <p>覆盖：序列化/反序列化往返一致、空 state 处理、格式错误显式失败（非返回 null）。
 * panelSelections 子元素结构校验、urlState 可选。</p>
 */
public class TestFilterStateCodec {

    @Test
    public void testEncodeDecodeRoundTrip() {
        Map<String, Object> globalFilters = new LinkedHashMap<>();
        globalFilters.put("region", "east");
        globalFilters.put("dateRange.start", "2024-01-01");
        globalFilters.put("dateRange.end", "2024-06-30");

        Map<String, Map<String, Object>> panelSelections = new LinkedHashMap<>();
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("field", "region");
        selection.put("value", "east");
        panelSelections.put("p-source", selection);

        String urlState = "region=east&dateRange.start=2024-01-01";

        String json = FilterStateCodec.encode(globalFilters, panelSelections, urlState);
        FilterState state = FilterStateCodec.decode("dash-1", json);

        assertEquals("east", state.getGlobalFilters().get("region"));
        assertEquals("2024-01-01", state.getGlobalFilters().get("dateRange.start"));
        assertEquals("2024-06-30", state.getGlobalFilters().get("dateRange.end"));
        assertEquals(3, state.getGlobalFilters().size());

        assertEquals(1, state.getPanelSelections().size());
        FilterState.PanelSelection ps = state.getPanelSelections().get("p-source");
        assertNotNull(ps);
        assertEquals("region", ps.getField());
        assertEquals("east", ps.getValue());

        assertEquals(urlState, state.getUrlState());
    }

    @Test
    public void testEncodeEmptyReturnsValidJson() {
        String json = FilterStateCodec.encode(null, null, null);
        assertNotNull(json);
        FilterState state = FilterStateCodec.decode("dash-2", json);
        assertTrue(state.getGlobalFilters().isEmpty());
        assertTrue(state.getPanelSelections().isEmpty());
        assertNull(state.getUrlState());
    }

    @Test
    public void testDecodeNullEmptyReturnsEmptyState() {
        FilterState s1 = FilterStateCodec.decode("dash-3", null);
        FilterState s2 = FilterStateCodec.decode("dash-3", "");
        assertTrue(s1.getGlobalFilters().isEmpty());
        assertTrue(s2.getGlobalFilters().isEmpty());
    }

    @Test
    public void testDecodeMalformedJsonThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> FilterStateCodec.decode("dash-4", "{not valid json"));
        assertEquals(ERR_DATAV_INVALID_FILTER_STATE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testDecodeNonObjectThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> FilterStateCodec.decode("dash-5", "[1,2,3]"));
        assertEquals(ERR_DATAV_INVALID_FILTER_STATE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testDecodePanelSelectionsNotObjectThrows() {
        String json = "{\"panelSelections\":\"not-an-object\"}";
        NopException ex = assertThrows(NopException.class,
                () -> FilterStateCodec.decode("dash-6", json));
        assertEquals(ERR_DATAV_INVALID_FILTER_STATE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testDecodePanelSelectionValueMissingFieldThrows() {
        String json = "{\"panelSelections\":{\"p1\":{\"value\":\"east\"}}}";
        NopException ex = assertThrows(NopException.class,
                () -> FilterStateCodec.decode("dash-7", json));
        assertEquals(ERR_DATAV_INVALID_FILTER_STATE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testDecodePanelSelectionValueNotObjectThrows() {
        String json = "{\"panelSelections\":{\"p1\":\"not-an-object\"}}";
        NopException ex = assertThrows(NopException.class,
                () -> FilterStateCodec.decode("dash-8", json));
        assertEquals(ERR_DATAV_INVALID_FILTER_STATE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testDecodeGlobalFiltersNotObjectThrows() {
        String json = "{\"globalFilters\":\"not-an-object\"}";
        NopException ex = assertThrows(NopException.class,
                () -> FilterStateCodec.decode("dash-9", json));
        assertEquals(ERR_DATAV_INVALID_FILTER_STATE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testEncodeWithEmptyPanelSelections() {
        Map<String, Object> globalFilters = Map.of("region", "east");
        String json = FilterStateCodec.encode(globalFilters, Map.of(), "region=east");
        FilterState state = FilterStateCodec.decode("dash-10", json);
        assertEquals("east", state.getGlobalFilters().get("region"));
        assertTrue(state.getPanelSelections().isEmpty());
        assertFalse(state.getGlobalFilters().isEmpty());
    }

    @Test
    public void testEncodeUrlStateNullOmittedInJson() {
        String json = FilterStateCodec.encode(Map.of("region", "east"), null, null);
        // urlState null is omitted from json; decode returns null urlState (legit)
        FilterState state = FilterStateCodec.decode("dash-11", json);
        assertNull(state.getUrlState());
        assertEquals("east", state.getGlobalFilters().get("region"));
    }

    @Test
    public void testRoundTripPreservesInsertionOrder() {
        Map<String, Object> globalFilters = new LinkedHashMap<>();
        globalFilters.put("zeta", "1");
        globalFilters.put("alpha", "2");
        globalFilters.put("middle", "3");

        String json = FilterStateCodec.encode(globalFilters, null, null);
        FilterState state = FilterStateCodec.decode("dash-12", json);

        // LinkedHashMap preserves insertion order
        Object[] keys = state.getGlobalFilters().keySet().toArray();
        assertEquals("zeta", keys[0]);
        assertEquals("alpha", keys[1]);
        assertEquals("middle", keys[2]);
    }
}
