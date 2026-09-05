/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.core.initialize.CoreInitialization;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmEntityState;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 ORM-02：newRevEntity在orm_clearDirty()之后必须将revEnd的关闭修改标记为dirty，
 * 否则queueUpdate(entity)生成的dirtyPropIds为空，buildUpdateSql返回null，
 * 批执行时旧修订记录的关闭UPDATE被整体跳过（回调链也不执行），同一实体出现两条revEnd=MAX的"当前版本"。
 */
public class TestOrmRevisionCloseUpdate {

    private static OrmModel ormModel;
    private static final long SESSION_REV = 100L;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        ormModel = (OrmModel) DslModelHelper.loadDslModel(
                io.nop.core.resource.VirtualFileSystem.instance().getResource(
                        "/nop/test/orm/test-collection-filter.orm.xml"));
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IOrmSessionImplementor stubSession() {
        return (IOrmSessionImplementor) Proxy.newProxyInstance(
                TestOrmRevisionCloseUpdate.class.getClassLoader(),
                new Class[]{IOrmSessionImplementor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSessionRevVersion":
                            return SESSION_REV;
                        case "toString":
                            return "stub-session";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            break;
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class)
                        return false;
                    if (rt == int.class)
                        return 0;
                    if (rt == long.class)
                        return 0L;
                    return null;
                });
    }

    @Test
    public void testNewRevEntityMarksRevEndDirty() {
        IEntityModel entityModel = ormModel.getEntityModel("test.entity.TestRevisionEntity");
        int endVerPropId = entityModel.getNopRevEndVarPropId();
        int beginVerPropId = entityModel.getNopRevBeginVerPropId();
        assertTrue(endVerPropId > 0 && beginVerPropId > 0, "useRevision实体应自动补充revBegin/revEnd内部列");

        DynamicOrmEntity entity = new DynamicOrmEntity(entityModel);
        // 模拟已存在的当前修订记录：revBegin=200(>sessionRev)、revEnd=MAX
        entity.orm_internalSet(entityModel.getColumn("sid", true).getPropId(), 1L);
        entity.orm_internalSet(beginVerPropId, 200L);
        entity.orm_internalSet(endVerPropId, OrmConstants.NOP_VER_MAX_VALUE);
        entity.orm_state(OrmEntityState.MANAGED);

        IOrmEntity revEntity = OrmRevisionHelper.newRevEntity(OrmConstants.REV_TYPE_UPDATE, entityModel, entity,
                stubSession());

        assertTrue(entity.orm_propDirty(endVerPropId),
                "revEnd的关闭修改必须标记为dirty，否则旧修订记录的关闭UPDATE不会生成");
        assertEquals(SESSION_REV, entity.orm_propValue(endVerPropId));
        assertEquals(OrmConstants.NOP_VER_MAX_VALUE, revEntity.orm_propValue(endVerPropId),
                "新修订记录的revEnd应保持MAX");
    }
}
