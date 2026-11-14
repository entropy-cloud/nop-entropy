package io.nop.lint.graphql;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end (Minimum Rules #22): a GraphQL RPC request through the engine
 * reaches the container-wired {@link NopLintBizModel} bean (the beans.xml
 * platform-assembly proof) and the real fast-profile engine, returning the
 * diagnostic view; the boundary failures surface as structured response
 * errors.
 */
class TestNopLintGraphQL {

    static GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        NopLintBizModel bizModel = BeanContainer.getBeanByType(NopLintBizModel.class);
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new BizModelSchemaLoader(bizModel));
        engine.init();
    }

    // 测试卫生（合并修订 2026-11-14）：不在 @AfterAll 里 CoreInitialization.destroy()——
    // 全局上下文销毁后同类内其他测试的重初始化存在已关闭对象残留
    //（context-already-closed）。全局上下文由平台测试基建统一管理。


    /**
     * 测试卫生（合并修订 2026-11-14，与 treesitter B 修同型）：经 ContextProvider.runWithContext
     * 提供开放环境上下文再执行 RPC——否则 invokeWithBindingCtx 的 per-request 上下文在响应构建
     * 时闭合，restoreCtx 撞已关闭对象（context-already-closed）。
     */
    private ApiResponse<?> execute(String query, Map<String, Object> data) {
        // newRpcContext 也须在开放环境上下文内创建（绑定上下文复用开放分支）
        return ContextProvider.runWithContext(ctx -> FutureHelper.syncGet(
                engine.executeRpcAsync(engine.newRpcContext(
                        GraphQLOperationType.query, query, request(data)))));
    }

    private static ApiRequest<Map<String, Object>> request(Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        return request;
    }

    @Test
    void checkSourceQueryReturnsDiagnosticViewsThroughTheWiredBean() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "class Bad {\n    void x() {\n        throw new RuntimeException(\"b\");\n    }\n}\n");
        data.put("language", "java");
        ApiResponse<?> response = execute("Lint__checkSource", data);
        assertTrue(response.isOk(), String.valueOf(response));
        String body = String.valueOf(response.get());
        assertTrue(body.contains("ruleId") || body.contains("diagnostics"),
                "the diagnostic view shape is the response: " + body);
    }

    @Test
    void listRulesQueryExposesTheLibraryMetadata() {
        ApiResponse<?> response = execute("Lint__listRules", new HashMap<>());
        assertTrue(response.isOk(), String.valueOf(response));
        String body = String.valueOf(response.get());
        assertTrue(body.contains("nop/no-raw-exception") || body.contains("no-raw-exception"),
                "a known rule id is listed: " + body);
    }

    @Test
    void unknownRuleIdReturnsStructuredError() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "class A {\n}");
        data.put("language", "java");
        data.put("rules", List.of("nop/no-such-rule"));
        ApiResponse<?> response = execute("Lint__checkSource", data);
        assertFalse(response.isOk(), "unknown rule id must fail the response");
        assertTrue(String.valueOf(response.getCode()).contains("nop/no-such-rule"),
                "error must name the offending id: " + response);
    }

    @Test
    void oversizeSourceReturnsStructuredError() {
        char[] big = new char[1024 * 1024 + 1];
        java.util.Arrays.fill(big, 'x');
        Map<String, Object> data = new HashMap<>();
        data.put("source", new String(big));
        data.put("language", "java");
        ApiResponse<?> response = execute("Lint__checkSource", data);
        assertFalse(response.isOk(), "oversize source must fail the response");
        assertTrue(String.valueOf(response.getCode()).contains("max-source-size"),
                "error must name the cap: " + response);
    }

    /**
     * Mirrors the nop-graphql-core engine test's schema loader: reflects the
     * {@code @BizModel} annotations of the given container bean. The DTO
     * object definitions are registered under the engine's generated
     * {@code g_<fqcn>} names — the same definitions a production xmeta
     * schema carries (the minimal reflection loader does not synthesize
     * them), so the RPC path exercises real type resolution + field
     * selection.
     */
    static class BizModelSchemaLoader implements IGraphQLSchemaLoader {
        final GraphQLBizModels bizModels = new GraphQLBizModels();
        final Map<String, io.nop.graphql.core.ast.GraphQLObjectDefinition> defs = new HashMap<>();

        BizModelSchemaLoader(Object... beans) {
            TypeRegistry registry = new TypeRegistry();
            bizModels.build(registry, List.of(beans));
            defs.put("g_io_nop_lint_graphql_LintDiagnosticView",
                    objDef("g_io_nop_lint_graphql_LintDiagnosticView",
                            field("ruleId", "String"), field("severity", "String"),
                            field("message", "String"), field("line", "Int"),
                            field("endLine", "Int")));
            defs.put("g_io_nop_lint_graphql_LintCheckResult",
                    objDef("g_io_nop_lint_graphql_LintCheckResult",
                            listField("diagnostics", "g_io_nop_lint_graphql_LintDiagnosticView"),
                            field("errorCount", "Int"), field("warningCount", "Int"),
                            field("infoCount", "Int"), field("hintCount", "Int"),
                            field("otherCount", "Int"), field("total", "Int")));
            defs.put("g_io_nop_lint_graphql_LintRuleView",
                    objDef("g_io_nop_lint_graphql_LintRuleView",
                            field("id", "String"), field("severity", "String"),
                            field("message", "String"), field("category", "String"),
                            field("autoFixable", "Boolean")));
        }

        private static io.nop.graphql.core.ast.GraphQLObjectDefinition objDef(String name,
                io.nop.graphql.core.ast.GraphQLFieldDefinition... fields) {
            io.nop.graphql.core.ast.GraphQLObjectDefinition def =
                    new io.nop.graphql.core.ast.GraphQLObjectDefinition();
            def.setName(name);
            def.setFields(new ArrayList<>(List.of(fields)));
            return def;
        }

        private static io.nop.graphql.core.ast.GraphQLFieldDefinition field(String name,
                String scalar) {
            io.nop.graphql.core.ast.GraphQLFieldDefinition field =
                    new io.nop.graphql.core.ast.GraphQLFieldDefinition();
            field.setName(name);
            io.nop.graphql.core.ast.GraphQLNamedType type =
                    new io.nop.graphql.core.ast.GraphQLNamedType();
            type.setName(scalar);
            field.setType(type);
            return field;
        }

        private static io.nop.graphql.core.ast.GraphQLFieldDefinition listField(String name,
                String element) {
            io.nop.graphql.core.ast.GraphQLFieldDefinition field =
                    new io.nop.graphql.core.ast.GraphQLFieldDefinition();
            field.setName(name);
            io.nop.graphql.core.ast.GraphQLListType list =
                    new io.nop.graphql.core.ast.GraphQLListType();
            io.nop.graphql.core.ast.GraphQLNamedType type =
                    new io.nop.graphql.core.ast.GraphQLNamedType();
            type.setName(element);
            list.setType(type);
            field.setType(list);
            return field;
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLFieldDefinition getOperationDefinition(
                GraphQLOperationType opType, String name) {
            return bizModels.getOperationDefinition(opType, name);
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLObjectDefinition getObjectTypeDefinition(String objName) {
            return defs.get(objName);
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLObjectDefinition resolveTypeDefinition(
                io.nop.graphql.core.ast.GraphQLType type) {
            return defs.get(type.getNamedTypeName());
        }

        @Override
        public List<io.nop.graphql.core.ast.GraphQLFieldDefinition> getOperationDefinitions(
                GraphQLOperationType opType) {
            return Collections.emptyList();
        }

        @Override
        public io.nop.api.core.beans.FieldSelectionBean getFragmentDefinition(String objName,
                                                                              String fragmentName) {
            return null;
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLDocument getGraphQLDocument() {
            return new io.nop.graphql.core.ast.GraphQLDocument();
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLTypeDefinition getTypeDefinition(String objName) {
            return defs.get(objName);
        }

        @Override
        public Collection<io.nop.graphql.core.ast.GraphQLTypeDefinition> getTypeDefinitions() {
            return new ArrayList<>(defs.values());
        }

        @Override
        public java.util.Set<String> getBizObjNames() {
            return java.util.Set.of("Lint");
        }

        @Override
        public Map<String, io.nop.graphql.core.ast.GraphQLFieldDefinition> getBizOperationDefinitions(
                String bizObjName) {
            return Collections.emptyMap();
        }
    }
}
