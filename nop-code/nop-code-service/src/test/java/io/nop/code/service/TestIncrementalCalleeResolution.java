package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeCall;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N3.1-s: incremental/single-file callee resolution.
 *
 * Fixture design (live analyzer semantics): JavaFileAnalyzer's type solver is
 * reflection-only, so calleeQualifiedName is produced for same-CU calls and for calls on
 * JRE-typed receivers, but NOT for project/classpath types outside the JRE. The cross-file
 * fixture exploits the JRE channel: a synthetic java/util/ArrayList.java is indexed as a
 * project symbol (java.util.ArrayList.isEmpty) while the caller resolves the same method via
 * the real JRE class — a genuine cross-file qn-bearing call edge. Utils.java exercises the
 * same-file edge (implicit this).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIncrementalCalleeResolution extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    private static final String CALLEE_QN = "java.util.ArrayList.isEmpty";
    private static final String CALLEE_PATH = "java/util/ArrayList.java";

    // synthetic source declaring the same package/class/method as the JRE class so the
    // caller's resolved qualified name matches an indexed project symbol in another file
    private static final String CALLEE_V1 = """
            package java.util;

            public class ArrayList {
                private int version1;
                public boolean isEmpty() { return version1 == 0; }
            }
            """;

    private static final String CALLEE_V2 = """
            package java.util;

            public class ArrayList {
                private int version1;
                private int version2;
                public boolean isEmpty() { return version2 == 0; }
            }
            """;

    private static final String CALLEE_METHOD_REMOVED = """
            package java.util;

            public class ArrayList {
                private int version1;
                private int version2;
            }
            """;

    private static final String CALLER_V1 = """
            package app;

            import java.util.ArrayList;

            public class Caller {
                private ArrayList<String> list = new ArrayList<>();
                public boolean check() {
                    return list.isEmpty();
                }
            }
            """;

    private static final String CALLER_V2 = """
            package app;

            import java.util.ArrayList;

            public class Caller {
                private ArrayList<String> list = new ArrayList<>();
                private String marker;
                public boolean check() {
                    return list.isEmpty();
                }
            }
            """;

    private static final String UTILS_V1 = """
            package app;

            public class Utils {
                public String upper(String s) { return s.toUpperCase(); }
                public String wrap(String s) { return "[" + upper(s) + "]"; }
            }
            """;

    private static final String UTILS_V2 = """
            package app;

            public class Utils {
                private int created;
                public String upper(String s) { return s.toUpperCase(); }
                public String wrap(String s) { created++; return "[" + upper(s) + "]"; }
            }
            """;

    private Path writeProject() throws Exception {
        Path src = tempDir.resolve("src");
        writeJavaFile(src.resolve("java/util"), "ArrayList.java", CALLEE_V1);
        writeJavaFile(src.resolve("app"), "Caller.java", CALLER_V1);
        writeJavaFile(src.resolve("app"), "Utils.java", UTILS_V1);
        return src;
    }

    private void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String fileTriggerPath(Path src) {
        return "file:" + src.toAbsolutePath().toString().replace('\\', '/');
    }

    private String symbolIdByQn(String indexId, String qualifiedName) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        List<NopCodeSymbol> rows = dao.findAllByQuery(new QueryBean()
                .addFilter(FilterBeans.and(
                        FilterBeans.eq("indexId", indexId),
                        FilterBeans.eq("qualifiedName", qualifiedName))));
        assertFalse(rows.isEmpty(), "symbol must exist in index: " + qualifiedName);
        return rows.get(0).getId();
    }

    private List<NopCodeCall> callRowsFrom(String indexId, String callerSymbolId) {
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        return dao.findAllByQuery(new QueryBean()
                .addFilter(FilterBeans.and(
                        FilterBeans.eq("indexId", indexId),
                        FilterBeans.eq("callerId", callerSymbolId))));
    }

    private boolean hasEdgeTo(String indexId, String callerSymbolId, String calleeSymbolId) {
        return callRowsFrom(indexId, callerSymbolId).stream()
                .anyMatch(r -> calleeSymbolId.equals(r.getCalleeId()));
    }

    /**
     * Fixture probe: the full-index flow must persist the cross-file caller→callee edge and
     * the same-file wrap→upper edge. If this fails the fixtures no longer produce qn-bearing
     * calls and the dependent assertions are meaningless.
     */
    @Test
    void testFixtureProducesResolvableCallEdges() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-probe";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        String checkId = symbolIdByQn(indexId, "app.Caller.check");
        String calleeId = symbolIdByQn(indexId, CALLEE_QN);
        assertTrue(hasEdgeTo(indexId, checkId, calleeId),
                "cross-file check→isEmpty edge missing after full index");

        String wrapId = symbolIdByQn(indexId, "app.Utils.wrap");
        String upperId = symbolIdByQn(indexId, "app.Utils.upper");
        assertTrue(hasEdgeTo(indexId, wrapId, upperId),
                "same-file wrap→upper edge missing after full index");

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Test A: re-index the CALLER incrementally; its cross-file edge must be re-persisted and
     * re-resolved via the DB symbol lookup (the untouched callee file keeps its symbol id).
     * Pre-fix this edge was silently dropped (calleeId null → persist skip).
     */
    @Test
    void testIncrementalCallerReanalysisResolvesCrossFileCallee() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-testa";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        String calleeIdBefore = symbolIdByQn(indexId, CALLEE_QN);

        Files.writeString(src.resolve("app/Caller.java"), CALLER_V2);
        int changed = codeIndexService.triggerIncrementalIndex(indexId, fileTriggerPath(src), null);
        assertEquals(1, changed, "only Caller.java should have changed");

        String checkId = symbolIdByQn(indexId, "app.Caller.check");
        assertTrue(hasEdgeTo(indexId, checkId, calleeIdBefore),
                "edge must be resolved to the untouched callee's current symbol id");

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Test B (changed→changed): both caller and callee change in one incremental run. The
     * caller's edge must point at the callee's NEW symbol id (in-memory map precedence over
     * the deleted DB rows) — two-stage analyze-all-then-resolve is what makes this possible.
     */
    @Test
    void testChangedToChangedEdgePointsAtNewSymbolIds() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-testb";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        String oldCalleeId = symbolIdByQn(indexId, CALLEE_QN);

        Files.writeString(src.resolve("app/Caller.java"), CALLER_V2);
        Files.writeString(src.resolve(CALLEE_PATH), CALLEE_V2);
        int changed = codeIndexService.triggerIncrementalIndex(indexId, fileTriggerPath(src), null);
        assertEquals(2, changed);

        String newCalleeId = symbolIdByQn(indexId, CALLEE_QN);
        assertNotEquals(oldCalleeId, newCalleeId, "re-analyzed callee must hold a fresh symbol id");

        String checkId = symbolIdByQn(indexId, "app.Caller.check");
        assertTrue(hasEdgeTo(indexId, checkId, newCalleeId),
                "changed→changed edge must resolve to the callee's new symbol id");
        assertFalse(hasEdgeTo(indexId, checkId, oldCalleeId),
                "no edge may point at the deleted old symbol id");

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Test C (indexFile): single-file re-index of the caller must also resolve its cross-file
     * edge (same shared resolution semantics as the incremental flow).
     */
    @Test
    void testIndexFileResolvesCrossFileCallee() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-testc";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        String calleeId = symbolIdByQn(indexId, CALLEE_QN);

        codeIndexService.indexFile(indexId, "app/Caller.java", CALLER_V2);

        String checkId = symbolIdByQn(indexId, "app.Caller.check");
        assertTrue(hasEdgeTo(indexId, checkId, calleeId),
                "indexFile edge must resolve to the untouched callee's symbol id");

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Test D: same-file method-call edge (wrap→upper, implicit this) must be restored by
     * incremental re-analysis of its own file. Pre-fix this edge was dropped too.
     */
    @Test
    void testIncrementalRestoresSameFileMethodEdge() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-testd";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        Files.writeString(src.resolve("app/Utils.java"), UTILS_V2);
        codeIndexService.triggerIncrementalIndex(indexId, fileTriggerPath(src), null);

        String wrapId = symbolIdByQn(indexId, "app.Utils.wrap");
        String upperId = symbolIdByQn(indexId, "app.Utils.upper");
        assertTrue(hasEdgeTo(indexId, wrapId, upperId),
                "same-file wrap→upper edge must be restored after incremental re-analysis");

        codeIndexService.deleteIndex(indexId);
    }

    // ====== N3.1-s Phase 2: dependent-edge recovery ======

    private void seedDependency(String indexId, String source, String target) {
        IEntityDao<io.nop.code.dao.entity.NopCodeDependency> depDao =
                daoProvider.daoFor(io.nop.code.dao.entity.NopCodeDependency.class);
        io.nop.code.dao.entity.NopCodeDependency dep = depDao.newEntity();
        dep.setIndexId(indexId);
        dep.setSourceFilePath(source);
        dep.setTargetFilePath(target);
        depDao.saveEntity(dep);
    }

    /**
     * Test E: changing the callee file deletes the dependent caller's edge (deleteFileRecords
     * removes rows with calleeId in the changed file's symbol set). Recovery must re-target it
     * onto the callee's NEW symbol id. The dependency row is seeded so the caller also shows up
     * in the N3.1 affectedFiles observable surface (recovery itself is snapshot-driven).
     */
    @Test
    void testIncrementalRecoversDependentEdgeToNewSymbolId() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-teste";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        String oldCalleeId = symbolIdByQn(indexId, CALLEE_QN);

        seedDependency(indexId, "app/Caller.java", CALLEE_PATH);
        Files.writeString(src.resolve(CALLEE_PATH), CALLEE_V2);
        codeIndexService.triggerIncrementalIndex(indexId, fileTriggerPath(src), null);

        String newCalleeId = symbolIdByQn(indexId, CALLEE_QN);
        assertNotEquals(oldCalleeId, newCalleeId);

        String checkId = symbolIdByQn(indexId, "app.Caller.check");
        assertTrue(hasEdgeTo(indexId, checkId, newCalleeId),
                "dependent caller edge must be recovered onto the callee's new symbol id");
        assertFalse(hasEdgeTo(indexId, checkId, oldCalleeId),
                "no recovered edge may point at the deleted old symbol id");
        assertTrue(codeIndexService.getLastIncrementalAffectedFiles(indexId).stream()
                        .anyMatch(f -> f.endsWith("Caller.java")),
                "seeded dependency must surface Caller.java in the affected set");

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Test F: when the callee method is removed, the dependent edge must die with it — no
     * resurrection, no dangling row (recovery drops edges whose target qn no longer exists).
     */
    @Test
    void testRemovedCalleeDoesNotResurrectDependentEdge() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-testf";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        String checkIdBefore = symbolIdByQn(indexId, "app.Caller.check");
        assertFalse(callRowsFrom(indexId, checkIdBefore).isEmpty(), "edge must exist before the change");

        Files.writeString(src.resolve(CALLEE_PATH), CALLEE_METHOD_REMOVED);
        codeIndexService.triggerIncrementalIndex(indexId, fileTriggerPath(src), null);

        String checkId = symbolIdByQn(indexId, "app.Caller.check");
        assertEquals(checkIdBefore, checkId, "caller file was not re-analyzed; symbol id must stand");
        assertTrue(callRowsFrom(indexId, checkId).isEmpty(),
                "edge must be dropped, not left dangling or resurrected");

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Test G: single-file re-index of the callee (indexFile) must also recover the dependent
     * caller's edge onto the new symbol id.
     */
    @Test
    void testIndexFileRecoversDependentEdge() throws Exception {
        Path src = writeProject();
        String indexId = "n31s-testg";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        String oldCalleeId = symbolIdByQn(indexId, CALLEE_QN);

        codeIndexService.indexFile(indexId, CALLEE_PATH, CALLEE_V2);

        String newCalleeId = symbolIdByQn(indexId, CALLEE_QN);
        assertNotEquals(oldCalleeId, newCalleeId);

        String checkId = symbolIdByQn(indexId, "app.Caller.check");
        assertTrue(hasEdgeTo(indexId, checkId, newCalleeId),
                "indexFile recovery must re-target the dependent edge to the new symbol id");
        assertFalse(hasEdgeTo(indexId, checkId, oldCalleeId),
                "no edge may point at the deleted old symbol id");

        codeIndexService.deleteIndex(indexId);
    }
}
