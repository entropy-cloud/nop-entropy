/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.OrmErrors;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.impl.ProxyStub;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.session.IOrmSessionImplementor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-004（plan 2553 裁定 H）钉：两阶段拓扑 future 未完成 → fail-fast（不静默消费）。
 * 结构钉+行为钉。
 *
 * <p>路径：单元级 ProxyStub（沿 TestLoadedOrmModelCloseRace 先例，nop-orm 测试域无
 * mockito）。flushAsync 对阶段 future 的契约是同步完成假说：阶段1/阶段2 任一
 * batchExecuteAsync 返回未完成 future 即抛 OrmException(ERR_ORM_BATCH_FLUSH_ABORTED)，
 * 参数 entityName/phase 指明违约点。异常自 flushAsync 同步抛出（throw 位于 waitAll
 * 之前，不经返回 future 传播），断言可观察面为同步抛出 + 错误码 + 参数。</p>
 */
public class TestBatchActionPhaseContract {

    private static final String ENTITY_NAME = "io.nop.orm.persister.TestPhaseEntity";

    /**
     * 构造最小队列：session -> loadedModel -> topo(entityModel) -> persister。
     * persister.batchExecuteAsync 按阶段返回给定 future。
     */
    private static BatchActionQueueImpl newQueue(CompletableFuture<Void> phase1Future,
                                                 CompletableFuture<Void> phase2Future) {
        IBatchAction.EntitySaveAction action = new IBatchAction.EntitySaveAction(
                ProxyStub.of(io.nop.orm.IOrmEntity.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "orm_entityName":
                            return ENTITY_NAME;
                        case "get_id":
                            return "1";
                        default:
                            return null;
                    }
                }), null, (ret, err) -> {
                });

        IEntityPersister persister = ProxyStub.of(IEntityPersister.class, (proxy, method, args) -> {
            if ("batchExecuteAsync".equals(method.getName())) {
                boolean topoAsc = (Boolean) args[0];
                return topoAsc ? phase1Future : phase2Future;
            }
            return null;
        });

        IEntityModel entityModel = ProxyStub.of(IEntityModel.class,
                (proxy, method, args) -> "getName".equals(method.getName()) ? ENTITY_NAME : null);

        ILoadedOrmModel model = ProxyStub.of(ILoadedOrmModel.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getEntityModelsInTopoOrder":
                    return List.of(entityModel);
                case "requireEntityPersister":
                    return persister;
                default:
                    return null;
            }
        });

        IOrmSessionImplementor session = ProxyStub.of(IOrmSessionImplementor.class,
                (proxy, method, args) -> "getLoadedOrmModel".equals(method.getName()) ? model : null);

        BatchActionQueueImpl queue = new BatchActionQueueImpl(null, session);
        // topo 遍历按 capturedActions 键取 holder——必须先入队动作，否则 holder 为 null
        queue.enqueueSave(action);
        return queue;
    }

    private static void assertAborted(OrmException e, int expectedPhase) {
        assertEquals(OrmErrors.ERR_ORM_BATCH_FLUSH_ABORTED.getErrorCode(), e.getErrorCode(),
                "必须抛 ERR_ORM_BATCH_FLUSH_ABORTED（缺陷态静默消费未完成 future -> 红）");
        Map<String, Object> params = e.getParams();
        assertEquals(ENTITY_NAME, params.get("entityName"), "entityName 参数必须指明违约实体");
        assertEquals(Integer.valueOf(expectedPhase), params.get("phase"), "phase 参数必须指明违约阶段");
    }

    @Test
    public void testPhase1FutureNotDoneAborts() {
        BatchActionQueueImpl queue = newQueue(new CompletableFuture<>(), CompletableFuture.completedFuture(null));

        // throw 位于 flushAsync 方法体内（waitAll 之前），同步向调用方传播
        OrmException e = assertThrows(OrmException.class, () -> queue.flushAsync(),
                "阶段1 future 未完成必须 fail-fast");
        assertAborted(e, 1);
    }

    @Test
    public void testPhase2FutureNotDoneAborts() {
        BatchActionQueueImpl queue = newQueue(CompletableFuture.completedFuture(null),
                new CompletableFuture<>());

        OrmException e = assertThrows(OrmException.class, () -> queue.flushAsync(),
                "阶段2（逆序 delete）future 未完成必须 fail-fast");
        assertAborted(e, 2);
    }

    /**
     * 结构钉：两处未完成判定必须均带 phase 参数（1/2），删除任一 throw 点或参数 → 红。
     */
    @Test
    public void testThrowSitesCarryPhaseParam() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Paths.get(
                "src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java"));
        int first = src.indexOf("ERR_ORM_BATCH_FLUSH_ABORTED");
        int second = src.indexOf("ERR_ORM_BATCH_FLUSH_ABORTED", first + 1);
        assertTrue(first > 0 && second > first, "flushAsync 必须有两个 abort throw 点");
        assertTrue(src.substring(first, second).contains("\"phase\", 1"), "阶段1 throw 必须带 phase=1");
        assertTrue(src.substring(second, second + 300).contains("\"phase\", 2"), "阶段2 throw 必须带 phase=2");
    }
}
