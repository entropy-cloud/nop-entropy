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
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.biz.model.BizModel;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体级 {@code no-service} 标签：标记后该 BizObject 的 GraphQL/RPC/gRPC 服务方法不再对外发布。
 * 覆盖运行时收口（BizObjectImpl）与真实入口接线（BizObjectManager），并钉住 {@code not-pub} 既有行为不变。
 */
@NopTestConfig(enableIoc = OptionalBoolean.FALSE)
public class TestNoServicePublish extends JunitBaseTestCase {

    BizObjectManager bizObjManager;

    @BeforeEach
    public void setUp() {
        bizObjManager = new BizObjectManager();
        bizObjManager.setBizModelBeans(Arrays.asList(new MyObjectBizModel(), new NoServiceObjectBizModel()));
        bizObjManager.init();
    }

    @AfterEach
    public void tearDown() {
        bizObjManager.destroy();
    }

    @Test
    public void testNoServiceOperationsHiddenThroughRealEntry() {
        // 真实入口：BizObjectManager -> IBizObject.getOperationDefinition*
        assertNull(bizObjManager.getOperationDefinition(GraphQLOperationType.query, "NoServiceObject__get"),
                "no-service biz object must not expose query operations");
        assertNull(bizObjManager.getOperationDefinition(GraphQLOperationType.mutation, "NoServiceObject__save"),
                "no-service biz object must not expose mutation operations");

        // 无参入口（gRPC/maker-checker 走这里）也必须为空
        Map<String, GraphQLFieldDefinition> noArg = bizObjManager.getBizOperationDefinitions("NoServiceObject");
        assertNotNull(noArg);
        assertTrue(noArg.isEmpty(), "no-service biz object must expose no operations on the no-arg entry");
    }

    @Test
    public void testUntaggedObjectUnaffected() {
        assertNotNull(bizObjManager.getOperationDefinition(GraphQLOperationType.query, "MyObject__get"),
                "untagged biz object operations must remain published");
        assertNotNull(bizObjManager.getOperationDefinition(GraphQLOperationType.mutation,
                "MyObject__sensitiveMutation"), "untagged biz object mutations must remain published");
    }

    /**
     * not-pub 既有行为不变：普通入口隐藏；无参入口（历史上未收口）仍返回操作。
     */
    @Test
    public void testNotPubLegacyBehaviorUnchanged() {
        BizObjectImpl bizObj = new BizObjectImpl("NotPubObject");
        BizModel bizModel = new BizModel();
        bizModel.setTagSet(Set.of("not-pub"));
        bizObj.setBizModel(bizModel);

        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setName("get");
        field.setOperationType(GraphQLOperationType.query);
        bizObj.setOperations(Map.of("get", field));

        assertNull(bizObj.getOperationDefinition(GraphQLOperationType.query, "get"),
                "not-pub must hide operations on the typed entry");
        assertTrue(bizObj.getOperationDefinitions(GraphQLOperationType.query).isEmpty(),
                "not-pub must hide operations on the opType entry");
        assertFalse(bizObj.getOperationDefinitions().isEmpty(),
                "not-pub no-arg entry must keep its legacy behavior (not gated)");
    }

    /**
     * no-service 无参入口收口（gRPC 路径），与 not-pub 的处理不同。
     */
    @Test
    public void testNoServiceNoArgEntryHidden() {
        BizObjectImpl bizObj = new BizObjectImpl("NoServiceObject");
        bizObj.setObjMeta(io.nop.xlang.xmeta.SchemaLoader.loadXMeta(
                "/biz/NoServiceObject/NoServiceObject.xmeta"));

        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setName("get");
        field.setOperationType(GraphQLOperationType.query);
        bizObj.setOperations(Map.of("get", field));

        assertNull(bizObj.getOperationDefinition(GraphQLOperationType.query, "get"));
        assertTrue(bizObj.getOperationDefinitions(GraphQLOperationType.query).isEmpty());
        assertTrue(bizObj.getOperationDefinitions().isEmpty(),
                "no-service must hide operations on the no-arg entry");
    }
}
