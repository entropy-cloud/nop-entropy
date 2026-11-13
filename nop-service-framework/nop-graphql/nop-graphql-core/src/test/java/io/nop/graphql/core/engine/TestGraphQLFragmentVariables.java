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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_VAR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * fragment 上下文变量校验：operation 已声明的变量在 fragment body 字段参数、fragment body 字段指令、
 * fragment spread 指令三处引用时必须放行（修复前 GraphQLSelectionResolver 对这三处传入恒空 map，
 * 误报 unknown-var 且整查被拒）；全文档未声明的变量仍然响亮拒绝。
 */
public class TestGraphQLFragmentVariables extends BaseTestCase {
    GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    @BeforeEach
    public void setUp() {
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader(
                "classpath:io/nop/graphql/core/engine/fragment-var-test.graphql",
                List.of(new TreeBizModel())));
        engine.init();
    }

    /**
     * 位点1：fragment body 字段参数引用已声明变量。修复前 fragment 预解析传空 map，
     * 报 ERR_GRAPHQL_UNKNOWN_VAR 且整查被拒。
     */
    @Test
    public void testFragmentBodyArgVariableResolved() {
        String query = "query($lim: Int) { Tree__get(id:\"1\") { ...FA } } "
                + "fragment FA on Tree { children(limit: $lim) { name } }";
        assertDoesNotThrow(() -> engine.parseOperation(query, true));
    }

    /**
     * 位点3：fragment body 字段指令参数引用已声明变量（体内字段指令收到的 vars 来自
     * fragment 预解析的传播链）。
     */
    @Test
    public void testFragmentBodyFieldDirectiveVariableResolved() {
        String query = "query($c: Boolean) { Tree__get(id:\"1\") { ...FB } } "
                + "fragment FB on Tree { name @skip(if: $c) }";
        assertDoesNotThrow(() -> engine.parseOperation(query, true));
    }

    /**
     * 位点2：fragment spread 自身指令参数引用已声明变量。该路径独立可达（fragment body
     * 无变量也触发），修复前与执行面 SelectionBeanBuilder 用真实 vars 求值直接矛盾。
     */
    @Test
    public void testFragmentSpreadDirectiveVariableResolved() {
        String query = "query($c: Boolean) { Tree__get(id:\"1\") { ...FC @skip(if: $c) } } "
                + "fragment FC on Tree { name }";
        assertDoesNotThrow(() -> engine.parseOperation(query, true));
    }

    /**
     * 平台自有 @Exclude 指令（base.graphql，FRAGMENT_SPREAD location）常量参数保持可用。
     */
    @Test
    public void testFragmentSpreadExcludeDirectiveConstantArgsResolved() {
        String query = "query { Tree__get(id:\"1\") { ...FD @Exclude(fields: [\"name\"]) } } "
                + "fragment FD on Tree { id name }";
        assertDoesNotThrow(() -> engine.parseOperation(query, true));
    }

    /**
     * 嵌套 fragment 链（F1 spread F2），变量在最内层 F2 body 中引用，预解析沿链传递 docVars。
     */
    @Test
    public void testNestedFragmentChainVariableResolved() {
        String query = "query($lim: Int) { Tree__get(id:\"1\") { ...F1 } } "
                + "fragment F1 on Tree { ...F2 } "
                + "fragment F2 on Tree { children(limit: $lim) { name } }";
        assertDoesNotThrow(() -> engine.parseOperation(query, true));
    }

    /**
     * 负对照：fragment body 引用全文档未声明的变量，必须仍报 unknown-var（不允许修复引入跳过校验）。
     */
    @Test
    public void testUndefinedVariableInFragmentBodyRejected() {
        String query = "query($lim: Int) { Tree__get(id:\"1\") { ...FE } } "
                + "fragment FE on Tree { children(limit: $undefinedVar) { name } }";
        NopException err = assertThrows(NopException.class, () -> engine.parseOperation(query, true));
        assertEquals(ERR_GRAPHQL_UNKNOWN_VAR.getErrorCode(), err.getErrorCode());
    }

    /**
     * 负对照：fragment spread 指令引用未声明变量，修复后的传入 vars 拒绝路径保持响亮失败。
     */
    @Test
    public void testUndefinedVariableInSpreadDirectiveRejected() {
        String query = "query($c: Boolean) { Tree__get(id:\"1\") { ...FF @skip(if: $undefinedVar) } } "
                + "fragment FF on Tree { name }";
        NopException err = assertThrows(NopException.class, () -> engine.parseOperation(query, true));
        assertEquals(ERR_GRAPHQL_UNKNOWN_VAR.getErrorCode(), err.getErrorCode());
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
