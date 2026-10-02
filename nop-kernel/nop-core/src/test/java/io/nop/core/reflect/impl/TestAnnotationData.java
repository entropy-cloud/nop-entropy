package io.nop.core.reflect.impl;

import io.nop.core.reflect.IAnnotationData;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAnnotationData {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface MyAnn {
        String value() default "default";

        int size() default 3;

        boolean enabled() default true;
    }

    @MyAnn(value = "hello", size = 5)
    public static class AnnHolder {
    }

    public static class PlainHolder {
    }

    @Test
    public void testFromAnnotationCapturesAllAttributes() {
        AnnotationData data = AnnotationData.fromAnnotation(AnnHolder.class.getAnnotation(MyAnn.class));
        assertNotNull(data);
        assertEquals(MyAnn.class.getCanonicalName(), data.getName());
        assertEquals(MyAnn.class.getCanonicalName(), data.key(), "key() should return the annotation name");

        assertEquals("hello", data.getProperty("value"));
        assertEquals(5, data.getProperty("size"));
        assertEquals(true, data.getProperty("enabled"), "attribute with default value should be captured");

        assertTrue(data.getPropertyNames().contains("value"));
        assertTrue(data.getPropertyNames().contains("size"));
        assertTrue(data.getPropertyNames().contains("enabled"));
        // fromAnnotation 同时会捕获 annotationType()/toString() 等非业务键，不断言精确 size
    }

    @Test
    public void testPropertiesAreUnmodifiable() {
        AnnotationData data = AnnotationData.fromAnnotation(AnnHolder.class.getAnnotation(MyAnn.class));
        Map<String, Object> props = data.getProperties();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> props.put("other", 1), "properties map should be unmodifiable");
    }

    @Test
    public void testFromAnnotationReturnsNullWhenAnnotationMissing() {
        assertNull(AnnotationData.fromAnnotation(PlainHolder.class, MyAnn.class));
        // annClass == null 时直接返回 null
        assertNull(AnnotationData.fromAnnotation(PlainHolder.class, null));
    }

    @Test
    public void testGetAnnotationValueExtractsValueAttribute() {
        Object v = AnnotationData.getAnnotationValue(AnnHolder.class, MyAnn.class);
        assertEquals("hello", v);

        // 注解不存在时返回 null
        assertNull(AnnotationData.getAnnotationValue(PlainHolder.class, MyAnn.class));
        // annClass == null 时返回 null
        assertNull(AnnotationData.getAnnotationValue(PlainHolder.class, null));

        // 直接从注解实例提取
        Object v2 = AnnotationData.getAnnotationValue(AnnHolder.class.getAnnotation(MyAnn.class));
        assertEquals("hello", v2);
    }

    @Test
    public void testManualConstructionAndNullProperties() {
        AnnotationData data = new AnnotationData("my-ann", null);
        assertEquals("my-ann", data.getName());
        assertNull(data.getProperties());
        assertEquals(0, data.getPropertyNames().size(), "null properties should yield empty property names");
        assertNull(data.getProperty("any"));
    }
}
