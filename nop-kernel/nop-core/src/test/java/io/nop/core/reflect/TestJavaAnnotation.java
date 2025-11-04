/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.annotations.meta.ObjMetaAnnotation;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaAnnotation {
    interface MyInterface {
        @NoReflection
        int myFn2();
    }

    @Description("ssss")
    static class MyClass implements MyInterface {
        @BizLoader("aaa")
        public int myFn() {
            return 0;
        }

        public int myFn2() {
            return 3;
        }

        @BizLoader("bbb")
        @NoReflection
        public int myFn3() {
            return 4;
        }
    }

    @Description("yyy")
    static class MyClassExt extends MyClass {
        @BizLoader("bbb")
        @Override
        public int myFn() {
            return 0;
        }

        public int myFn3() {
            return 4;
        }
    }

    @Description("base")
    @NoReflection
    static class BaseClass {

    }

    @NoReflection
    static class ExtClass {

    }

    @Test
    public void testGetAnnotation() throws Exception {
        Annotation[] anns = MyClassExt.class.getAnnotations();
        assertEquals(1, anns.length);
        assertEquals("yyy", ((Description) anns[0]).value());

        Method method = MyClassExt.class.getMethod("myFn", new Class[0]);
        anns = method.getAnnotations();
        assertEquals(1, anns.length);
        assertEquals("bbb", ((BizLoader) anns[0]).value());

    }

    @Test
    public void testInheritableAnnotationOnClass() throws Exception {
        Annotation[] anns = ExtClass.class.getAnnotations();
        assertEquals(1, anns.length);
        assertTrue(anns[0] instanceof NoReflection);

        assertTrue(ExtClass.class.isAnnotationPresent(NoReflection.class));
        assertNotNull(ExtClass.class.getAnnotation(NoReflection.class));
    }

    // 不会自动继承基类方法上的注解
    @Test
    public void testInheritableAnnotationOnMethod() throws Exception {
        Annotation[] anns;

        Method method = MyClassExt.class.getMethod("myFn2", new Class[0]);
        anns = method.getAnnotations();
        assertEquals(0, anns.length);

        method = MyClassExt.class.getMethod("myFn3", new Class[0]);
        anns = method.getAnnotations();
        assertEquals(0, anns.length);

        assertNull(method.getAnnotation(BizLoader.class));
        assertEquals(0, method.getAnnotationsByType(BizLoader.class).length);
    }

    @Test
    public void testAnnotationImpl() {
        ObjMetaAnnotation impl = new ObjMetaAnnotation();
        impl.setDescription("sss");
        assertTrue(impl instanceof Annotation);
        assertEquals("sss", impl.description());
    }
}