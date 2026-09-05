/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.rpc;

import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.dao.shard.ShardSelection;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.session.IOrmSessionImplementor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归覆盖审查报告 RPC-01：IEntityPersistDriver.batchExecuteAsync在无动作时必须返回
 * 已完成的空future而非null（null依赖调用方FutureHelper.collectWaiting的判空容忍，
 * 属于对接口契约的偏离，新增调用方按非null假设编码即NPE）。
 */
public class TestRpcEntityPersistDriverNullFuture {

    @Test
    public void testBatchExecuteAsyncReturnsNonNullFutureWhenNoActions() {
        RpcEntityPersistDriver driver = new RpcEntityPersistDriver();
        driver.setRpcServiceInvoker(new IRpcServiceInvoker() {
            @Override
            public java.util.concurrent.CompletionStage<io.nop.api.core.beans.ApiResponse<?>> invokeAsync(
                    String serviceName, String serviceMethod, io.nop.api.core.beans.ApiRequest<?> request,
                    io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException("无动作时不应发起RPC调用");
            }
        });

        CompletionStage<Void> ret = driver.batchExecuteAsync(true, "qs", null, null, null,
                (IOrmSessionImplementor) null);
        assertNotNull(ret, "无save/update动作时应返回已完成future而非null");

        ret = driver.batchExecuteAsync(false, "qs", null, null, Collections.emptyList(),
                (IOrmSessionImplementor) null);
        assertNotNull(ret, "无delete动作时应返回已完成future而非null");
    }

    @Test
    public void testLockAlwaysFailsDocumented() {
        // lock()语义为恒不支持：返回false使session.lock抛ERR_ORM_LOCK_ENTITY_FAIL
        RpcEntityPersistDriver driver = new RpcEntityPersistDriver();
        org.junit.jupiter.api.Assertions.assertFalse(
                driver.lock((ShardSelection) null, null, null, null, (IOrmSessionImplementor) null));
    }
}
