package io.nop.core.model.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFilterBeanEvaluator {

    public static class Person {
        private String name = "n1";
        private int age = 20;
        private int salary = 100;

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public int getSalary() {
            return salary;
        }
    }

    private static IVariableScope scope() {
        return BeanVariableScope.makeScope(new Person());
    }

    @Test
    public void testCompareOpOnBeanProperty() {
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();

        assertTrue(evaluator.visitRoot(FilterBeans.eq("age", 20), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.eq("age", 21), scope()));
        assertTrue(evaluator.visitRoot(FilterBeans.gt("age", 18), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.lt("age", 18), scope()));
        assertTrue(evaluator.visitRoot(FilterBeans.ne("name", "other"), scope()));
    }

    @Test
    public void testValueNameIndirection() {
        // valueName 引用 scope 中的另一个属性作为比较值
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        TreeBean filter = FilterBeans.propEq("age", "salary");
        assertFalse(evaluator.visitRoot(filter, scope()), "age(20) != salary(100)");

        assertTrue(evaluator.visitRoot(FilterBeans.propLt("age", "salary"), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.propGt("age", "salary"), scope()));
    }

    @Test
    public void testBetweenOpWithAttrs() {
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();

        assertTrue(evaluator.visitRoot(FilterBeans.between("age", 10, 30), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.between("age", 21, 30), scope()));
        // excludeMin/excludeMax 属性生效
        TreeBean excludeMin = FilterBeans.between("age", 20, 30);
        excludeMin.setAttr("excludeMin", true);
        assertFalse(evaluator.visitRoot(excludeMin, scope()), "excludeMin=true should reject boundary value");
    }

    @Test
    public void testBetweenOpWithMinNameMaxName() {
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        // min/max 通过 valueName 从 scope 取值：salary(100) 之间取 age... 构造 age<=x<=salary
        TreeBean filter = new TreeBean();
        filter.setTagName("between");
        filter.setAttr("name", "age");
        filter.setAttr("minName", "age");
        filter.setAttr("maxName", "salary");
        assertTrue(evaluator.visitRoot(filter, scope()));

        filter.setAttr("minName", "salary");
        filter.setAttr("maxName", "salary");
        assertFalse(evaluator.visitRoot(filter, scope()));
    }

    @Test
    public void testAssertOp() {
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        assertTrue(evaluator.visitRoot(FilterBeans.notNull("name"), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.isNull("name"), scope()));
        assertTrue(evaluator.visitRoot(FilterBeans.notEmpty("name"), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.isBlank("name"), scope()));
    }

    @Test
    public void testUnknownPropertyThrows() {
        // BeanVariableScope 访问 bean 上不存在的属性应报错而不是返回 null
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> evaluator.visitRoot(FilterBeans.isNull("missingProp"), scope()));
    }

    @Test
    public void testAndOrNotGroups() {
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();

        TreeBean and = FilterBeans.and(FilterBeans.eq("age", 20), FilterBeans.eq("name", "n1"));
        assertTrue(evaluator.visitRoot(and, scope()));

        TreeBean andFail = FilterBeans.and(FilterBeans.eq("age", 20), FilterBeans.eq("name", "other"));
        assertFalse(evaluator.visitRoot(andFail, scope()));

        TreeBean or = FilterBeans.or(FilterBeans.eq("age", 99), FilterBeans.eq("name", "n1"));
        assertTrue(evaluator.visitRoot(or, scope()));

        TreeBean orFail = FilterBeans.or(FilterBeans.eq("age", 99), FilterBeans.eq("name", "other"));
        assertFalse(evaluator.visitRoot(orFail, scope()));

        TreeBean not = FilterBeans.not(FilterBeans.eq("age", 99));
        assertTrue(evaluator.visitRoot(not, scope()));

        TreeBean notMatch = FilterBeans.not(FilterBeans.eq("age", 20));
        assertFalse(evaluator.visitRoot(notMatch, scope()));
    }

    @Test
    public void testEmptyGroupSemantics() {
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        // FilterBeans.and() 空参返回 alwaysTrue bean，or() 空参返回 alwaysFalse bean
        assertTrue(evaluator.visitRoot(FilterBeans.and(), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.or(), scope()));

        // evaluator 自身对空 children 的 and/or 组都返回 true
        TreeBean rawAnd = new TreeBean();
        rawAnd.setTagName("and");
        assertTrue(evaluator.visitRoot(rawAnd, scope()));
        TreeBean rawOr = new TreeBean();
        rawOr.setTagName("or");
        assertTrue(evaluator.visitRoot(rawOr, scope()));
    }

    @Test
    public void testAlwaysTrueAndFalse() {
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        assertTrue(evaluator.visitRoot(FilterBeans.alwaysTrue(), scope()));
        assertFalse(evaluator.visitRoot(FilterBeans.alwaysFalse(), scope()));
    }

    @Test
    public void testNullFilterYieldsNull() {
        // 基类 visitNullFilter 默认返回 null（由调用方决定 null 是否视为通过）
        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        assertNull(evaluator.visitRoot(null, scope()));
    }

    @Test
    public void testMapScope() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "ACTIVE");
        IVariableScope mapScope = new IVariableScope() {
            @Override
            public Object getValueByPropPath(String propPath) {
                return map.get(propPath);
            }

            @Override
            public Object getValue(String name) {
                return map.get(name);
            }

            @Override
            public boolean containsValue(String name) {
                return map.containsKey(name);
            }

        };

        FilterBeanEvaluator evaluator = new FilterBeanEvaluator();
        assertTrue(evaluator.visitRoot(FilterBeans.eq("status", "ACTIVE"), mapScope));
        assertFalse(evaluator.visitRoot(FilterBeans.eq("status", "INACTIVE"), mapScope));
    }
}
