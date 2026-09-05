/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.core.initialize.CoreInitialization;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmEntityState;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 ORM-05：集合批量加载完成后，必须把"因集合加载而已经变为非proxy"的实体
 * 从待加载队列（entityPropLoadMap）中移除。修复前清理只针对entityLoadMap，
 * 而proxy实体实际登记在entityPropLoadMap中，导致已加载实体被冗余二次批量查询。
 */
public class TestBatchLoadQueueCleanup {

    private static OrmModel ormModel;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        ormModel = (OrmModel) DslModelHelper.loadDslModel(
                io.nop.core.resource.VirtualFileSystem.instance().getResource(
                        "/nop/test/orm/app.orm.xml"));
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testCollectionLoadRemovesLoadedEntitiesFromPendingQueue() {
        IEntityModel collegeModel = ormModel.getEntityModel("io.nop.app.SimsCollege");
        IEntityModel classModel = ormModel.getEntityModel("io.nop.app.SimsClass");
        IEntityRelationModel rel = collegeModel.getRelation("cachedClasses", true);

        AtomicInteger entityLoadCalls = new AtomicInteger();
        AtomicReference<DynamicOrmEntity> elementRef = new AtomicReference<>();

        IOrmSessionImplementor session = (IOrmSessionImplementor) Proxy.newProxyInstance(
                TestBatchLoadQueueCleanup.class.getClassLoader(),
                new Class[]{IOrmSessionImplementor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCollectionModel":
                            return rel;
                        case "getEntityModel":
                            return ormModel.getEntityModel((String) args[0]);
                        case "internalBatchLoadCollectionAsync":
                            // 模拟集合加载完成：集合内元素变为非proxy
                            if (elementRef.get() != null)
                                elementRef.get().orm_state(OrmEntityState.MANAGED);
                            return CompletableFuture.completedFuture(null);
                        case "internalBatchLoadAsync":
                            entityLoadCalls.incrementAndGet();
                            return CompletableFuture.completedFuture(null);
                        case "flushBatchLoadQueue":
                        case "internalClearDirty":
                        case "internalMarkDirty":
                        case "internalMarkExtDirty":
                        case "internalClearExtDirty":
                            return null;
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

        OrmBatchLoadQueueImpl queue = new OrmBatchLoadQueueImpl(session);

        // proxy集合 + 一个proxy元素，元素同时被登记为待加载实体
        DynamicOrmEntity owner = new DynamicOrmEntity(collegeModel);
        OrmEntitySet<IOrmEntity> coll = new OrmEntitySet<>(owner, "cachedClasses", null, null,
                DynamicOrmEntity.class);

        DynamicOrmEntity element = new DynamicOrmEntity(classModel);
        element.orm_state(OrmEntityState.PROXY);
        coll.orm_beginLoad();
        coll.orm_internalAdd(element);
        // 保持proxy待加载状态（真实场景中集合在加载前为proxy）
        coll.orm_proxy(true);
        elementRef.set(element);

        queue.enqueue(coll);
        queue.enqueue(element);

        // 集合加载完成后，已装配为非proxy的元素不应再触发实体批量加载
        queue.flush();

        assertTrue(element.orm_state() == OrmEntityState.MANAGED, "集合加载后元素应为MANAGED");
        assertEquals(0, entityLoadCalls.get(),
                "集合加载已装配的实体不应再被冗余二次批量加载");
    }
}
