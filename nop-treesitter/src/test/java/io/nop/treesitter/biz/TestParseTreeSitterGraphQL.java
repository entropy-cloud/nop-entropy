package io.nop.treesitter.biz;

import io.nop.api.core.beans.ApiRequest;
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
 * reaches the container-wired {@link TreeSitterBizModel} bean and the
 * tree-sitter runtime, returning the s-expression; an unknown grammar returns
 * a structured error carrying the defined code.
 */
class TestParseTreeSitterGraphQL {

    static GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        TreeSitterBizModel bizModel = BeanContainer.getBeanByType(TreeSitterBizModel.class);
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new BizModelSchemaLoader(bizModel));
        engine.init();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static ApiRequest<Map<String, Object>> rpcRequest(String source, String language) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("source", source);
        data.put("language", language);
        request.setData(data);
        return request;
    }

    @Test
    void parseTreeSitterQueryParsesThroughTheWiredBean() {
        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.query,
                "TreeSitter__parseTreeSitter", rpcRequest("{\"a\":1}", "json"));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertTrue(response.isOk(), String.valueOf(response));
        assertEquals("(document\n  (object\n    (pair\n      (string\n        (string_content))\n"
                + "      (number))))", response.get());
    }

    @Test
    void unknownLanguageReturnsStructuredError() {
        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.query,
                "TreeSitter__parseTreeSitter", rpcRequest("[]", "python"));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertFalse(response.isOk(), "unknown grammar must fail the response");
        assertTrue(String.valueOf(response.getCode()).contains("unknown-language")
                        || String.valueOf(response.getMsg()).contains("python"),
                "error must name the failing grammar: " + response.getCode() + " / " + response.getMsg());
    }

    /**
     * Mirrors the nop-graphql-core engine test's schema loader: reflects the
     * {@code @BizModel} annotations of the given container bean.
     */
    static class BizModelSchemaLoader implements IGraphQLSchemaLoader {
        final GraphQLBizModels bizModels = new GraphQLBizModels();
        final Map<String, io.nop.graphql.core.ast.GraphQLObjectDefinition> defs = new HashMap<>();

        BizModelSchemaLoader(Object... beans) {
            TypeRegistry registry = new TypeRegistry();
            bizModels.build(registry, List.of(beans));
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
            return java.util.Set.of("TreeSitter");
        }

        @Override
        public Map<String, io.nop.graphql.core.ast.GraphQLFieldDefinition> getBizOperationDefinitions(
                String bizObjName) {
            return Collections.emptyMap();
        }
    }
}
