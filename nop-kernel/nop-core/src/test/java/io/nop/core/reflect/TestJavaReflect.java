/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaReflect {
    @Test
    public void testClassLoader() {
        try {
            ClassLoader classLoader = TestJavaReflect.class.getClassLoader();
            Package[] pkgs = classLoader.getDefinedPackages();
            System.out.println(StringHelper.joinArray(pkgs, "\n"));
        } catch (Exception e) {

        }
    }

    @Test
    public void testInterface() {
        assertTrue(Modifier.isAbstract(IFunctionModel.class.getModifiers()));
    }

    @Test
    public void testType() {
        Class clazz = MyClass.class;
        Class clazz2 = MyClass2.class;

        Type superType1 = clazz.getGenericSuperclass();
        Type superType = clazz2.getGenericSuperclass();
        System.out.println(superType);
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            Type type = method.getGenericReturnType();
            System.out.println(type);
        }

        methods = clazz2.getDeclaredMethods();
        for (Method method : methods) {
            Type type = method.getGenericReturnType();
            System.out.println(type);
        }
    }

    @Test
    public void testAnnotation() throws Exception {
        Method method = MyClass2.class.getDeclaredMethod("getValue");
        assertFalse(method.isAnnotationPresent(NoReflection.class));
        NoReflection ref = method.getAnnotation(NoReflection.class);
        assertNull(ref);

        Annotation[] anns = method.getAnnotations();
        for (Annotation ann : anns) {
            System.out.println(ann);
            assertTrue(ann instanceof NoReflection);
        }
    }

    @Test
    public void testTypeArg() throws Exception {
        Method method = MyClass2.class.getDeclaredMethod("getValue3");
        Type type = method.getGenericReturnType();
        // V extends Map
        assertTrue(type instanceof TypeVariable);
        TypeVariable tv = (TypeVariable) type;
        Type[] bounds = tv.getBounds();

        // Map<String,List>
        assertTrue(bounds[0] instanceof ParameterizedType);

        ParameterizedType pt = (ParameterizedType) bounds[0];
        assertEquals(String.class, pt.getActualTypeArguments()[0]);

        // List<? super String>
        assertTrue(pt.getActualTypeArguments()[1] instanceof ParameterizedType);
        ParameterizedType pt2 = (ParameterizedType) pt.getActualTypeArguments()[1];
        assertEquals(List.class, pt2.getRawType());

        Type elmType = pt2.getActualTypeArguments()[0];
        assertTrue(elmType instanceof WildcardType);

        // ? super String
        WildcardType wt = (WildcardType) elmType;
        Type[] lower = wt.getLowerBounds();
        Type[] upper = wt.getUpperBounds();
        assertEquals(1, lower.length);
        assertEquals(String.class, lower[0]);

        assertEquals(1, upper.length);
        assertEquals(Object.class, upper[0]);
    }

    @Test
    public void testVariable() throws Throwable {
        Method method = MyClass2.class.getMethod("getValue4", Map.class);
        Type retType = method.getGenericReturnType();
        assertTrue(retType instanceof ParameterizedType);

        ParameterizedType pt = (ParameterizedType) retType;
        Type argType0 = pt.getActualTypeArguments()[0];
        Type argType1 = pt.getActualTypeArguments()[1];
        assertTrue(argType0 instanceof TypeVariable);
        assertTrue(argType1 instanceof TypeVariable);

        TypeVariable tv0 = (TypeVariable) argType0;
        TypeVariable tv1 = (TypeVariable) argType1;
        assertEquals("S", tv0.getName());
        assertEquals("T", tv1.getName());

        ParameterizedType pt2 = (ParameterizedType) tv1.getBounds()[0];
        TypeVariable tv3 = (TypeVariable) pt2.getActualTypeArguments()[0];
        assertEquals("S", tv3.getName());
        assertEquals(tv0, tv3);
        assertTrue(tv0 == tv3);

        Type argType = method.getGenericParameterTypes()[0];
        assertTrue(argType == tv0);
    }

    @Test
    public void testGenericType() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(MyEntity2.class);
        IFunctionModel fun = classModel.getMethodsByName("getList").getUniqueMethod();
        assertEquals("java.util.List<java.lang.Integer>", fun.getReturnType().toString());
        IGenericType type = fun.getArgs().get(0).getType();
        assertEquals("java.lang.String", type.toString());
    }
}

class BaseEntity<A, B> {
    public List<A> getList(B input) {
        return Collections.emptyList();
    }
}

class MyEntity<A> extends BaseEntity<A, String> {

}

class MyEntity2 extends MyEntity<Integer> {
    // @Override
    // public List<Integer> getList(String input) {
    // return super.getList(input);
    // }
}

class MyClass<T> extends HashMap<String, T> {
    private T a;

    @NoReflection
    public T getValue() {
        return a;
    }

    public int intValue() {
        return 0;
    }
}

class MyClass2 extends MyClass<String> {
    public <V> V getValue2() {
        return null;
    }

    @Override
    public String getValue() {
        return null;
    }

    public <V extends Map<String, List<? super String>>> V getValue3() {
        return null;
    }

    public <S extends Map, T extends List<S>> Map<S, T> getValue4(S map) {
        return null;
    }
}