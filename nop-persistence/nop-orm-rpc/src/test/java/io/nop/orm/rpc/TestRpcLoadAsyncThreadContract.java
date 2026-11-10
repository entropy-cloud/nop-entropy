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
import io.nop.api.core.context.BaseContextProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.commons.collections.MutableIntArray;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.session.IOrmSessionImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 2.5-C1-001（plan 2554 裁定 E）线程契约载体：装配回调（bindEntity/internalAssemble/
 * markMissing）会变更 session 状态，不得在 RPC 传输线程上裸执行。
 * 有 context：回调必须落入调用方 context 的线程纪律内（BaseContext 降级实现无专用 worker，
 * 最强可达钉为「回调执行时绑定调用方 context 实例」——缺陷态在完成线程裸回调时 context 为 null）；
 * 无 context：回调内联在完成线程上执行且 context 保持 null（禁止 thenOnContext 在 RPC 线程
 * 静默新建 context）。
 */
public class TestRpcLoadAsyncThreadContract {

    private static final String TRANSPORT_THREAD_NAME = "rpc-transport-thread";

    private RpcEntityPersistDriver driver;
    private IRpcServiceInvoker invoker;
    private IEntityModel entityModel;
    private IOrmSessionImplementor session;
    private ExecutorService transportExecutor;

    private final AtomicReference<String> callbackThreadName = new AtomicReference<>();
    private final AtomicReference<IContext> callbackContext = new AtomicReference<>();

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

        // 记录装配回调的执行线程与当时的 context 绑定（最强可达观察点）
        lenient().doAnswer(inv -> {
            callbackThreadName.set(Thread.currentThread().getName());
            callbackContext.set(ContextProvider.currentContext());
            return null;
        }).when(session).internalAssemble(any(), any(), any());

        transportExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, TRANSPORT_THREAD_NAME);
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    public void tearDown() {
        // 清除当前线程残留的 context ThreadLocal（防跨用例污染）
        BaseContextProvider.clear();
        transportExecutor.shutdownNow();
    }

    /**
     * 预置 invoker 返回未完成 future；测试先发起 loadAsync 再触发传输线程 complete，
     * 避免回调挂接前未来已完成（否则 thenAccept 会内联在挂接线程上，观察不到传输线程交错）
     */
    private CompletableFuture<ApiResponse<?>> armInvoker(List<Map<String, Object>> rows) {
        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
        when(invoker.invokeAsync(anyString(), anyString(), any(ApiRequest.class), isNull()))
                .thenReturn(future);
        return future;
    }

    private void completeOnTransportThread(CompletableFuture<ApiResponse<?>> future,
                                           List<Map<String, Object>> rows) {
        transportExecutor.execute(() -> {
            // 模拟 RPC 传输线程：无 context 绑定
            BaseContextProvider.clear();
            future.complete(ApiResponse.buildSuccess(rows));
        });
    }

    private IOrmEntity entityWithId(long id) {
        IOrmEntity entity = mock(IOrmEntity.class);
        lenient().when(entity.orm_idString()).thenReturn(String.valueOf(id));
        lenient().when(entity.orm_propValue(1)).thenReturn(id);
        lenient().when(entity.orm_propName(1)).thenReturn("sid");
        lenient().when(entity.orm_propName(2)).thenReturn("name");
        return entity;
    }

    @Test
    public void testLoadAsyncWithCallerContextCallbackRunsInsideContext() {
        IContext callerContext = ContextProvider.newContext(true);
        try {
            IOrmEntity e1 = entityWithId(1);
            CompletableFuture<ApiResponse<?>> rpcFuture = armInvoker(List.of(Map.of("sid", 1L, "name", "a")));

            // 先在调用方线程（持有 context）上发起加载（挂接回调），再由传输线程完成
            CompletableFuture<Void> future = driver
                    .loadAsync(null, e1, MutableIntArray.of(1, 2), null, session)
                    .toCompletableFuture();
            completeOnTransportThread(rpcFuture, List.of(Map.of("sid", 1L, "name", "a")));
            future.join();

            // 缺陷态：回调在 rpc-transport-thread 上裸执行，context 为 null → 红。
            // 修复后：经 thenOnContext 出口泵投递，回调执行时必须绑定调用方 context 实例。
            assertEquals(TRANSPORT_THREAD_NAME, callbackThreadName.get(),
                    "BaseContext 降级实现无专用 worker：出口泵任务在完成线程内联 flush（线程名不要求切换）");
            assertSame(callerContext, callbackContext.get(),
                    "装配回调执行时必须绑定调用方 context 实例（会话状态变更不得脱离 context 纪律落传输线程）");
        } finally {
            ContextProvider.instance().detachContext();
            callerContext.close();
        }
    }

    @Test
    public void testLoadAsyncWithoutContextRunsInlineOnTransportThread() {
        IOrmEntity e1 = entityWithId(1);
        CompletableFuture<ApiResponse<?>> rpcFuture = armInvoker(List.of(Map.of("sid", 1L, "name", "a")));

        CompletableFuture<Void> future = driver
                .loadAsync(null, e1, MutableIntArray.of(1, 2), null, session)
                .toCompletableFuture();
        completeOnTransportThread(rpcFuture, List.of(Map.of("sid", 1L, "name", "a")));
        future.join();

        // 无 context 调用方（同步等待同线程语义）：回调内联于完成线程，且不得新建 context
        assertEquals(TRANSPORT_THREAD_NAME, callbackThreadName.get(), "无 context 时回调内联于完成线程");
        assertNull(callbackContext.get(), "无 context 调用方不得在 RPC 线程上被静默创建 context");
    }

    @Test
    public void testBatchLoadAsyncWithCallerContextCallbackRunsInsideContext() {
        IContext callerContext = ContextProvider.newContext(true);
        try {
            IOrmEntity e1 = entityWithId(1);
            IOrmEntity e2 = entityWithId(2);
            CompletableFuture<ApiResponse<?>> rpcFuture = armInvoker(List.of(
                    Map.of("sid", 2L, "name", "b"),
                    Map.of("sid", 1L, "name", "a")));

            CompletableFuture<Void> future = driver
                    .batchLoadAsync(null, List.of(e1, e2), MutableIntArray.of(1, 2), null, session)
                    .toCompletableFuture();
            completeOnTransportThread(rpcFuture, List.of(
                    Map.of("sid", 2L, "name", "b"),
                    Map.of("sid", 1L, "name", "a")));
            future.join();

            assertEquals(TRANSPORT_THREAD_NAME, callbackThreadName.get());
            assertSame(callerContext, callbackContext.get(),
                    "批量装配回调同样必须绑定调用方 context 实例");
        } finally {
            ContextProvider.instance().detachContext();
            callerContext.close();
        }
    }
}
