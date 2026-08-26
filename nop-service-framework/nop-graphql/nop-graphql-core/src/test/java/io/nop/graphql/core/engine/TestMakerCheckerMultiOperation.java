/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * maker-checker开启时多operation文档的tryAction决策：
 * 修复前maker-checker分支取context.getOperation().getFieldSelection()（恒为第一个顶层字段），
 * 1) 标注了@BizMakerChecker的字段排在未标注字段之后时，其tryAction永远不会被咨询（审批拦截被静默绕过）；
 * 2) 未标注字段排在标注字段之后时，会被错误路由进前者的tryAction。
 * 修复后以env.getSelection()（当前正在执行的顶层字段）为准，且HTTP GraphQL路径的request
 * 取selectionBean.args（原先env.getOpRequest()在该路径恒为null）。
 */
public class TestMakerCheckerMultiOperation extends BaseTestCase {
    GraphQLEngine engine;

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
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader(java.util.List.of(new McBizModel())));
        engine.init();
    }

    @BizModel("McObj")
    public static class McBizModel {
        @BizQuery
        public String a() {
            return "normal_a";
        }

        @BizQuery
        public String b(@Name("x") int x) {
            return "normal_b_" + x;
        }
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        context.setMakerCheckerEnabled(true);
        return FutureHelper.syncGet(engine.executeGraphQLAsync(context));
    }

    /**
     * B标注tryAction而A未标注（A在前）：B必须路由进自己的tryAction。
     * 修复前B的tryAction永远不会被咨询，B直接执行（审批拦截被静默绕过）。
     */
    @Test
    public void testTryActionConsultedForAnnotatedFieldAfterUnannotated() {
        AtomicReference<Object> capturedRequest = new AtomicReference<>();
        GraphQLFieldDefinition bDef = engine.getOperationDefinition(null, "McObj__b");
        assertNotNull(bDef, "McObj__b operation must be registered");
        IServiceAction tryAction = (request, selection, ctx) -> {
            capturedRequest.set(request);
            return "try_b";
        };
        bDef.setTryAction(tryAction);

        GraphQLResponseBean response = execute("query{ McObj__a McObj__b(x:1) }");
        assertTrue(response.getErrors() == null || response.getErrors().isEmpty(), "no errors expected");
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertEquals("normal_a", data.get("McObj__a"), "unannotated field must execute normally");
        assertEquals("try_b", data.get("McObj__b"), "annotated field must be routed into its tryAction");

        assertNotNull(capturedRequest.get(), "tryAction must receive the field request (args), not null");
        assertEquals(Map.of("x", 1), capturedRequest.get(),
                "HTTP GraphQL path: request comes from selectionBean args");
    }

    /**
     * A标注tryAction而B未标注（A在前）：B不得被错误路由进A的tryAction。
     * 修复前决策取第一个字段的fieldDef，B也会走A的tryAction（request=null、selection为B的）。
     */
    @Test
    public void testUnannotatedFieldAfterAnnotatedNotRoutedIntoTryAction() {
        GraphQLFieldDefinition aDef = engine.getOperationDefinition(null, "McObj__a");
        aDef.setTryAction((request, selection, ctx) -> "try_a");

        GraphQLResponseBean response = execute("query{ McObj__a McObj__b(x:2) }");
        assertTrue(response.getErrors() == null || response.getErrors().isEmpty(), "no errors expected");
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertEquals("try_a", data.get("McObj__a"));
        assertEquals("normal_b_2", data.get("McObj__b"),
                "field without tryAction must execute via its own fetcher even when the first operation has one");
    }
}
