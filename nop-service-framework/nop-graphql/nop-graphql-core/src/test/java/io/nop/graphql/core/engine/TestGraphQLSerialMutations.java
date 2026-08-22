/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraphQL规范要求mutation的顶层字段串行执行，保证副作用顺序。
 * 修复前：_invokeOperations循环内立即发起所有operation的fetcher，异步mutation的副作用可能乱序。
 */
public class TestGraphQLSerialMutations extends BaseTestCase {
    GraphQLEngine engine;
    SerialMutationBizModel bizModel;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        bizModel = new SerialMutationBizModel();
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader(List.of(bizModel)));
        engine.init();
    }

    private static void awaitEvent(List<String> events, String event) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (events.contains(event))
                return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new IllegalStateException("timeout waiting for event: " + event + ", events=" + events);
    }

    @Test
    public void testMutationsExecuteSerially() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("mutation { SerialMut__step1 SerialMut__step2 }");

        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        CompletionStage<GraphQLResponseBean> promise = engine.executeGraphQLAsync(context);

        List<String> events = bizModel.events;
        awaitEvent(events, "start1");

        // step1尚未完成时，step2不允许发起。修复前两个fetcher同时发起，此处即已包含start2
        assertFalse(events.contains("start2"),
                "mutation step2 must not start before step1 completes, events=" + events);

        bizModel.gate1.complete("r1");
        GraphQLResponseBean response = FutureHelper.syncGet(promise);

        assertTrue(events.contains("end1"));
        assertTrue(events.contains("start2"));
        assertTrue(events.indexOf("end1") < events.indexOf("start2"),
                "step2 side effect must be observed after step1 completes, events=" + events);
        assertEquals(0, response.getErrors() == null ? 0 : response.getErrors().size());
    }

    @BizModel("SerialMut")
    public static class SerialMutationBizModel {
        final List<String> events = new CopyOnWriteArrayList<>();
        final CompletableFuture<String> gate1 = new CompletableFuture<>();

        @BizMutation
        public CompletionStage<String> step1() {
            events.add("start1");
            return gate1.thenApply(v -> {
                events.add("end1");
                return v;
            });
        }

        @BizMutation
        public String step2() {
            events.add("start2");
            return "r2";
        }
    }
}
