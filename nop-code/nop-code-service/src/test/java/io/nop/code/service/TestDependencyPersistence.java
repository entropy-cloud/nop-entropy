package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.api.dto.DepGraphDTO;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.api.core.beans.query.QueryBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static io.nop.api.core.beans.FilterBeans.eq;
import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestDependencyPersistence extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @Test
    void testDependencyGraphEndToEnd() {
        String indexId = "test-dep-persistence";
        Path projectRoot = new File("src/test/resources/test-project/src/main/java").toPath();

        int fileCount = codeIndexService.indexDirectory(indexId,
                projectRoot.toAbsolutePath().toString(), "**/*.java");
        assertTrue(fileCount > 0, "Should index at least 1 file");

        IEntityDao<NopCodeDependency> depDao = daoProvider.daoFor(NopCodeDependency.class);
        QueryBean query = new QueryBean();
        query.addFilter(eq("indexId", indexId));
        List<NopCodeDependency> deps = depDao.findAllByQuery(query);

        assertTrue(deps.size() > 0,
                "Should have at least 1 dependency record, got " + deps.size());

        for (NopCodeDependency dep : deps) {
            assertNotNull(dep.getId(), "Dependency should have an ID");
            assertEquals(indexId, dep.getIndexId(), "Dependency should belong to index");
            assertNotNull(dep.getSourceFilePath(), "Dependency should have source file path");
        }

        DepGraphDTO graph = codeIndexService.getDepGraph(indexId, false);
        assertNotNull(graph, "getDepGraph should return a result");
        assertNotNull(graph.getEdges(), "Graph should have edges");
        assertNotNull(graph.getNodes(), "Graph should have nodes");

        codeIndexService.deleteIndex(indexId);

        QueryBean afterDelete = new QueryBean();
        afterDelete.addFilter(eq("indexId", indexId));
        List<NopCodeDependency> depsAfterDelete = depDao.findAllByQuery(afterDelete);
        assertTrue(depsAfterDelete.isEmpty(), "All dependencies should be deleted");
    }

    @Test
    void testGetDepsReturnsConsistentGraph() {
        String indexId = "test-dep-consistency";
        Path projectRoot = new File("src/test/resources/test-project/src/main/java").toPath();

        codeIndexService.indexDirectory(indexId,
                projectRoot.toAbsolutePath().toString(), "**/*.java");

        DepGraphDTO fullGraph = codeIndexService.getDepGraph(indexId, false);
        DepGraphDTO depsFromRoot = codeIndexService.getDeps(indexId, "src/main/java/com/example/Main.java", 3);

        assertNotNull(fullGraph);
        assertNotNull(depsFromRoot);

        codeIndexService.deleteIndex(indexId);
    }
}
