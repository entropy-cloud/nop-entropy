package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N5.1 end-to-end: TypeScript call edges must be persisted through the standard full-index
 * flow. The analyzer produces calleeQualifiedName candidates (same-file and import-based);
 * ProjectAnalyzer.resolveCalls resolves them against the global symbol table. The fixture
 * deliberately uses a helper declared AFTER its caller (post-walk resolution) and avoids the
 * index.ts module form (its qn prefix does not match the symbol qn — INFERRED by design).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestTypeScriptCallGraphIntegration extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    private static final String FORMAT_TS = """
            export function formatName(n: string): string {
                return n.trim();
            }
            """;

    private static final String RUN_TS = """
            import { formatName } from './format';

            export function run(n: string): string {
                logIt(formatName(n));
                return n;
            }

            function logIt(n: string): void {}
            """;

    private String symbolIdByQn(String indexId, String qualifiedName) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        List<NopCodeSymbol> rows = dao.findAllByQuery(new QueryBean()
                .addFilter(FilterBeans.and(
                        FilterBeans.eq("indexId", indexId),
                        FilterBeans.eq("qualifiedName", qualifiedName))));
        assertFalse(rows.isEmpty(), "symbol must exist: " + qualifiedName);
        return rows.get(0).getId();
    }

    private List<NopCodeCall> callsFrom(String indexId, String callerSymbolId) {
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        return dao.findAllByQuery(new QueryBean()
                .addFilter(FilterBeans.and(
                        FilterBeans.eq("indexId", indexId),
                        FilterBeans.eq("callerId", callerSymbolId))));
    }

    @Test
    void testTypeScriptCallEdgesPersistedThroughFullIndex() throws Exception {
        Path src = tempDir.resolve("project/src/app");
        Files.createDirectories(src);
        Files.writeString(src.resolve("format.ts"), FORMAT_TS);
        Files.writeString(src.resolve("run.ts"), RUN_TS);

        String indexId = "n51_ts_callgraph";
        String projectRoot = tempDir.resolve("project").toString();
        assertTrue(codeIndexService.indexDirectory(indexId, projectRoot, "**/*.ts") >= 2,
                "both TS files must be indexed");

        String formatFnId = symbolIdByQn(indexId, "app.format.formatName");
        String runFnId = symbolIdByQn(indexId, "app.run.run");
        String logItId = symbolIdByQn(indexId, "app.run.logIt");

        List<NopCodeCall> edges = callsFrom(indexId, runFnId);

        assertTrue(edges.stream().anyMatch(c -> formatFnId.equals(c.getCalleeId())),
                "cross-file TS call edge run→formatName must be persisted with a resolved calleeId");
        assertTrue(edges.stream().anyMatch(c -> logItId.equals(c.getCalleeId())),
                "same-file TS call edge run→logIt (declared later) must be persisted");

        // the collected import statement must feed the file-level dependency graph (N3.1)
        IEntityDao<NopCodeDependency> depDao = daoProvider.daoFor(NopCodeDependency.class);
        List<NopCodeDependency> deps = depDao.findAllByQuery(new QueryBean()
                .addFilter(FilterBeans.eq("indexId", indexId)));
        // NOTE: resolved=false here is a PRE-EXISTING persist-layer flush-visibility behavior
        // (getProjectFilePaths cannot see file rows staged in the same unflushed batch; the
        // Java path has the identical shape) — recorded as a known residual in plan 15, not
        // an N5.1 regression. The N5.1 deliverable is that TS imports now PRODUCE rows at all.
        assertTrue(deps.stream().anyMatch(d -> "src/app/run.ts".equals(d.getSourceFilePath())
                        && d.getImportStatement() != null && d.getImportStatement().contains("./format")),
                "TS import must produce a dependency row, got: " + deps.stream()
                        .map(d -> d.getSourceFilePath() + "->" + d.getTargetFilePath() + " resolved=" + d.getResolved())
                        .toList());

        codeIndexService.deleteIndex(indexId);
    }
}
