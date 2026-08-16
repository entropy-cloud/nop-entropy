package io.nop.record_mapping;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_VALUE_NOT_IN_DICT;

/**
 * nop-record-mapping 缺陷回归测试（plan 2253）
 * 每个测试先验证缺陷存在（修复前 FAIL），修复后转绿
 */
public class TestRecordMappingRegression extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDictFieldWithNullValue() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.DictFieldTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", null);

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        Assertions.assertNull(target.get("status"));
    }

    @Test
    public void testDictFieldWithEmptyStringValue() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.DictFieldTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", "");

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        Assertions.assertNull(target.get("status"));
    }

    @Test
    public void testDictFieldWithInvalidValue() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.DictFieldTest");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", "9");

        Map<String, Object> target = new LinkedHashMap<>();
        NopException e = Assertions.assertThrows(NopException.class,
                () -> mapping.map(source, target, new RecordMappingContext()));
        Assertions.assertEquals(ERR_RECORD_FIELD_VALUE_NOT_IN_DICT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testFlattenToWritesToTarget() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.FlattenToTest");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("c", "b1");
        item.put("d", "b2");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("listB", Arrays.asList(item));

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        // 展平结果写入 target，前缀取 from（listB）而非 name（listA）
        Assertions.assertEquals("b1", target.get("listB-1-a"));
        Assertions.assertEquals("b2", target.get("listB-1-b"));
        // source 不被修改
        Assertions.assertNull(source.get("listA-1-a"));
        Assertions.assertNull(source.get("listB-1-a"));
    }

    @Test
    public void testFlattenRoundTrip() {
        IRecordMapping mapping = RecordMappingManager.instance().getRecordMapping("test.demo.FlattenRoundTripTest");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items-1-c", "b1");
        source.put("items-1-d", "b2");
        source.put("items-2-c", "b3");
        source.put("items-2-d", "b4");

        Map<String, Object> target = new LinkedHashMap<>();
        mapping.map(source, target, new RecordMappingContext());

        // flattenFrom → flattenTo round-trip：目标上得到同构展平键（ItemPass_to_Test 为恒等映射 c→c, d→d）
        Assertions.assertEquals("b1", target.get("items-1-c"));
        Assertions.assertEquals("b2", target.get("items-1-d"));
        Assertions.assertEquals("b3", target.get("items-2-c"));
        Assertions.assertEquals("b4", target.get("items-2-d"));
        // source 不被修改
        Assertions.assertEquals("b1", source.get("items-1-c"));
        Assertions.assertNull(target.get("listA-1-a"));
    }
}