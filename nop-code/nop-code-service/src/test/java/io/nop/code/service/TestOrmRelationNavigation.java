package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.*;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestOrmRelationNavigation extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private static final String TEST_PROJECT_PATH =
            Paths.get("src/test/resources/test-project/src/main/java").toString();

    private String currentIndexId;

    @BeforeEach
    void setUp() {
        currentIndexId = "orm-nav-" + System.nanoTime();
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", currentIndexId);
        data.put("directoryPath", TEST_PROJECT_PATH);
        data.put("filePattern", "**/*.java");
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopCodeIndex__indexDirectory", request);
        ApiResponse<?> resp = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        assertTrue(resp.isOk(), "Indexing should succeed");
    }

    @Test
    void testInheritanceSuperTypeResolvesToSymbol() {
        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        List<NopCodeInheritance> inheritances = inhDao.findAll();
        assertFalse(inheritances.isEmpty(), "Test project should have inheritance relationships");

        Boolean foundResolved = ormTemplate.runInSession(session -> {
            for (NopCodeInheritance inh : inheritances) {
                NopCodeInheritance loaded = (NopCodeInheritance) session.get(
                        NopCodeInheritance.class.getName(), inh.getId());
                if (loaded == null) continue;
                NopCodeSymbol superType = loaded.getSuperType();
                if (superType != null) {
                    assertNotNull(superType.getId(), "Resolved superType should have an ID");
                    return true;
                }
            }
            return false;
        });
        assertTrue(foundResolved, "At least one inheritance should resolve superType to a NopCodeSymbol entity");
    }

    @Test
    void testAnnotationUsageAnnotationTypeResolvesToSymbol() {
        IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        List<NopCodeAnnotationUsage> usages = annotDao.findAll();
        assertFalse(usages.isEmpty(), "Test project should have annotation usages");

        Boolean foundResolved = ormTemplate.runInSession(session -> {
            for (NopCodeAnnotationUsage usage : usages) {
                NopCodeAnnotationUsage loaded = (NopCodeAnnotationUsage) session.get(
                        NopCodeAnnotationUsage.class.getName(), usage.getId());
                if (loaded == null) continue;
                NopCodeSymbol annotationType = loaded.getAnnotationType();
                if (annotationType != null) {
                    assertNotNull(annotationType.getId(), "Resolved annotationType should have an ID");
                    return true;
                }
            }
            return false;
        });
        assertTrue(foundResolved, "At least one annotation usage should resolve annotationType to a NopCodeSymbol entity");
    }

    @Test
    void testCascadeDeleteCompleteness() {
        String indexId = currentIndexId;

        long usageCountBefore = countByIndexId(NopCodeUsage.class, indexId);
        long inhCountBefore = countByIndexId(NopCodeInheritance.class, indexId);

        assertTrue(usageCountBefore > 0, "Should have usages before delete");
        assertTrue(inhCountBefore > 0, "Should have inheritances before delete");

        ormTemplate.runInSession(session -> {
            NopCodeIndex indexEntity = (NopCodeIndex) session.get(
                    NopCodeIndex.class.getName(), indexId);
            assertNotNull(indexEntity, "Index entity should exist");
            session.delete(indexEntity);
            session.flush();
            return null;
        });

        assertEquals(0, countByIndexId(NopCodeUsage.class, indexId),
                "Usages should be cascade-deleted");
        assertEquals(0, countByIndexId(NopCodeCall.class, indexId),
                "Calls should be cascade-deleted");
        assertEquals(0, countByIndexId(NopCodeInheritance.class, indexId),
                "Inheritances should be cascade-deleted");
        assertEquals(0, countByIndexId(NopCodeAnnotationUsage.class, indexId),
                "AnnotationUsages should be cascade-deleted");
        assertEquals(0, countByIndexId(NopCodeSemanticEdge.class, indexId),
                "SemanticEdges should be cascade-deleted");
        assertEquals(0, countByIndexId(NopCodeFile.class, indexId),
                "Files should be cascade-deleted");
        assertEquals(0, countByIndexId(NopCodeSymbol.class, indexId),
                "Symbols should be cascade-deleted");
    }

    private <T extends io.nop.dao.api.IDaoEntity> long countByIndexId(Class<T> entityClass, String indexId) {
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        return dao.findAllByQuery(query).size();
    }
}
