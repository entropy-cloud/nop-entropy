/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.orm.support;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 回归覆盖 wi7#1（plan 2306 项 35）：detached / transient 实体访问 to-one ref 属性时，
 * 未显式设置的 ref 按"未加载"（null）处理，不得抛 session-not-attached——
 * detached 元数据遍历（如 DynEntityMetaToOrmModel.toColumnModel 访问 getDomain/getModule）
 * 依赖此语义。
 */
public class TestDetachedRefAccess {

    @Test
    public void testUnsetRefReturnsNullWithoutEnhancer() {
        DynamicOrmEntity entity = new DynamicOrmEntity();
        // 未 attach enhancer、ref 从未显式设置：internalGetRefEntity 必须返回 null，
        // 修复前经 requireEnhancer() 抛 ERR_ORM_SESSION_CLOSED / ERR_ORM_ENTITY_NOT_ATTACHED
        assertNull(entity.internalGetRefEntity("module"));
    }

    @Test
    public void testExplicitlySetRefStillReturnedWhenDetached() {
        DynamicOrmEntity entity = new DynamicOrmEntity();
        DynamicOrmEntity ref = new DynamicOrmEntity(stubEntityModel());
        entity.internalSetRefEntity("module", ref, () -> {
        });

        // 显式设置过的 ref 在 detached 状态下仍可读取
        assertSame(ref, entity.internalGetRefEntity("module"));
    }

    private static IEntityModel stubEntityModel() {
        // getIdProp 返回单列主键桩（internalSetRefEntity 的 orm_hasId 路径需要）
        IEntityPropModel idProp = (IEntityPropModel) java.lang.reflect.Proxy.newProxyInstance(
                TestDetachedRefAccess.class.getClassLoader(),
                new Class[]{IEntityPropModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isSingleColumn":
                            return true;
                        case "getColumnPropId":
                            return 1;
                        case "getColumnPropIds":
                            return new int[]{1};
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class)
                                return false;
                            if (rt.isPrimitive())
                                return 0;
                            return null;
                    }
                });
        return (IEntityModel) java.lang.reflect.Proxy.newProxyInstance(
                TestDetachedRefAccess.class.getClassLoader(),
                new Class[]{IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getIdProp":
                            return idProp;
                        case "getPropIdBound":
                            return 2;
                        case "getName":
                            return "StubEntity";
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class)
                                return false;
                            if (rt.isPrimitive())
                                return 0;
                            return null;
                    }
                });
    }
}
