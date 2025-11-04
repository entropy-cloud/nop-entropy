/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core;

import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.annotations.meta.PropMetaAnnotation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReflect {
    @Test
    public void testAbstract() {
        assertTrue(Modifier.isAbstract(A1.class.getModifiers()));
        assertTrue(PropMeta.class.isAnnotation());
        assertTrue(!PropMetaAnnotation.class.isAnnotation());
    }

    @Test
    public void testInterfaces() throws Exception {
        Type[] ifs = C2.class.getGenericInterfaces();
        System.out.println(Arrays.asList(ifs));

        ifs = B2.class.getGenericInterfaces();
        System.out.println(Arrays.asList(ifs));

        ParameterizedType type = (ParameterizedType) ifs[1];
        Class clazz = (Class) type.getActualTypeArguments()[0];
        assertEquals(String.class, clazz);

        Method method = B2.class.getMethod("invoke");
        TypeVariable returnType = (TypeVariable) method.getGenericReturnType();
        Type[] bound = returnType.getBounds();
        System.out.println(returnType);
        assertEquals(Object.class, bound[0]);
    }

    @Test
    public void testRecursive() {
        Class clz = R.class;
        TypeVariable[] params = clz.getTypeParameters();
        ParameterizedType T = (ParameterizedType) params[0].getBounds()[0];
        assertEquals(clz, T.getRawType());
        Type var = T.getActualTypeArguments()[0];
        assertEquals(var, params[0]);
        assertTrue(T.getActualTypeArguments()[0] instanceof TypeVariable);
    }

    @Test
    public void testTypeVariable() {
        Class clz = R2.class;
        TypeVariable[] params = clz.getTypeParameters();
        TypeVariable V = (TypeVariable) params[0].getBounds()[0];
        assertEquals(V, params[1]);
    }

    @Test
    public void testMethodTypeVariable() throws Exception {
        Method method = A2.class.getMethod("invoke2");
        TypeVariable W = method.getTypeParameters()[0];
        assertEquals(W, method.getGenericReturnType());
    }

    @Test
    public void testFunctionalMethod() {
        Method mtd = null;
        for (Method method : MyFunction.class.getMethods()) {
            if (method.isSynthetic() || method.isDefault())
                continue;

            if ((method.getModifiers() & (Modifier.ABSTRACT | Modifier.STATIC))
                    == Modifier.ABSTRACT) {
                mtd = method;
                break;
            }
        }
        assertEquals("apply", mtd.getName());
    }

    @Test
    public void testArray() {
        Class[] ifs = String[].class.getInterfaces();
        assertEquals(2, ifs.length);
        assertEquals(Object.class, String[].class.getSuperclass());
        assertEquals(Object.class, String[].class.getGenericSuperclass());
    }
}

@FunctionalInterface
interface MyFunction extends Function<String, Object> {
    static MyFunction defaultFunc() {
        return a -> a;
    }
}

class R<T extends R<T, V>, V extends List<R<T, V>>> {

}

class V {

}

class R2<T extends V, V extends List<String>> {

}

interface A1<T> {

}

interface A2<T> extends A1<T> {
    default T invoke() {
        return null;
    }

    default <W extends List> W invoke2() {
        return null;
    }
}

interface B1 {

}

class B2 implements B1, A2<String> {

}

class C2 extends B2 implements A1<String> {

}