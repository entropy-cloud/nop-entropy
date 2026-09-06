/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP GraphQL请求缺省variables键时的语义：未提供变量等同空变量集（变量取值为null），
 * 而不是在GraphQLVariable.buildValue(null)处NPE被收敛为internal error。
 */
public class TestGraphQLVariables extends BaseTestCase {
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
        engine.setSchemaLoader(new MockGraphQLSchemaLoader());
        engine.init();
    }

    @Test
    public void testMissingVariablesKeyDoesNotThrowNpe() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query($id:String){ MyEntity__get(id:$id){ id name } }");
        // 不设置variables：GraphQLRequestBean.variables为null
        assertTrue(request.getVariables() == null, "precondition: variables key is absent");

        // 修复前：initGraphQLContext → buildSelectionBean(null) → GraphQLVariable.buildValue(null)直接NPE
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        assertTrue(context.getFieldSelection() != null, "selection bean must be built with empty variable set");

        // 未提供的变量按null传入：后续得到的是明确的参数校验错误（field-null-arg），
        // 而非被web层收敛的internal error（NPE堆栈）
        GraphQLResponseBean response = FutureHelper.syncGet(engine.executeGraphQLAsync(context));
        assertTrue(response.getErrors() != null && !response.getErrors().isEmpty());
        assertTrue(response.getErrors().get(0).getMessage().contains("id"),
                "expected the explicit field-null-arg validation message, got: "
                        + response.getErrors().get(0).getMessage());
    }
}
