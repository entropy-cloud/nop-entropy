package io.nop.record_mapping.impl;

import io.nop.core.reflect.bean.BeanTool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestFlattenListProcessor {

    @Test
    public void testParseFromFlattenObj() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试对象
        Map<String, Object> obj = new HashMap<>();
        obj.put("name", "test");

        // 添加扁平化列表属性
        obj.put("items-1-id", 1);
        obj.put("items-1-name", "item1");
        obj.put("items-2-id", 2);
        obj.put("items-2-name", "item2");
        obj.put("items-3-id", 3);
        obj.put("items-3-name", "item3");

        // 解析扁平化列表
        List<Map<String, Object>> result = processor.parseFromFlattenObj(obj, "items", false);

        // 验证结果
        assertEquals(3, result.size());

        Map<String, Object> item1 = result.get(0);
        assertEquals(1, item1.get("id"));
        assertEquals("item1", item1.get("name"));

        Map<String, Object> item2 = result.get(1);
        assertEquals(2, item2.get("id"));
        assertEquals("item2", item2.get("name"));

        Map<String, Object> item3 = result.get(2);
        assertEquals(3, item3.get("id"));
        assertEquals("item3", item3.get("name"));
    }

    @Test
    public void testParseFromFlattenObjWithDisableToPropPath() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试对象
        Map<String, Object> obj = new HashMap<>();
        obj.put("name", "test");

        // 添加扁平化列表属性
        obj.put("items-1-id", 1);
        obj.put("items-1-name", "item1");
        obj.put("items-2-id", 2);
        obj.put("items-2-name", "item2");

        // 解析扁平化列表（禁用属性路径）
        List<Map<String, Object>> result = processor.parseFromFlattenObj(obj, "items", true);

        // 验证结果
        assertEquals(2, result.size());

        Map<String, Object> item1 = result.get(0);
        assertEquals(1, item1.get("id"));
        assertEquals("item1", item1.get("name"));
    }

    @Test
    public void testParseFromFlattenObjWithComplexProperties() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试对象
        Map<String, Object> obj = new HashMap<>();

        // 添加复杂属性路径
        obj.put("items-1-user.name", "张三");
        obj.put("items-1-user.age", 25);
        obj.put("items-2-user.name", "李四");
        obj.put("items-2-user.age", 30);

        // 解析扁平化列表
        List<Map<String, Object>> result = processor.parseFromFlattenObj(obj, "items", false);

        // 验证结果
        assertEquals(2, result.size());

        Map<String, Object> item1 = result.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> user1 = (Map<String, Object>) item1.get("user");
        assertEquals("张三", user1.get("name"));
        assertEquals(25, user1.get("age"));

        Map<String, Object> item2 = result.get(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> user2 = (Map<String, Object>) item2.get("user");
        assertEquals("李四", user2.get("name"));
        assertEquals(30, user2.get("age"));
    }

    @Test
    public void testGenerateFlattenObj() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试数据
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("name", "item1");
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2);
        item2.put("name", "item2");
        items.add(item2);

        // 生成扁平化对象
        Map<String, Object> result = new HashMap<>();
        processor.generateFlattenObj(result, items, "items", false, item -> (Map<String, Object>) item);

        // 验证结果
        assertEquals(1, result.get("items-1-id"));
        assertEquals("item1", result.get("items-1-name"));
        assertEquals(2, result.get("items-2-id"));
        assertEquals("item2", result.get("items-2-name"));
    }

    @Test
    public void testGenerateFlattenObjWithDisableToPropPath() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试数据
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("name", "item1");
        items.add(item1);

        // 生成扁平化对象（禁用属性路径）
        Map<String, Object> result = new HashMap<>();
        processor.generateFlattenObj(result, items, "items", true, item -> (Map<String, Object>) item);

        // 验证结果
        assertEquals(1, result.get("items-1-id"));
        assertEquals("item1", result.get("items-1-name"));
    }

    @Test
    public void testGenerateFlattenObjWithComplexProperties() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试数据
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put("name", "张三");
        user1.put("age", 25);
        item1.put("user", user1);
        items.add(item1);

        // 生成扁平化对象
        Map<String, Object> result = new HashMap<>();
        processor.generateFlattenObj(result, items, "items", false, item -> (Map<String, Object>) item);

        // 验证结果
        assertEquals("张三", BeanTool.getComplexProperty(result,"items-1-user.name"));
        assertEquals(25, BeanTool.getComplexProperty(result,"items-1-user.age"));
    }

    @Test
    public void testGenerateFlattenObjWithEmptyList() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 空列表
        List<Map<String, Object>> items = new ArrayList<>();

        // 生成扁平化对象
        Map<String, Object> result = new HashMap<>();
        result.put("existing", "value");
        processor.generateFlattenObj(result, items, "items", false, item -> (Map<String, Object>) item);

        // 验证结果（空列表不应修改原对象）
        assertEquals("value", result.get("existing"));
        assertNull(result.get("items-1-id"));
    }

    @Test
    public void testGenerateFlattenObjWithNullList() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // null列表
        List<Map<String, Object>> items = null;

        // 生成扁平化对象
        Map<String, Object> result = new HashMap<>();
        result.put("existing", "value");
        processor.generateFlattenObj(result, items, "items", false, item -> (Map<String, Object>) item);

        // 验证结果（null列表不应修改原对象）
        assertEquals("value", result.get("existing"));
        assertNull(result.get("items-1-id"));
    }

    @Test
    public void testGenerateFlattenObjWithCustomItemValuesGetter() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试数据
        List<String> items = Arrays.asList("item1", "item2");

        // 自定义值获取器
        Function<Object, Map<String, Object>> itemValuesGetter = item -> {
            Map<String, Object> values = new HashMap<>();
            values.put("value", item);
            values.put("length", ((String) item).length());
            return values;
        };

        // 生成扁平化对象
        Map<String, Object> result = new HashMap<>();
        processor.generateFlattenObj(result, items, "items", false, itemValuesGetter);

        // 验证结果
        assertEquals("item1", result.get("items-1-value"));
        assertEquals(5, result.get("items-1-length"));
        assertEquals("item2", result.get("items-2-value"));
        assertEquals(5, result.get("items-2-length"));
    }

    @Test
    public void testParseFromFlattenObjWithGapInIndex() {
        FlattenListProcessor processor = FlattenListProcessor.instance();

        // 创建测试对象（索引不连续）
        Map<String, Object> obj = new HashMap<>();
        obj.put("items-1-id", 1);
        obj.put("items-3-id", 3);
        obj.put("items-5-id", 5);

        // 解析扁平化列表
        List<Map<String, Object>> result = processor.parseFromFlattenObj(obj, "items", false);

        // 验证结果（应该按索引顺序排列）
        assertEquals(3, result.size());
        assertEquals(1, result.get(0).get("id"));
        assertEquals(3, result.get(1).get("id"));
        assertEquals(5, result.get(2).get("id"));
    }
}
