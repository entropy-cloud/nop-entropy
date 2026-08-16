package io.nop.datav.service.filter;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PARAM_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数定义解析 + 筛选值解析 单元测试（D2-1 Phase 1）。
 *
 * <p>覆盖：正常解析、空参数定义、格式错误显式失败（非返回 null）、
 * 复合类型 date-range 输入输出、类型校验错误路径、未知参数过滤、默认值填充。</p>
 */
public class TestDashboardParamParserAndResolver {

    // ==================== Parser ====================

    @Test
    public void testParseEmptyReturnsEmptyList() {
        assertEquals(0, DashboardParamParser.parse(null).size());
        assertEquals(0, DashboardParamParser.parse("").size());
    }

    @Test
    public void testParseNormalDefinitions() {
        String json = JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all", "label", "区域"),
                Map.of("name", "limit", "type", "number", "defaultValue", 10),
                Map.of("name", "dateRange", "type", "date-range",
                        "defaultValue", Map.of("start", "2024-01-01", "end", "2024-12-31"))
        ));
        List<DashboardParamDefinition> defs = DashboardParamParser.parse(json);
        assertEquals(3, defs.size());
        assertEquals("region", defs.get(0).getName());
        assertEquals(DashboardParamDefinition.TYPE_STRING, defs.get(0).getType());
        assertEquals("all", defs.get(0).getDefaultValue());
        assertTrue(defs.get(2).isComposite());
    }

    @Test
    public void testParseMalformedJsonThrowsExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> DashboardParamParser.parse("{not valid json"));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseNonArrayThrowsExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> DashboardParamParser.parse("{\"name\":\"region\"}"));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseElementMissingNameThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> DashboardParamParser.parse("[{\"type\":\"string\"}]"));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseUnsupportedTypeThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> DashboardParamParser.parse("[{\"name\":\"x\",\"type\":\"bogus\"}]"));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseDuplicateNameThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> DashboardParamParser.parse(
                        "[{\"name\":\"x\",\"type\":\"string\"},{\"name\":\"x\",\"type\":\"number\"}]"));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseDateRangeDefaultValueNotMapThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> DashboardParamParser.parse(
                        "[{\"name\":\"dr\",\"type\":\"date-range\",\"defaultValue\":\"2024-01-01\"}]"));
        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    // ==================== Resolver ====================

    @Test
    public void testResolveEmptyDefinitionsReturnsEmpty() {
        Map<String, Object> out = DashboardFilterResolver.resolve(null, Map.of("a", "b"));
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void testResolveStringWithDefaultValueFilling() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "region", DashboardParamDefinition.TYPE_STRING, "all", null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def), Map.of());
        assertEquals("all", out.get("region"));
    }

    @Test
    public void testResolveStringProvidedOverridesDefault() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "region", DashboardParamDefinition.TYPE_STRING, "all", null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def), Map.of("region", "East"));
        assertEquals("East", out.get("region"));
    }

    @Test
    public void testResolveDateRangeFlatKeys() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "dateRange", DashboardParamDefinition.TYPE_DATE_RANGE,
                Map.of("start", "2024-01-01", "end", "2024-12-31"), null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def),
                Map.of("dateRange.start", "2024-02-01", "dateRange.end", "2024-06-30"));
        assertEquals("2024-02-01", out.get("dateRange.start"));
        assertEquals("2024-06-30", out.get("dateRange.end"));
        assertFalse(out.containsKey("dateRange"));
    }

    @Test
    public void testResolveDateRangeDefaultsWhenMissing() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "dateRange", DashboardParamDefinition.TYPE_DATE_RANGE,
                Map.of("start", "2024-01-01", "end", "2024-12-31"), null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def), Map.of());
        assertEquals("2024-01-01", out.get("dateRange.start"));
        assertEquals("2024-12-31", out.get("dateRange.end"));
    }

    @Test
    public void testResolveDateRangePartialInputUsesDefaultForMissingSubKey() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "dateRange", DashboardParamDefinition.TYPE_DATE_RANGE,
                Map.of("start", "2024-01-01", "end", "2024-12-31"), null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def),
                Map.of("dateRange.start", "2024-03-01"));
        assertEquals("2024-03-01", out.get("dateRange.start"));
        assertEquals("2024-12-31", out.get("dateRange.end"));
    }

    @Test
    public void testResolveUnknownParamFiltered() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "region", DashboardParamDefinition.TYPE_STRING, "all", null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def),
                Map.of("region", "East", "unknownKey", "shouldDrop"));
        assertTrue(out.containsKey("region"));
        assertFalse(out.containsKey("unknownKey"), "unknown params must be explicitly filtered");
    }

    @Test
    public void testResolveNumberTypeMismatchThrows() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "limit", DashboardParamDefinition.TYPE_NUMBER, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def), Map.of("limit", "not-a-number")));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveNumberValid() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "limit", DashboardParamDefinition.TYPE_NUMBER, null, null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def), Map.of("limit", 42));
        assertEquals(42, ((Number) out.get("limit")).intValue());
    }

    @Test
    public void testResolveDateTypeMismatchThrows() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "saleDate", DashboardParamDefinition.TYPE_DATE, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def), Map.of("saleDate", "2024/13/45")));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveDateValid() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "saleDate", DashboardParamDefinition.TYPE_DATE, null, null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def), Map.of("saleDate", "2024-06-15"));
        assertEquals("2024-06-15", out.get("saleDate"));
    }

    @Test
    public void testResolveDateRangeInvalidDateThrows() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "dateRange", DashboardParamDefinition.TYPE_DATE_RANGE,
                Map.of("start", "2024-01-01", "end", "2024-12-31"), null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def),
                        Map.of("dateRange.start", "bogus", "dateRange.end", "2024-12-31")));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveNoDefaultAndNoInputOmitsParam() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "region", DashboardParamDefinition.TYPE_STRING, null, null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def), Map.of());
        assertFalse(out.containsKey("region"));
    }

    // ==================== date-range delimited 接受形态（D2-4，§11.4） ====================

    @Test
    public void testResolveDateRangeDelimitedStringAccepted() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE, null, null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def),
                Map.of("period", "2024-01-01,2024-06-30"));
        assertEquals("2024-01-01", out.get("period.start"), "delimited scalar split into flat start key");
        assertEquals("2024-06-30", out.get("period.end"), "delimited scalar split into flat end key");
        assertFalse(out.containsKey("period"), "output is always flat-key form (canonical)");
    }

    @Test
    public void testResolveDateRangeFlatKeysTakePrecedenceOverDelimitedScalar() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE, null, null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def),
                Map.of("period", "2024-01-01,2024-06-30",
                        "period.start", "2024-02-01",
                        "period.end", "2024-03-31"));
        assertEquals("2024-02-01", out.get("period.start"), "flat keys win when both forms provided");
        assertEquals("2024-03-31", out.get("period.end"));
    }

    @Test
    public void testResolveDateRangeDelimitedNoDelimiterThrows() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def), Map.of("period", "2024-01-01")));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveDateRangeDelimitedThreeSegmentsThrows() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def),
                        Map.of("period", "2024-01-01,2024-06-30,2024-12-31")));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveDateRangeDelimitedRelativeTokenRejected() {
        // §11.4 核实记录：relative 语义值（如 'today,today'）显式不支持
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def), Map.of("period", "today,today")));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveDateRangeDelimitedNonDatePartsThrows() {
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def), Map.of("period", "bogus,2024-12-31")));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveDateRangeArrayFormRejected() {
        // §11.4：数组形态显式拒绝（flux 发布的是字符串，不引入第二提交形态）
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> DashboardFilterResolver.resolve(List.of(def),
                        Map.of("period", List.of("2024-01-01", "2024-12-31"))));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveDateRangeDelimitedScalarIgnoredWhenOneFlatKeyPresent() {
        // 优先级细则：delimited 标量仅在两个扁平 key 均缺省时被消费
        DashboardParamDefinition def = new DashboardParamDefinition(
                "period", DashboardParamDefinition.TYPE_DATE_RANGE,
                Map.of("start", "2024-01-01", "end", "2024-12-31"), null, null);
        Map<String, Object> out = DashboardFilterResolver.resolve(List.of(def),
                Map.of("period", "2024-01-01,2024-06-30", "period.start", "2024-03-01"));
        assertEquals("2024-03-01", out.get("period.start"));
        assertEquals("2024-12-31", out.get("period.end"), "end falls back to default, scalar not consumed");
    }
}
