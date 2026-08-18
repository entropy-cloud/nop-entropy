/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.GraphQLEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(enableIoc = OptionalBoolean.FALSE)
public class TestBizObjectManager extends JunitBaseTestCase {

    BizObjectManager bizObjManager;

    GraphQLEngine engine;

    @BeforeEach
    public void setUp() {
        bizObjManager = new BizObjectManager();
        bizObjManager.setBizModelBeans(Arrays.asList(new MyObjectBizModel()));
        bizObjManager.init();

        engine = new GraphQLEngine();
        engine.init();
        engine.setSchemaLoader(bizObjManager);
    }

    @AfterEach
    public void tearDown() {
        bizObjManager.destroy();
    }

    @Test
    public void testQuery() {
        GraphQLRequestBean request = attachmentBean("request.yaml", GraphQLRequestBean.class);
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        GraphQLResponseBean response = engine.executeGraphQL(context);
        System.out.println(JSON.serialize(response, true));

        assertEquals(attachmentJsonText("response.json"), JSON.serialize(response, true));
    }

    @Test
    public void testStateMachine() {
        GraphQLRequestBean request = attachmentBean("mutation.yaml", GraphQLRequestBean.class);
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        GraphQLResponseBean response = engine.executeGraphQL(context);
        System.out.println(JSON.serialize(response, true));

        assertEquals(attachmentJsonText("mutation-response.json"), JSON.serialize(response, true));
    }

    /**
     * W12-impl Phase 1：@MfaRequired 元数据经 BizObjectBuildHelper.mergeBizModel（Java
     * biz-model → BizObject 合并的实际搬运点）不丢失；deepClone 路径
     * （getGraphQLDocument 的字段 deep-clone）同样保留。
     */
    @Test
    public void testMfaRequiredMetaSurvivesBizObjectMerge() {
        io.nop.graphql.core.ast.GraphQLFieldDefinition field = bizObjManager.getOperationDefinition(
                io.nop.graphql.core.ast.GraphQLOperationType.mutation, "MyObject__sensitiveMutation");
        org.junit.jupiter.api.Assertions.assertNotNull(field, "sensitiveMutation must be registered as operation");
        org.junit.jupiter.api.Assertions.assertNotNull(field.getMfaRequiredMeta(),
                "mergeBizModel must carry mfaRequiredMeta from java biz model to biz object operation");

        // deepClone 路径（getGraphQLDocument 对 operation 字段做 deepClone）
        io.nop.graphql.core.ast.GraphQLDocument doc = bizObjManager.getGraphQLDocument();
        boolean clonedHasMeta = doc.getDefinitions().stream()
                .filter(d -> d instanceof io.nop.graphql.core.ast.GraphQLObjectDefinition)
                .map(d -> (io.nop.graphql.core.ast.GraphQLObjectDefinition) d)
                .flatMap(d -> d.getFields().stream())
                .anyMatch(f -> "MyObject__sensitiveMutation".equals(f.getName()) && f.getMfaRequiredMeta() != null);
        org.junit.jupiter.api.Assertions.assertTrue(clonedHasMeta,
                "deep-cloned document must keep mfaRequiredMeta");
    }
}