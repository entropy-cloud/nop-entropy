/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.api.core.util.FutureHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLLogger;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
@NopTestProperty(name = "nop.auth.graphql.enable-audit", value = "true")
@NopTestProperty(name = "nop.auth.graphql.audit-mutation-patterns", value = "NopAuthUser__*")
public class TestGraphQLLogger extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IAuditService auditService;

    @Inject
    IGraphQLLogger graphQLLogger;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testAudit() {
        long beginTime = CoreMetrics.currentTimeMillis();
        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.mutation, "NopAuthUser__save",
                request);
        ApiResponse<?> result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
        graphQLLogger.onRpcExecute(context, beginTime, result, null);
        assertTrue(FutureHelper.waitUntil(() -> auditService.isAllProcessed(), 10000));
        // 重新求值证据：批次处理计数清零不保证审计事务已提交——条件等待审计行带主键
        // 实际落库可读，再交由快照校验；上限仅作挂死保护
        assertTrue(FutureHelper.waitUntil(() -> {
            NopAuthOpLog example = new NopAuthOpLog();
            example.setDescription("保存数据");
            List<NopAuthOpLog> rows = daoProvider.daoFor(NopAuthOpLog.class).findAllByExample(example);
            return !rows.isEmpty() && rows.stream().allMatch(l -> l.getLogId() != null);
        }, 10000));
    }
}
