/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRecordReflection {

    record Point(int x, int y) {
    }

    record NamedPoint(String name, int x, int y) {
    }

    record Empty() {
    }

    record Pair<A, B>(A first, B second) {
    }

    record AnnotatedPoint(@Description("x coord") int x, @Description("y coord") int y) {
    }

    record ValidatedRange(int min, int max) {
        public ValidatedRange {
            if (min > max)
                throw new IllegalArgumentException("min > max");
        }
    }

    @Test
    public void testBeanModelProperties() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(Point.class);
        assertNotNull(beanModel);

        Map<String, ? extends IBeanPropertyModel> props = beanModel.getPropertyModels();
        assertEquals(2, props.size());
        assertTrue(props.containsKey("x"));
        assertTrue(props.containsKey("y"));

        IBeanPropertyModel xProp = props.get("x");
        assertEquals("x", xProp.getName());
        assertEquals(int.class, xProp.getType().getRawClass());
        assertNotNull(xProp.getGetter());
        assertNull(xProp.getSetter());

        IBeanPropertyModel yProp = props.get("y");
        assertEquals("y", yProp.getName());
        assertEquals(int.class, yProp.getType().getRawClass());
        assertNotNull(yProp.getGetter());
        assertNull(yProp.getSetter());
    }

    @Test
    public void testInstantiation() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(Point.class);

        Object instance = beanModel.newInstance(new Object[]{10, 20});
        assertNotNull(instance);
        assertTrue(instance instanceof Point);
        Point point = (Point) instance;
        assertEquals(10, point.x());
        assertEquals(20, point.y());
    }

    @Test
    public void testPropertyGetter() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(Point.class);
        Point point = new Point(5, 15);

        IBeanPropertyModel xProp = beanModel.getPropertyModel("x");
        IBeanPropertyModel yProp = beanModel.getPropertyModel("y");

        assertEquals(5, xProp.getPropertyValue(point, null));
        assertEquals(15, yProp.getPropertyValue(point, null));
    }

    @Test
    public void testImmutableAndDataBean() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(Point.class);
        assertTrue(beanModel.isImmutable());
        assertTrue(beanModel.isDataBean());
    }

    @Test
    public void testJsonSerialization() {
        NamedPoint point = new NamedPoint("origin", 0, 0);
        String json = JsonTool.serialize(point, false);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"origin\""));
        assertTrue(json.contains("\"x\":0"));
        assertTrue(json.contains("\"y\":0"));
    }

    @Test
    public void testJsonDeserialization() {
        String json = "{\"name\":\"origin\",\"x\":0,\"y\":0}";
        Object result = JsonTool.parseBeanFromText(json, NamedPoint.class);
        assertNotNull(result);
        assertTrue(result instanceof NamedPoint);
        NamedPoint point = (NamedPoint) result;
        assertEquals("origin", point.name());
        assertEquals(0, point.x());
        assertEquals(0, point.y());
    }

    @Test
    public void testJsonRoundTrip() {
        NamedPoint original = new NamedPoint("test", 100, 200);
        String json = JsonTool.serialize(original, false);
        NamedPoint restored = (NamedPoint) JsonTool.parseBeanFromText(json, NamedPoint.class);
        assertEquals(original, restored);
    }

    @Test
    public void testAnnotationPropagation() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(AnnotatedPoint.class);
        IBeanPropertyModel xProp = beanModel.getPropertyModel("x");
        assertNotNull(xProp);

        Description xDesc = xProp.getAnnotation(Description.class);
        assertNotNull(xDesc);
        assertEquals("x coord", xDesc.value());

        IBeanPropertyModel yProp = beanModel.getPropertyModel("y");
        Description yDesc = yProp.getAnnotation(Description.class);
        assertNotNull(yDesc);
        assertEquals("y coord", yDesc.value());
    }

    @Test
    public void testGenericRecord() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(Pair.class);
        Map<String, ? extends IBeanPropertyModel> props = beanModel.getPropertyModels();
        assertEquals(2, props.size());

        IBeanPropertyModel firstProp = props.get("first");
        assertNotNull(firstProp);
        assertEquals("first", firstProp.getName());

        IBeanPropertyModel secondProp = props.get("second");
        assertNotNull(secondProp);
        assertEquals("second", secondProp.getName());

        IGenericType type = ReflectionManager.instance().buildGenericType(
                new TypeReference<Pair<String, Integer>>() {}.getType());
        IBeanModel typedBeanModel = ReflectionManager.instance().getBeanModelForType(type);
        IBeanPropertyModel firstTyped = typedBeanModel.getPropertyModel("first");
        assertEquals(Object.class, firstTyped.getType().getRawClass());
        IBeanPropertyModel secondTyped = typedBeanModel.getPropertyModel("second");
        assertEquals(Object.class, secondTyped.getType().getRawClass());

        Pair<String, Integer> pair = new Pair<>("hello", 42);
        assertEquals("hello", firstTyped.getPropertyValue(pair, null));
        assertEquals(42, secondTyped.getPropertyValue(pair, null));
    }

    @Test
    public void testEmptyRecord() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(Empty.class);
        assertNotNull(beanModel);
        assertEquals(0, beanModel.getPropertyModels().size());
        assertTrue(beanModel.isImmutable());
        assertTrue(beanModel.isDataBean());

        Object instance = beanModel.newInstance(new Object[0]);
        assertNotNull(instance);
        assertTrue(instance instanceof Empty);
    }

    @Test
    public void testCompactConstructor() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(ValidatedRange.class);
        Object instance = beanModel.newInstance(new Object[]{1, 10});
        assertNotNull(instance);
        ValidatedRange range = (ValidatedRange) instance;
        assertEquals(1, range.min());
        assertEquals(10, range.max());

        boolean caught = false;
        try {
            beanModel.newInstance(new Object[]{10, 1});
        } catch (Exception e) {
            caught = true;
        }
        assertTrue(caught, "Compact constructor should reject min > max");
    }

    @Test
    public void testConverterForJavaType() {
        ITypeConverter converter = ReflectionManager.instance().getConverterForJavaType(Point.class);
        assertNotNull(converter);
    }
}
