/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-002（plan 2553 裁定 F）钉：既有会话分支异常 → failed future（与新会话分支契约一致）。
 * 变异红：try/catch 擦除后同步穿透红。
 */
public class TestRunInSessionAsyncContract extends AbstractOrmTestCase {

    @Test
    public void pin_existingSessionSyncThrowBecomesFailedFuture() {
        orm().runInSession(session -> {
            RuntimeException boom = new RuntimeException("boom-from-existing-session-callback");

            // 既有会话分支：同 factory 且 stateless 匹配，runInSessionAsync 不得把回调的
            // 同步异常穿透给调用方，必须收敛为 failed future
            CompletionStage<Object> stage = orm().runInSessionAsync(s -> {
                throw boom;
            });

            CompletionException err = assertThrows(CompletionException.class,
                    () -> stage.toCompletableFuture().join());
            assertTrue(err.getCause() == boom || err.getCause() instanceof RuntimeException,
                    "failed future 的 cause 必须是回调抛出的 RuntimeException，实际: " + err.getCause());
            assertTrue(err.getCause() == boom,
                    "cause 必须原样保留回调异常（不得包装丢失），实际: " + err.getCause());
            return null;
        });
    }
}
