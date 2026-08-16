package io.nop.datav.service.filter;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_FILTER_DEF_UNKNOWN_WIDGET;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_FILTER_DEF_WIDGET_TYPE_MISMATCH;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 筛选定义产出器单元测试（D2-4 Phase 2）。
 *
 * <p>覆盖（契约 §11.2/§11.3/§11.4 的定向断言，非仅「不抛错」）：四类参数类型缺省控件映射、
 * widget 覆盖映射、词表外/组合非法显式报错（不静默回退）、label/initialValue 透传与缺省、
 * date-range delimited 初值转换、空参数看板空定义、defaultValue 产出侧校验、不含 options、
 * 字段顺序、dateRangeValue 元数据钉死。</p>
 */
public class TestDashboardFilterDefExporter {

    private static DashboardParamDefinition def(String name, String type, Object defaultValue,
                                                String label, String widget) {
        return new DashboardParamDefinition(name, type, defaultValue, label, widget);
    }

    // ==================== 控件映射（§11.3 映射表） ====================

    @Test
    public void testFourTypesDefaultControlMapping() {
        assertEquals(DashboardParamDefinition.CONTROL_INPUT_TEXT,
                DashboardFilterDefExporter.resolveControl(def("a", "string", null, null, null)));
        assertEquals(DashboardParamDefinition.CONTROL_INPUT_NUMBER,
                DashboardFilterDefExporter.resolveControl(def("b", "number", null, null, null)));
        assertEquals(DashboardParamDefinition.CONTROL_INPUT_DATE,
                DashboardFilterDefExporter.resolveControl(def("c", "date", null, null, null)));
        assertEquals(DashboardParamDefinition.CONTROL_DATE_RANGE,
                DashboardFilterDefExporter.resolveControl(def("d", "date-range", null, null, null)));
    }

    @Test
    public void testWidgetOverrideControlMapping() {
        assertEquals(DashboardParamDefinition.CONTROL_SELECT,
                DashboardFilterDefExporter.resolveControl(def("a", "string", null, null, "dropdown")));
        assertEquals(DashboardParamDefinition.CONTROL_SELECT,
                DashboardFilterDefExporter.resolveControl(def("b", "number", null, null, "dropdown")));
        assertEquals(DashboardParamDefinition.CONTROL_INPUT_DATE,
                DashboardFilterDefExporter.resolveControl(def("c", "date", null, null, "date-picker")));
        assertEquals(DashboardParamDefinition.CONTROL_DATE_RANGE,
                DashboardFilterDefExporter.resolveControl(def("d", "date-range", null, null, "date-picker")));
    }

    @Test
    public void testUnknownWidgetThrowsExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterDefExporter.resolveControl(def("a", "string", null, null, "bogus")));
        assertEquals(ERR_DATAV_FILTER_DEF_UNKNOWN_WIDGET.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testWidgetTypeComboMismatchThrowsExplicitly() {
        // dropdown × date 类型组合非法（§11.3 映射表）
        NopException ex1 = assertThrows(NopException.class,
                () -> DashboardFilterDefExporter.resolveControl(def("a", "date-range", null, null, "dropdown")));
        assertEquals(ERR_DATAV_FILTER_DEF_WIDGET_TYPE_MISMATCH.getErrorCode(), ex1.getErrorCode());

        // date-picker × string 组合非法
        NopException ex2 = assertThrows(NopException.class,
                () -> DashboardFilterDefExporter.resolveControl(def("b", "string", null, null, "date-picker")));
        assertEquals(ERR_DATAV_FILTER_DEF_WIDGET_TYPE_MISMATCH.getErrorCode(), ex2.getErrorCode());
    }

    // ==================== initialValue（§11.4 delimited 形态） ====================

    @Test
    public void testDateRangeInitialValueIsDelimitedAbsoluteString() {
        Object initial = DashboardFilterDefExporter.resolveInitialValue(def("period", "date-range",
                Map.of("start", "2024-01-01", "end", "2024-12-31"), null, null));
        assertEquals("2024-01-01,2024-12-31", initial,
                "date-range initialValue must be delimited absolute date string (delimiter ',', format yyyy-MM-dd)");
    }

    @Test
    public void testDateRangeNoOrHalfDefaultOmitsInitialValue() {
        assertNullValue(DashboardFilterDefExporter.resolveInitialValue(def("p1", "date-range", null, null, null)));
        assertNullValue(DashboardFilterDefExporter.resolveInitialValue(def("p2", "date-range",
                Map.of("start", "2024-01-01"), null, null)));
    }

    @Test
    public void testSimpleInitialValueValidatedAndPassedThrough() {
        assertEquals("all", DashboardFilterDefExporter.resolveInitialValue(def("r", "string", "all", null, null)));
        assertEquals(10, DashboardFilterDefExporter.resolveInitialValue(def("n", "number", 10, null, null)));
        assertEquals("2024-06-15",
                DashboardFilterDefExporter.resolveInitialValue(def("d", "date", "2024-06-15", null, null)));
        assertNullValue(DashboardFilterDefExporter.resolveInitialValue(def("x", "string", null, null, null)));
    }

    @Test
    public void testInvalidDefaultValueThrowsExplicitly() {
        // date-range 默认值日期段非法
        NopException ex1 = assertThrows(NopException.class,
                () -> DashboardFilterDefExporter.resolveInitialValue(def("p", "date-range",
                        Map.of("start", "not-a-date", "end", "2024-12-31"), null, null)));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex1.getErrorCode());

        // date 类型默认值非日期（含 relative token——absolute-only 契约）
        NopException ex2 = assertThrows(NopException.class,
                () -> DashboardFilterDefExporter.resolveInitialValue(def("d", "date", "today", null, null)));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex2.getErrorCode());

        // number 类型默认值非数值
        NopException ex3 = assertThrows(NopException.class,
                () -> DashboardFilterDefExporter.resolveInitialValue(def("n", "number", "abc", null, null)));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex3.getErrorCode());
    }

    // ==================== 工件整体结构（§11.2） ====================

    @Test
    public void testExportEnvelopeAndFieldOrderFollowsParamConfigOrder() {
        Map<String, Object> out = DashboardFilterDefExporter.export("dash-1", List.of(
                def("region", "string", "all", "区域", "dropdown"),
                def("limit", "number", 10, null, null),
                def("saleDate", "date", null, "日期", null),
                def("period", "date-range", Map.of("start", "2024-01-01", "end", "2024-12-31"), "日期范围", null)));

        assertEquals("dash-1", out.get("dashboardId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) out.get("fields");
        assertEquals(4, fields.size(), "field order must follow paramConfig declaration order");
        assertEquals("region", fields.get(0).get("name"));
        assertEquals("limit", fields.get(1).get("name"));
        assertEquals("saleDate", fields.get(2).get("name"));
        assertEquals("period", fields.get(3).get("name"));

        // label 透传 / 缺省为 name
        assertEquals("区域", fields.get(0).get("label"));
        assertEquals("limit", fields.get(1).get("label"), "absent label defaults to name");
        assertEquals("日期范围", fields.get(3).get("label"));

        // control 映射落地
        assertEquals(DashboardParamDefinition.CONTROL_SELECT, fields.get(0).get("control"));
        assertEquals(DashboardParamDefinition.CONTROL_INPUT_NUMBER, fields.get(1).get("control"));
        assertEquals(DashboardParamDefinition.CONTROL_INPUT_DATE, fields.get(2).get("control"));
        assertEquals(DashboardParamDefinition.CONTROL_DATE_RANGE, fields.get(3).get("control"));

        // initialValue：透传 / 省略 / delimited
        assertEquals("all", fields.get(0).get("initialValue"));
        assertEquals(10, fields.get(1).get("initialValue"));
        assertFalse(fields.get(2).containsKey("initialValue"), "no defaultValue → initialValue key omitted");
        assertEquals("2024-01-01,2024-12-31", fields.get(3).get("initialValue"));
    }

    @Test
    public void testExportEmptyDefinitionsProducesEmptyFields() {
        Map<String, Object> out = DashboardFilterDefExporter.export("dash-empty", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) out.get("fields");
        assertNotNull(fields);
        assertTrue(fields.isEmpty(), "no-paramConfig dashboard exports empty definition (not an error)");

        Map<String, Object> nullOut = DashboardFilterDefExporter.export("dash-null", null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nullFields = (List<Map<String, Object>>) nullOut.get("fields");
        assertTrue(nullFields.isEmpty());
    }

    @Test
    public void testExportCarriesDateRangeValueMetadata() {
        Map<String, Object> out = DashboardFilterDefExporter.export("dash-meta", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> dateRangeValue = (Map<String, Object>) out.get("dateRangeValue");
        assertEquals(",", dateRangeValue.get("delimiter"));
        assertEquals("yyyy-MM-dd", dateRangeValue.get("valueFormat"));
    }

    @Test
    public void testExportFieldsContainNoOptionsKey() {
        Map<String, Object> out = DashboardFilterDefExporter.export("dash-opts", List.of(
                def("region", "string", "all", null, "dropdown")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) out.get("fields");
        assertFalse(fields.get(0).containsKey("options"),
                "adjudicated: definition carries no options (page schema supplies dropdown candidates, §11.3)");
    }

    private static void assertNullValue(Object value) {
        assertEquals(null, value);
    }
}
