package io.nop.refactor.graphql;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * The GraphQLEngine RPC end-to-end proof (nop-refactor WI6, Minimum Rules
 * #22/#23): a mutation request through the engine reaches the container-wired
 * {@link NopRefactorBizModel} bean (the beans.xml assembly proof), runs the
 * real rewrite chain, and returns the WI5 payload; the boundary failures
 * (cap, ruleset load) surface as structured response errors naming their
 * configuration keys.
 */
class TestNopRefactorGraphQL {

    static GraphQLEngine engine;
    static NopRefactorBizModel bizModel;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        bizModel = BeanContainer.getBeanByType(NopRefactorBizModel.class);
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new BizModelSchemaLoader(bizModel));
        engine.init();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static ApiRequest<Map<String, Object>> request(Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        return request;
    }

    private static Map<String, Object> input(String prefix, String... paths) {
        Map<String, Object> inner = new HashMap<>();
        inner.put("rulesetPrefix", prefix);
        inner.put("paths", List.of(paths));
        Map<String, Object> data = new HashMap<>();
        data.put("input", inner);
        return data;
    }

    private ApiResponse<?> rpc(String action, Map<String, Object> data) {
        IGraphQLExecutionContext context = engine.newRpcContext(
                GraphQLOperationType.mutation, action, request(data));
        return FutureHelper.syncGet(engine.executeRpcAsync(context));
    }

    private Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "refactor-graphql-rpc", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    @Test
    void previewRewriteReturnsThePayloadThroughTheWiredBeanWithoutWriting() throws Exception {
        Path file = writeTarget("preview/P.java",
                "class P { void m() { inlineCall(1); } }\n");
        Map<String, Object> data = input("/test/lint/graphql-rewrite",
                "target/refactor-graphql-rpc/preview/P.java");
        ApiResponse<?> response = rpc("Refactor__previewRewrite", data);

        assertTrue(response.isOk(), String.valueOf(response));
        String body = String.valueOf(response.get());
        assertTrue(body.contains("applied=false"), "preview reports applied=false: " + body);
        assertTrue(body.contains("directCall"), "the diff carries the rewrite: " + body);
        assertEquals("class P { void m() { inlineCall(1); } }\n", Files.readString(file),
                "preview never touches the file");
    }

    @Test
    void applyRewriteLandsTheRewriteAndReportsApplied() throws Exception {
        Path file = writeTarget("apply/A.java",
                "class A { void m() { inlineCall(1); } }\n");
        Map<String, Object> data = input("/test/lint/graphql-rewrite",
                "target/refactor-graphql-rpc/apply/A.java");
        ApiResponse<?> response = rpc("Refactor__applyRewrite", data);

        assertTrue(response.isOk(), String.valueOf(response));
        assertTrue(String.valueOf(response.get()).contains("applied=true"),
                "apply reports applied=true: " + response);
        assertEquals("class A { void m() { directCall(1); } }\n", Files.readString(file),
                "apply landed the rewrite through the wired bean");
    }

    @Test
    void oversizeTargetFailsStructuredNamingTheCap() throws Exception {
        Path file = writeTarget("cap/Big.java", "x".repeat(1024 * 1024 + 1));
        Map<String, Object> data = input("/test/lint/graphql-rewrite",
                "target/refactor-graphql-rpc/cap/Big.java");
        ApiResponse<?> response = rpc("Refactor__applyRewrite", data);

        assertFalse(response.isOk(), "an oversize target must fail the response");
        assertTrue(String.valueOf(response.getCode()).contains("max-source-size"),
                "the error must name the cap: " + response);
    }

    @Test
    void unknownRulesetPrefixFailsStructured() {
        Map<String, Object> data = input("/test/lint/does-not-exist",
                "target/refactor-graphql-rpc/apply/A.java");
        ApiResponse<?> response = rpc("Refactor__applyRewrite", data);

        assertFalse(response.isOk(), "an unloadable ruleset must fail the response");
    }

    /**
     * Mirrors the nop-graphql-core engine test's schema loader: reflects the
     * {@code @BizModel} annotations of the given container bean. The payload
     * object definitions are registered under the engine's generated
     * {@code g_<fqcn>} names — the same definitions a production xmeta
     * schema carries (the minimal reflection loader does not synthesize
     * them), so the RPC path exercises real type resolution + field
     * selection. The enum-typed components (nonApplied.reason, costTier) are
     * declared as String scalars and are simply not selected by the e2e
     * queries — the minimal loader does not synthesize enum types.
     */
    static class BizModelSchemaLoader implements IGraphQLSchemaLoader {
        final GraphQLBizModels bizModels = new GraphQLBizModels();
        final Map<String, io.nop.graphql.core.ast.GraphQLObjectDefinition> defs = new HashMap<>();

        BizModelSchemaLoader(Object... beans) {
            TypeRegistry registry = new TypeRegistry();
            bizModels.build(registry, List.of(beans));
            String sourceRange = "g_io_nop_lint_core_node_SourceRange";
            defs.put(sourceRange, objDef(sourceRange,
                    field("startByte", "Int"), field("endByte", "Int")));
            defs.put("RewriteInput", objDef("RewriteInput",
                    field("rulesetPrefix", "String"), listField("paths", "String")));
            defs.put("g_io_nop_refactor_graphql_RewriteInput", objDef(
                    "g_io_nop_refactor_graphql_RewriteInput",
                    field("rulesetPrefix", "String"), listField("paths", "String")));
            defs.put("g_io_nop_refactor_core_FileEdit", objDef(
                    "g_io_nop_refactor_core_FileEdit",
                    field("path", "String"), field("range", sourceRange),
                    field("summary", "String")));
            defs.put("g_io_nop_refactor_core_Verification", objDef(
                    "g_io_nop_refactor_core_Verification",
                    field("parseOk", "Boolean"), field("errorNodeCount", "Int"),
                    field("residualDiagnostics", "Int"), field("symbolIntact", "Boolean")));
            defs.put("g_io_nop_refactor_core_SkippedBuckets", objDef(
                    "g_io_nop_refactor_core_SkippedBuckets",
                    field("conflict", "Int"), field("outOfScope", "Int"),
                    field("unresolvedTarget", "Int"), field("rolledBack", "Int")));
            defs.put("g_io_nop_refactor_core_RefactorStats", objDef(
                    "g_io_nop_refactor_core_RefactorStats",
                    field("filesAffected", "Int"), field("editsApplied", "Int"),
                    field("skipped", "g_io_nop_refactor_core_SkippedBuckets"),
                    field("costTier", "String"), field("residualRuleCount", "Int")));
            defs.put("g_io_nop_refactor_core_NonApply", objDef(
                    "g_io_nop_refactor_core_NonApply",
                    field("reason", "String"), field("path", "String"),
                    field("detail", "String")));
            defs.put("g_io_nop_refactor_core_RefactorResult", objDef(
                    "g_io_nop_refactor_core_RefactorResult",
                    field("applied", "Boolean"),
                    listField("edits", "g_io_nop_refactor_core_FileEdit"),
                    field("diff", "String"),
                    field("verification", "g_io_nop_refactor_core_Verification"),
                    field("stats", "g_io_nop_refactor_core_RefactorStats"),
                    listField("nonApplied", "g_io_nop_refactor_core_NonApply")));
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
            return java.util.Set.of("Refactor");
        }

        @Override
        public Map<String, io.nop.graphql.core.ast.GraphQLFieldDefinition> getBizOperationDefinitions(
                String bizObjName) {
            return Collections.emptyMap();
        }
    }
}
