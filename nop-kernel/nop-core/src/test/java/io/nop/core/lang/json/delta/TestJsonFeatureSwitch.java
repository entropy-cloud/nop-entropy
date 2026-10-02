package io.nop.core.lang.json.delta;

import io.nop.core.lang.eval.IPredicateEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestJsonFeatureSwitch {

    /**
     * 简单谓词求值器：表达式为 true/false 字面量，或 expr 前缀反转
     */
    private static IPredicateEvaluator constEvaluator(boolean value) {
        return (loc, source) -> value;
    }

    private static IPredicateEvaluator notEvaluator() {
        return (loc, source) -> !Boolean.parseBoolean(source.trim());
    }

    @Test
    public void testProcessScalarReturnsSame() {
        assertSame("abc", JsonFeatureSwitch.INSTANCE.process("abc", constEvaluator(true)));
    }

    @Test
    public void testFeatureOnRemovesMapWhenEvaluatorFalse() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("feature:on", "true");
        map.put("value", 1);

        // evaluator 返回 false -> feature:on 不满足 -> 整个 map 移除
        assertNull(JsonFeatureSwitch.INSTANCE.processMap(map, constEvaluator(false)),
                "feature:on not satisfied should remove the whole map");
    }

    @Test
    public void testFeatureOnKeepsMapWhenEvaluatorTrue() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("feature:on", "true");
        map.put("value", 1);

        Map<String, Object> result = JsonFeatureSwitch.INSTANCE.processMap(map, constEvaluator(true));
        assertNotNull(result);
        assertEquals(1, result.get("value"));
        // feature:on 属性本身应被移除
        assertNull(result.get("feature:on"), "feature:on attr should be stripped from result");
    }

    @Test
    public void testFeatureOffRemovesMapWhenEvaluatorTrue() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("feature:off", "true");
        map.put("value", 1);

        assertNull(JsonFeatureSwitch.INSTANCE.processMap(map, constEvaluator(true)),
                "feature:off satisfied should remove the whole map");

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("feature:off", "true");
        map2.put("value", 1);
        assertNotNull(JsonFeatureSwitch.INSTANCE.processMap(map2, constEvaluator(false)),
                "feature:off not satisfied should keep the map");
    }

    @Test
    public void testNestedMapPrunedRecursively() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("feature:on", "true");
        child.put("x", 1);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("child", child);
        root.put("keep", 2);

        // child 的 feature:on 求值为 false -> child 从 root 中删除
        Map<String, Object> result = JsonFeatureSwitch.INSTANCE.processMap(root, constEvaluator(false));
        assertNotNull(result);
        assertNull(result.get("child"), "pruned child should be removed from parent");
        assertEquals(2, result.get("keep"));
    }

    @Test
    public void testProcessListRemovesNullifiedEntries() {
        Map<String, Object> off = new LinkedHashMap<>();
        off.put("feature:on", "true");
        off.put("v", 1);

        Map<String, Object> on = new LinkedHashMap<>();
        on.put("v", 2);

        List<Object> list = new java.util.ArrayList<>(Arrays.asList(off, on));
        List<Object> result = (List<Object>) JsonFeatureSwitch.INSTANCE.process(list, constEvaluator(false));
        assertEquals(1, result.size(), "nullified entries should be removed from list");
        assertEquals(2, ((Map<?, ?>) result.get(0)).get("v"));
    }

    @Test
    public void testEvaluatorReceivesFeatureExpression() {
        // feature:on 的表达式原样传给 evaluator，如 "false" 取反后为 true -> 保留
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("feature:on", "false");
        map.put("value", 1);

        Map<String, Object> result = JsonFeatureSwitch.INSTANCE.processMap(map, notEvaluator());
        assertNotNull(result, "not(false)=true should keep the map");
    }

    @Test
    public void testEmptyFeatureAttrsAreIgnored() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", 1);
        Map<String, Object> result = JsonFeatureSwitch.INSTANCE.processMap(map, constEvaluator(false));
        assertNotNull(result, "no feature attrs -> map always kept");
        assertEquals(1, result.get("value"));
    }

    @Test
    public void testNonMapScalarProcessPassthrough() {
        assertEquals(Integer.valueOf(3), JsonFeatureSwitch.INSTANCE.process(3, constEvaluator(false)));
        assertNull(JsonFeatureSwitch.INSTANCE.process(null, constEvaluator(true)));
    }

    @Test
    public void testHashMapSubtypeHandled() {
        Map<String, Object> map = new HashMap<>();
        map.put("feature:on", "true");
        map.put("v", 1);
        // 非 JObject 的普通 map getLocation 返回 null 也不会出错
        assertNotNull(JsonFeatureSwitch.INSTANCE.processMap(map, constEvaluator(true)));
    }
}
