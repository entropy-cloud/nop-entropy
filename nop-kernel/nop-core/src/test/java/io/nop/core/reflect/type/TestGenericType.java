/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.type;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.impl.SafeRawTypeResolver;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.type.utils.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGenericType {
    @Test
    public void testPredefined() {
        IGenericType objectArray = PredefinedGenericTypes.ARRAY_ANY_TYPE;
        assertEquals("java.lang.Object[]", objectArray.toString());
        assertEquals("byte[]", PredefinedGenericTypes.ARRAY_PRIMITIVE_BYTE_TYPE.toString());
    }

    @Test
    public void testBoxType() {
        IGenericType intType = PredefinedGenericTypes.PRIMITIVE_INT_TYPE;
        assertTrue(intType.isAssignableFrom(Integer.class));
        assertTrue(intType.isAssignableTo(Integer.class));

        IGenericType boolType = PredefinedGenericTypes.PRIMITIVE_BOOLEAN_TYPE;
        assertTrue(boolType.isAssignableTo(Boolean.class));
        assertTrue(boolType.isAssignableFrom(Boolean.class));

        boolType = PredefinedGenericTypes.BOOLEAN_TYPE;
        assertTrue(boolType.isAssignableFrom(boolean.class));
        assertTrue(boolType.isAssignableTo(boolean.class));
    }

    @Test
    public void testCollectionType() {
        IGenericType type = ReflectionManager.instance().buildGenericType(new TypeReference<List<String>>() {
        }.getType());

        assertEquals(PredefinedGenericTypes.STRING_TYPE, type.getComponentType());
    }

    @Test
    public void testParse() {
        try {
            new GenericTypeParser().rawTypeResolver(SafeRawTypeResolver.INSTANCE).parseFromText(null, "a..b");
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void testParseFunctionType() {
        IFunctionType type = new GenericTypeParser().parseFunctionTypeFromText(null, "(event:any,ctx:any)=>boolean");
        assertEquals("(event:java.lang.Object,ctx:java.lang.Object)=>boolean", type.toString());
    }

    static class MyList<T, V> extends ArrayList<V> {

    }

    static class MyListExt<V> extends MyList<String, V> {

    }

    static class MyListExt2<V> extends MyList<V, String> {

    }

    static class MyList3 extends MyList<Integer, Float> {

    }

    @Test
    public void testComponentType() {
        IGenericType type = ReflectionManager.instance().buildGenericType(new TypeReference<MyList<Double, String>>() {
        }.getType());

        assertEquals(PredefinedGenericTypes.STRING_TYPE, type.getComponentType());

        type = ReflectionManager.instance().buildGenericType(new TypeReference<MyListExt2<Double>>() {
        }.getType());
        assertEquals(PredefinedGenericTypes.STRING_TYPE, type.getComponentType());

        type = ReflectionManager.instance().buildGenericType(new TypeReference<MyList3>() {
        }.getType());
        assertEquals(PredefinedGenericTypes.FLOAT_TYPE, type.getComponentType());
    }

    @Test
    public void testRefine() {
        IGenericType baseType = ReflectionManager.instance().buildGenericType(GraphQLRequestBean.class);
        IGenericType beanType = GenericTypeHelper.buildRequestType(baseType);

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(ApiRequest.class);

        IGenericType propType = beanModel.getPropertyModel("data").getType();
        IGenericType dataType = propType.refine(beanModel.getType(), beanType);
        assertEquals(GraphQLRequestBean.class, dataType.getRawClass());
    }

    @Test
    public void testResolveClass() {
        IGenericType type = new GenericTypeParser().parseFromText(null, "List<MyBean>");
        type.resolveClassName(typeName -> "my." + typeName);
        assertEquals("java.util.List<my.MyBean>", type.toString());
    }
}