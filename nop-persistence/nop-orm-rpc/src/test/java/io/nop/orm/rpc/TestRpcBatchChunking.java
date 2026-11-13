/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.commons.collections.MutableIntArray;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.session.IOrmSessionImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2.5-MA-11（plan 2554 裁定 D）：RPC 实体驱动按 nop.orm.rpc.batch-size 拆分多请求载体。
 * 断言目标：超 batch-size 批拆分为多个 invoker 调用且参数分块、返回 future 同步完成
 * （2553 守卫不变式：BatchActionQueueImpl 两阶段 flush 对未完成 future fail-fast）、
 * batchLoadAsync 结果聚合等价于未拆分基线。
 */
public class TestRpcBatchChunking {

    private RpcEntityPersistDriver driver;
    private IRpcServiceInvoker invoker;
    private IEntityModel entityModel;
    private IOrmSessionImplementor session;
    private final List<ApiRequest<?>> capturedRequests = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        driver = new RpcEntityPersistDriver();
        invoker = mock(IRpcServiceInvoker.class);
        entityModel = mock(IEntityModel.class);
        session = mock(IOrmSessionImplementor.class);

        IColumnModel sidCol = mock(IColumnModel.class);
        lenient().when(sidCol.getName()).thenReturn("sid");
        lenient().when(sidCol.getPropId()).thenReturn(1);
        lenient().doReturn(List.of(sidCol)).when(entityModel).getPkColumns();

        lenient().when(entityModel.getShortName()).thenReturn("EntityR");
        lenient().when(entityModel.getName()).thenReturn("EntityR");
        lenient().when(entityModel.getQuerySpace()).thenReturn(null);
        lenient().when(entityModel.getColumnByPropId(1, false)).thenReturn(sidCol);
        IColumnModel nameCol = mock(IColumnModel.class);
        lenient().when(nameCol.getName()).thenReturn("name");
        lenient().when(entityModel.getColumnByPropId(2, false)).thenReturn(nameCol);

        driver.setRpcServiceInvoker(invoker);
        driver.setServiceName("remote-svc");
        driver.init(entityModel, null);
    }

    @AfterEach
    public void tearDown() {
        // 恢复缺省配置，避免污染其他用例
        AppConfig.getConfigProvider().updateConfigValue(OrmRpcConfigs.CFG_ORM_RPC_BATCH_SIZE, null);
    }

    private IOrmEntity entityWithId(long id) {
        IOrmEntity entity = mock(IOrmEntity.class);
        lenient().when(entity.orm_idString()).thenReturn(String.valueOf(id));
        lenient().when(entity.orm_propValue(1)).thenReturn(id);
        lenient().when(entity.orm_propName(1)).thenReturn("sid");
        lenient().when(entity.orm_propName(2)).thenReturn("name");
        return entity;
    }

    private static Map<String, Object> row(long sid, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sid", sid);
        map.put("name", name);
        return map;
    }

    private static List<String> capturedIds(ApiRequest<?> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) request.getData();
        Object ids = data.get("ids");
        if (ids instanceof List) {
            return ((List<Object>) ids).stream().map(String::valueOf).collect(Collectors.toList());
        }
        // batchModify 请求的条目在 data 键下（每条为实体属性 Map，含 sid）
        Object rows = data.get("data");
        if (rows instanceof List) {
            return ((List<Map<String, Object>>) rows).stream()
                    .map(row -> String.valueOf(row.get("sid")))
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Test
    public void testBatchExecuteSplitByBatchSizeAndFutureDoneOnReturn() {
        AppConfig.getConfigProvider().updateConfigValue(OrmRpcConfigs.CFG_ORM_RPC_BATCH_SIZE, 2);

        List<IBatchAction.EntitySaveAction> saveActions = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            IBatchAction.EntitySaveAction action = mock(IBatchAction.EntitySaveAction.class);
            IOrmEntity entity = mock(IOrmEntity.class);
            lenient().when(action.getEntity()).thenReturn(entity);
            lenient().when(entity.orm_initedValues()).thenReturn(Map.of("sid", i));
            saveActions.add(action);
        }

        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchModify"), any(), isNull()))
                .thenAnswer(inv -> {
                    capturedRequests.add(inv.getArgument(2, ApiRequest.class));
                    return CompletableFuture.completedFuture(ApiResponse.buildSuccess(null));
                });

        CompletableFuture<Void> future = driver
                .batchExecuteAsync(true, null, saveActions, null, null, session)
                .toCompletableFuture();

        // 2553 守卫不变式：block-join 各 chunk 后返回，future 必须在返回时已完成
        assertTrue(future.isDone(), "batchExecuteAsync 返回 future 必须同步完成（2553 守卫不变式）");
        future.join();

        assertEquals(3, capturedRequests.size(), "5 条 save、batch-size=2 应拆为 2+2+1 三个请求");
        assertEquals(List.of("1", "2"), capturedIds(capturedRequests.get(0)));
        assertEquals(List.of("3", "4"), capturedIds(capturedRequests.get(1)));
        assertEquals(List.of("5"), capturedIds(capturedRequests.get(2)));
    }

    @Test
    public void testBatchExecuteErrorPropagatesInFailedFuture() {
        AppConfig.getConfigProvider().updateConfigValue(OrmRpcConfigs.CFG_ORM_RPC_BATCH_SIZE, 2);

        List<IBatchAction.EntitySaveAction> saveActions = new ArrayList<>();
        for (long i = 1; i <= 3; i++) {
            IBatchAction.EntitySaveAction action = mock(IBatchAction.EntitySaveAction.class);
            IOrmEntity entity = mock(IOrmEntity.class);
            lenient().when(action.getEntity()).thenReturn(entity);
            lenient().when(entity.orm_initedValues()).thenReturn(Map.of("sid", i));
            saveActions.add(action);
        }

        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchModify"), any(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(ApiResponse.buildSuccess(null)))
                .thenAnswer(inv -> CompletableFuture.completedFuture(
                        ApiResponse.buildError(new io.nop.api.core.beans.ErrorBean("test.chunk-fail"))));

        CompletableFuture<Void> future = driver
                .batchExecuteAsync(true, null, saveActions, null, null, session)
                .toCompletableFuture();

        assertTrue(future.isDone(), "chunk 失败经 block-join 捕获后仍以 failed future 同步返回");
        assertTrue(future.isCompletedExceptionally(), "第二个 chunk 失败必须以异常完成");
    }

    @Test
    public void testBatchLoadSplitByBatchSizeAndResultsMerged() {
        AppConfig.getConfigProvider().updateConfigValue(OrmRpcConfigs.CFG_ORM_RPC_BATCH_SIZE, 2);

        List<IOrmEntity> entities = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            entities.add(entityWithId(i));
        }

        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchGet"), any(), any()))
                .thenAnswer(inv -> {
                    ApiRequest<?> request = inv.getArgument(2, ApiRequest.class);
                    capturedRequests.add(request);
                    List<String> ids = capturedIds(request);
                    // 每个 chunk 只回带它请求的 ids（乱序），模拟远端行为
                    List<Map<String, Object>> rows = ids.stream()
                            .map(id -> row(Long.parseLong(id), "v" + id))
                            .collect(Collectors.toList());
                    java.util.Collections.reverse(rows);
                    return CompletableFuture.completedFuture(ApiResponse.buildSuccess(rows));
                });

        driver.batchLoadAsync(null, entities, MutableIntArray.of(1, 2), null, session)
                .toCompletableFuture().join();

        assertEquals(3, capturedRequests.size(), "5 个实体、batch-size=2 应拆为 2+2+1 三个请求");
        assertEquals(List.of("1", "2"), capturedIds(capturedRequests.get(0)));
        assertEquals(List.of("3", "4"), capturedIds(capturedRequests.get(1)));
        assertEquals(List.of("5"), capturedIds(capturedRequests.get(2)));

        // 聚合结果等价于未拆分基线：全部实体按主键正确装配
        for (long i = 1; i <= 5; i++) {
            IOrmEntity entity = entities.get((int) i - 1);
            ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
            verify(session).internalAssemble(eq(entity), captor.capture(), any());
            assertEquals("v" + i, captor.getValue()[1], "实体 " + i + " 必须按主键正确绑定");
        }
    }
}
