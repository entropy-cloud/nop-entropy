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
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.commons.collections.MutableIntArray;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.session.IOrmSessionImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestRpcEntityPersistDriver {

    private RpcEntityPersistDriver driver;
    private IRpcServiceInvoker invoker;
    private IEntityModel entityModel;
    private IOrmSessionImplementor session;

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

    @Test
    public void testBatchExecuteAsyncChecksSaveResponse() {
        IBatchAction.EntitySaveAction saveAction = mock(IBatchAction.EntitySaveAction.class);
        IOrmEntity entity = mock(IOrmEntity.class);
        lenient().when(saveAction.getEntity()).thenReturn(entity);
        lenient().when(entity.orm_initedValues()).thenReturn(Map.of("sid", 1));

        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchModify"), any(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(ApiResponse.buildError(new ErrorBean("test.save-fail"))));

        CompletionException e = assertThrows(CompletionException.class,
                () -> driver.batchExecuteAsync(true, null, List.of(saveAction), null, null, session)
                        .toCompletableFuture().join());
        assertTrue(e.getCause() instanceof NopException, String.valueOf(e.getCause()));
        assertEquals("test.save-fail", ((NopException) e.getCause()).getErrorCode());
    }

    @Test
    public void testBatchExecuteAsyncChecksDeleteResponse() {
        IBatchAction.EntityDeleteAction deleteAction = mock(IBatchAction.EntityDeleteAction.class);
        lenient().when(deleteAction.getIdString()).thenReturn("1");

        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchDelete"), any(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(ApiResponse.buildError(new ErrorBean("test.delete-fail"))));

        CompletionException e = assertThrows(CompletionException.class,
                () -> driver.batchExecuteAsync(false, null, null, null, List.of(deleteAction), session)
                        .toCompletableFuture().join());
        assertTrue(e.getCause() instanceof NopException, String.valueOf(e.getCause()));
        assertEquals("test.delete-fail", ((NopException) e.getCause()).getErrorCode());
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

    @Test
    public void testBatchLoadAsyncBindsByIdNotByPosition() {
        IOrmEntity e1 = entityWithId(1);
        IOrmEntity e2 = entityWithId(2);

        // 远端乱序返回
        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchGet"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(ApiResponse.buildSuccess(List.of(row(2, "b"), row(1, "a")))));

        driver.batchLoadAsync(null, List.of(e1, e2), MutableIntArray.of(1, 2), null, session)
                .toCompletableFuture().join();

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(session).internalAssemble(eq(e1), captor.capture(), any());
        assertEquals("a", captor.getValue()[1]);

        verify(session).internalAssemble(eq(e2), captor.capture(), any());
        assertEquals("b", captor.getValue()[1]);
    }

    @Test
    public void testBatchLoadAsyncMissingEntityMarkedMissing() {
        IOrmEntity e1 = entityWithId(1);
        IOrmEntity e2 = entityWithId(2);

        // 远端少返回 e2：修复前 list.get(i++) 抛 IndexOutOfBoundsException
        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchGet"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(ApiResponse.buildSuccess(new ArrayList<>(List.of(row(1, "a"))))));

        driver.batchLoadAsync(null, List.of(e1, e2), MutableIntArray.of(1, 2), null, session)
                .toCompletableFuture().join();

        verify(session).internalAssemble(eq(e1), any(), any());
        verify(session).markMissing(e2);
    }

    @Test
    public void testBatchLoadAsyncUnknownRowIgnored() {
        IOrmEntity e1 = entityWithId(1);

        // 远端多返回了一条未知记录，不能错绑到本地实体
        when(invoker.invokeAsync(eq("remote-svc"), eq("EntityR__batchGet"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        ApiResponse.buildSuccess(new ArrayList<>(List.of(row(99, "x"), row(1, "a"))))));

        driver.batchLoadAsync(null, List.of(e1), MutableIntArray.of(1, 2), null, session)
                .toCompletableFuture().join();

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(session).internalAssemble(eq(e1), captor.capture(), any());
        assertEquals("a", captor.getValue()[1]);
    }
}
