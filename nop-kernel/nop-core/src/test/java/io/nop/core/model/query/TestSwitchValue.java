package io.nop.core.model.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.util.IVariableScope;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSwitchValue {

    private IVariableScope mapScope(Map<String, Object> values) {
        return new IVariableScope() {
            @Override
            public Object getValueByPropPath(String propPath) {
                return values.get(propPath);
            }

            @Override
            public Object getValue(String name) {
                return values.get(name);
            }

            @Override
            public boolean containsValue(String name) {
                return values.containsKey(name);
            }

        };
    }

    @Test
    public void testNullWhenReturnsValue() {
        SwitchValue sv = new SwitchValue();
        sv.setValue("v1");
        assertEquals("v1", sv.eval(FilterBeanEvaluator.INSTANCE, mapScope(new HashMap<>())),
                "when == null should always match");
    }

    @Test
    public void testWhenFalseReturnsNull() {
        SwitchValue sv = new SwitchValue();
        sv.setWhen(FilterBeans.eq("x", 1));
        sv.setValue("v1");

        Map<String, Object> vars = new HashMap<>();
        vars.put("x", 2);
        assertNull(sv.eval(FilterBeanEvaluator.INSTANCE, mapScope(vars)),
                "when condition not matched should return null");

        vars.put("x", 1);
        assertEquals("v1", sv.eval(FilterBeanEvaluator.INSTANCE, mapScope(vars)));
    }

    @Test
    public void testCasesPickFirstMatching() {
        SwitchValue case1 = new SwitchValue();
        case1.setWhen(FilterBeans.gt("x", 10));
        case1.setValue("big");

        SwitchValue case2 = new SwitchValue();
        case2.setWhen(FilterBeans.gt("x", 5));
        case2.setValue("middle");

        SwitchValue root = new SwitchValue();
        root.setCases(Arrays.asList(case1, case2));

        Map<String, Object> vars = new HashMap<>();
        vars.put("x", 20);
        assertEquals("big", root.eval(FilterBeanEvaluator.INSTANCE, mapScope(vars)),
                "first matching case should win");

        vars.put("x", 7);
        assertEquals("middle", root.eval(FilterBeanEvaluator.INSTANCE, mapScope(vars)));

        vars.put("x", 1);
        assertNull(root.eval(FilterBeanEvaluator.INSTANCE, mapScope(vars)),
                "no matching case should return null");
    }

    @Test
    public void testGetUsesGlobalEvaluatorAndContextScope() {
        SwitchValue sv = new SwitchValue();
        sv.setValue(42);
        // when == null 时 get() 不依赖上下文变量
        assertEquals(42, sv.get());
    }

    @Test
    public void testNormalizeValueConvertsValueAndNestedCases() {
        SwitchValue child = new SwitchValue();
        child.setValue("30");

        SwitchValue root = new SwitchValue();
        root.setValue("10");
        root.setCases(Arrays.asList(child));

        root.normalizeValue(Integer.class);
        assertEquals(10, root.getValue(), "root value should be converted to Integer");
        assertEquals(30, child.getValue(), "nested case value should also be converted");

        // null 值不做转换
        SwitchValue nullSv = new SwitchValue();
        nullSv.normalizeValue(Integer.class);
        assertNull(nullSv.getValue());
    }

    @Test
    public void testIsDynamicAlwaysTrue() {
        assertTrue(new SwitchValue().isDynamic());
    }
}
