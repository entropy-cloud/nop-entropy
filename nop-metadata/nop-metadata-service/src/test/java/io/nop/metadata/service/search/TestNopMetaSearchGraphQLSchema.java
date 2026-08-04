package io.nop.metadata.service.search;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLListType;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.search.api.ISearchEngine;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-MA1-001 回归测试：NopMetaSearch GraphQL schema 类型解析 + 字段选择查询。
 *
 * <p>（a）schema 类型解析断言：经真实 IGraphQLEngine（非 mock）断言 `NopMetaSearch` 查询操作存在、
 * 其 `items` 字段元素类型解析为 `io.nop.metadata.api.dto.SearchHitDTO`（GraphQL 类型名
 * `g_io_nop_metadata_api_dto_SearchHitDTO`），且该类型定义可解析——不再指向已不存在的
 * core.dto 包下的 SearchHitDTO（DTO 迁移 c3162d4da 后 core.dto 包不存在）。
 *
 * <p>（b）端到端查询断言：经 IGraphQLEngine 执行选择 `items` 字段的 GraphQL 查询，验证
 * 搜索 BizModel 经容器注入的 ISearchEngine 真正可用且字段选择完整走通。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaSearchGraphQLSchema extends JunitBaseTestCase {

    private static final String SEARCH_HIT_DTO_GQL_TYPE = "g_io_nop_metadata_api_dto_SearchHitDTO";

    public TestNopMetaSearchGraphQLSchema() {
        setTestConfig("nop.orm.init-database-schema", true);
        setTestConfig("nop.search.index-dir", "./target/search-test-index");
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    @Nullable
    ISearchEngine searchEngine;

    @Test
    public void testNopMetaSearchItemsTypeResolvesToApiDto() {
        GraphQLFieldDefinition op = graphQLEngine.getOperationDefinition(GraphQLOperationType.query,
                "NopMetaSearch__searchMetadata");
        assertNotNull(op, "NopMetaSearch__searchMetadata operation must exist in GraphQL schema");
        assertTrue(op.getType() instanceof GraphQLNamedType,
                "operation result type must be a named type: " + op.getType());

        String resultTypeName = ((GraphQLNamedType) op.getType()).getName();
        GraphQLTypeDefinition resultType = graphQLEngine.getTypeDefinition(resultTypeName);
        assertNotNull(resultType, "result type " + resultTypeName + " must be defined in schema");
        assertTrue(resultType instanceof GraphQLObjectDefinition,
                "result type must be an object definition: " + resultTypeName);

        GraphQLFieldDefinition items = findField((GraphQLObjectDefinition) resultType, "items");
        assertNotNull(items, "NopMetaSearch result type must expose items field");

        String elementTypeName = unwrapListElementTypeName(items.getType());
        assertNotNull(elementTypeName, "items must be a list type, got: " + items.getType());
        assertEquals(SEARCH_HIT_DTO_GQL_TYPE, elementTypeName,
                "items element type must resolve to io.nop.metadata.api.dto.SearchHitDTO, "
                        + "not the stale core.dto SearchHitDTO");

        assertNotNull(graphQLEngine.getTypeDefinition(elementTypeName),
                "element type " + elementTypeName + " must be resolvable in schema");
    }

    @Test
    public void testNopMetaSearchItemsSelectionQuery() {
        assertNotNull(searchEngine,
                "ISearchEngine bean must be available in the test container (search-defaults.beans.xml "
                        + "ioc:default=true). If this fails, the e2e section degrades to schema-level "
                        + "assertion (a) with the reason recorded in the plan/log.");

        GraphQLResponseBean response = execute(
                "query { NopMetaSearch__searchMetadata(query: \"nonexistent-keyword\", entityType: \"MetaTable\", limit: 10) "
                        + "{ total limit items { id name } } }");
        assertFalse(response.hasError(), "searchMetadata query must not error: " + response);

        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull(data, "response data must be present: " + response);
        Object opData = data.get("NopMetaSearch__searchMetadata");
        assertNotNull(opData, "operation result must be present, got: " + data);
        data = (Map<String, Object>) opData;
        assertTrue(data.containsKey("total"), "data must expose total, got: " + data);
        assertEquals(0, ((Number) data.get("total")).intValue(), "empty index must report total=0");
        assertTrue(data.containsKey("limit"), "data must expose limit, got: " + data);
        assertEquals(10, ((Number) data.get("limit")).intValue(), "limit must be honored");

        List<?> items = (List<?>) data.get("items");
        assertNotNull(items, "items field must be selected and present");
        assertTrue(items.isEmpty(), "empty index must return no items");
    }

    private static GraphQLFieldDefinition findField(GraphQLObjectDefinition objDef, String name) {
        List<GraphQLFieldDefinition> fields = objDef.getFields();
        if (fields != null) {
            for (GraphQLFieldDefinition field : fields) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
        }
        return null;
    }

    private static String unwrapListElementTypeName(GraphQLType type) {
        GraphQLType cur = type;
        while (cur instanceof GraphQLListType) {
            cur = ((GraphQLListType) cur).getItemType();
        }
        if (cur instanceof GraphQLNamedType) {
            return ((GraphQLNamedType) cur).getName();
        }
        return null;
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
