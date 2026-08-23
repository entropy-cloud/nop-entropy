/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_INVALID_FRAGMENT;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_QUERY_EXCEED_MAX_DEPTH;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * fragment引用嵌套层数校验：fragment预解析以level=-1为基线，使用点深度不叠加到fragment body上，
 * 若不限制引用链长度，可用链式fragment构造展开深度约为maxDepth×链长的查询（DoS防护绕过）。
 */
public class TestGraphQLFragmentDepth extends BaseTestCase {
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
        engine.setSchemaLoader(new MockGraphQLSchemaLoader(List.of(new TreeBizModel())));
        engine.init();
    }

    /**
     * 12个链式fragment（每个body只有1层字段）引用嵌套12层，超过maxDepth(7)。
     * 每个fragment独立校验均通过（body仅1层字段），展开深度防护只能靠引用链长度限制。
     * 修复前：链式引用不叠加深度，任意长度的fragment链均被放行。
     */
    @Test
    public void testFragmentChainExceedsMaxDepth() {
        StringBuilder sb = new StringBuilder();
        sb.append("query { Tree__get(id:\"1\") { ...F1 } }");
        for (int i = 1; i <= 12; i++) {
            if (i < 12) {
                sb.append(" fragment F").append(i).append(" on Tree { children { ...F").append(i + 1).append(" } }");
            } else {
                sb.append(" fragment F").append(i).append(" on Tree { children { name } }");
            }
        }

        NopException err = assertThrows(NopException.class, () -> engine.parseOperation(sb.toString(), true));
        assertEquals(ERR_GRAPHQL_QUERY_EXCEED_MAX_DEPTH.getErrorCode(), err.getErrorCode());
    }

    /**
     * 深度受限的合法fragment用法不受影响：引用嵌套1层。
     */
    @Test
    public void testFragmentWithinDepthStillWorks() {
        String query = "query { Tree__get(id:\"1\") { ...FA } } fragment FA on Tree { children { name } }";
        assertDoesNotThrow(() -> engine.parseOperation(query, true));
    }

    /**
     * 标准introspection查询形态（如FullType→TypeRef，仅2层引用嵌套，但fragment body自身
     * 字段嵌套较深）必须放行：总展开深度语义（展开深度≤maxDepth）会拒绝标准客户端的
     * IntrospectionQuery，属于行为回归，判定语义只能是引用嵌套层数。
     */
    @Test
    public void testIntrospectionShapeFragmentPasses() {
        StringBuilder typeRef = new StringBuilder("fragment TypeRef on Tree {");
        int indent = 0;
        for (int i = 0; i < 8; i++) {
            typeRef.append(" children {");
            indent++;
        }
        typeRef.append(" name");
        for (int i = 0; i < indent; i++) {
            typeRef.append(" }");
        }
        typeRef.append(" }");

        String query = "query { Tree__get(id:\"1\") { children { ...TypeRef } } }" + typeRef;
        assertDoesNotThrow(() -> engine.parseOperation(query, true));
    }

    /**
     * fragment循环引用（F1 -> F2 -> F1）在解析期即拒绝。修复前resolve阶段静默放行，
     * 执行期_fetchSelections对resolvedFragment无限递归导致StackOverflowError。
     */
    @Test
    public void testFragmentCycleRejected() {
        String query = "query { Tree__get(id:\"1\") { ...FC1 } }"
                + " fragment FC1 on Tree { children { ...FC2 } }"
                + " fragment FC2 on Tree { children { ...FC1 } }";

        NopException err = assertThrows(NopException.class, () -> engine.parseOperation(query, true));
        assertEquals(ERR_GRAPHQL_INVALID_FRAGMENT.getErrorCode(), err.getErrorCode());
    }

    public static class Tree {
        private String id;
        private String name;
        private List<Tree> children;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Tree> getChildren() {
            return children;
        }

        public void setChildren(List<Tree> children) {
            this.children = children;
        }
    }

    @BizModel("Tree")
    public static class TreeBizModel {
        @BizQuery("get")
        @GraphQLReturn(bizObjName = "Tree")
        public Tree getTree(@Name("id") String id) {
            Tree tree = new Tree();
            tree.setId(id);
            tree.setName("root");
            return tree;
        }
    }
}
