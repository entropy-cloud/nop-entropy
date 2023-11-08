/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGraphQLEngine extends BaseTestCase {
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
    public void testLoad() {
        GraphQLRequestBean request = attachmentBean("request.yaml", GraphQLRequestBean.class);
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        CompletionStage<GraphQLResponseBean> promise = engine.executeGraphQLAsync(context);
        GraphQLResponseBean response = FutureHelper.syncGet(promise);
        System.out.println(JsonTool.serialize(response, true));
        assertEquals(attachmentJsonText("response.json"), JsonTool.serialize(response, true));
    }

    @Test
    public void testPrintSource() {
        String source = engine.getSchemaLoader().getGraphQLDocument().toSource();
        System.out.println(source);
    }

    @Test
    public void testRpcDirective() {
        String selection = "name,children @TreeChildren(max:5)";
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setSelection(new FieldSelectionBeanParser().parseFromText(null, selection));

        IGraphQLExecutionContext context = engine.newRpcContext(null, "MyEntity__get", request);
        String source = context.getFieldSelection().toString();
        System.out.println(source);

        assertEquals("MyEntity__get{name,children{name,children{name,children{name,children{name,children{name}}}}}}",source);

        System.out.println(context.getRequest().getDocument().toSource());
    }
}
