/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm;

import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.biz.IBizObjectQueryProcessorBuilder;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.xlang.xmeta.IObjPropMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * graphql:queryMethod配在非关联属性且无graphql:filter时的fail-fast质量：
 * 抛带objType/propName上下文的NopException（nop.err.biz.connection-filter-required），
 * 而非裸IllegalArgumentException("filter")（构造器Guard.notNull，无对象/属性信息难定位）。
 */
public class TestOrmFetcherBuilderConnectionFilter {

    /**
     * 构造queryMethod=findPage、无graphql:filter、bizObjName=this的propMeta代理。
     */
    private static IObjPropMeta propMeta(String propName) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            switch (method.getName()) {
                case "prop_get":
                    if ("graphql:queryMethod".equals(args[0]))
                        return "findPage";
                    return null;
                case "getName":
                    return propName;
                case "getBizObjName":
                case "getItemBizObjName":
                    return "THIS_OBJ";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (IObjPropMeta) Proxy.newProxyInstance(IObjPropMeta.class.getClassLoader(),
                new Class<?>[]{IObjPropMeta.class}, handler);
    }

    private static IEntityModel entityModel(String propName, IEntityPropModel propModel) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if ("getProp".equals(method.getName())) {
                // ignoreUnknown=true时未知属性返回null
                return propModel;
            }
            if ("getName".equals(method.getName()))
                return "TestEntity";
            return defaultValue(method.getReturnType());
        };
        return (IEntityModel) Proxy.newProxyInstance(IEntityModel.class.getClassLoader(),
                new Class<?>[]{IEntityModel.class}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class)
            return null;
        if (returnType == boolean.class)
            return false;
        if (returnType == char.class)
            return '\0';
        if (returnType == float.class)
            return 0f;
        if (returnType == double.class)
            return 0d;
        if (returnType == long.class)
            return 0L;
        return Integer.valueOf(0);
    }

    private static OrmFetcherBuilder builder() {
        IBizObjectQueryProcessorBuilder processorBuilder = new IBizObjectQueryProcessorBuilder() {
            @Override
            public <T> io.nop.graphql.core.biz.IBizObjectQueryProcessor<T> buildQueryProcessor(String bizObjName) {
                return null;
            }
        };
        return new OrmFetcherBuilder(null, null, processorBuilder);
    }

    @Test
    public void testQueryMethodOnNonRelationPropWithoutFilterThrowsContextualError() {
        OrmFetcherBuilder fetcherBuilder = builder();
        // 属性在实体上不存在（ignoreUnknown=true → getProp返回null → propModel为null）
        IEntityModel entityModel = entityModel("badProp", null);

        NopException err = assertThrows(NopException.class,
                () -> fetcherBuilder.getConnectionFetcher(entityModel, "TestObj", propMeta("badProp")));
        assertEquals("nop.err.biz.connection-filter-required", err.getErrorCode());
        assertEquals("TestObj", err.getParam("bizObjName"));
        assertEquals("badProp", err.getParam("propName"));
    }

    @Test
    public void testQueryMethodOnNonRelationColumnPropThrowsContextualError() {
        OrmFetcherBuilder fetcherBuilder = builder();
        // 属性存在但不是关联属性：propModel被置null，同样推导不出关联过滤条件
        IEntityPropModel columnProp = (IEntityPropModel) Proxy.newProxyInstance(
                IEntityPropModel.class.getClassLoader(), new Class<?>[]{IEntityPropModel.class},
                (Object proxy, Method method, Object[] args) -> {
                    if ("getName".equals(method.getName()))
                        return "name";
                    if ("isRelationModel".equals(method.getName()))
                        return false;
                    return defaultValue(method.getReturnType());
                });
        IEntityModel entityModel = entityModel("name", columnProp);

        NopException err = assertThrows(NopException.class,
                () -> fetcherBuilder.getConnectionFetcher(entityModel, "TestObj", propMeta("name")));
        assertEquals("nop.err.biz.connection-filter-required", err.getErrorCode());
        assertTrue(err.getParam("propName").equals("name"));
    }
}
