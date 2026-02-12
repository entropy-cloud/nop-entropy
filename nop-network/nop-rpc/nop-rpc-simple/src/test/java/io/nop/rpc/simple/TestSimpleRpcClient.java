/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.simple;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.core.reflect.impl.FunctionModel;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.rpc.core.reflect.DefaultRpcMessageTransformer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSimpleRpcClient {
    @Test
    public void testException() {
        ApiResponse<Object> res = ApiResponse.error(new ErrorBean("abc"));

        AtomicReference<Object> ref = new AtomicReference<>();

        CompletableFuture<Object> future = new CompletableFuture<>();
        future.complete(res);
        FunctionModel fn = new FunctionModel();
        fn.setReturnType(PredefinedGenericTypes.ANY_TYPE);
        future = future.thenApply(ret -> DefaultRpcMessageTransformer.INSTANCE.fromResponse("a", fn, res));
        future.whenComplete((ret, err) -> {
            ref.set(err);
        });
        assertTrue(ref.get().getClass() == CompletionException.class);

    }
}
