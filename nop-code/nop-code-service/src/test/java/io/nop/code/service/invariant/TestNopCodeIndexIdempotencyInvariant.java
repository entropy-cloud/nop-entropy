package io.nop.code.service.invariant;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.nop.api.core.beans.FilterBeans.eq;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * INV-03 invariant gate (Cycle 1 / I1): every public incremental-UPDATE method of
 * {@link ICodeIndexService} must be safe to retry — repeated invocation on the same
 * input must not produce duplicate records, duplicate processing, or cumulative
 * side effects.
 *
 * <p>This class has three parts:
 * <ol>
 *   <li><b>Green parameterized gate</b> — {@link #testIncrementalUpdateIsIdempotent}
 *       exhaustively enumerates the methods that are idempotent in the current live
 *       baseline and asserts retry-safety. This is the monotonic ratchet: a regression
 *       that breaks retry-safety of any of these methods turns the gate red.</li>
 *   <li><b>Executable red-list locks</b> — {@link #testKnownRedList*} methods document
 *       methods that currently VIOLATE idempotency (duplicate-key on retry). They pass
 *       by asserting the current broken behavior. When I4 fixes a method, the lock
 *       turns red, forcing the method to be moved from {@link #KNOWN_NON_IDEMPOTENT}
 *       into {@link #IDEMPOTENCE_TABLE}. This keeps the red list self-updating.</li>
 *   <li><b>Table-completeness gate</b> — {@link #testTableCompletenessGate} uses
 *       reflection to ensure every public method of {@code ICodeIndexService} is
 *       classified. A new method added to the interface that is not classified turns
 *       the gate red ("新增方法不入表即红").</li>
 * </ol>
 *
 * <p>Wiring: every method in {@link #IDEMPOTENCE_TABLE} is a real public method on
 * {@code ICodeIndexService} (verified by {@link #testIdempotenceTableEntriesAreRealInterfaceMethods}),
 * invoked through the live bean — not an isolated stub.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestNopCodeIndexIdempotencyInvariant extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    // ==================== Table-completeness classification ====================

    /**
     * The authoritative idempotency table: incremental-UPDATE methods that are
     * retry-safe in the current live baseline. Each is parameterized-tested below.
     */
    private static final Set<String> IDEMPOTENCE_TABLE = new HashSet<>(Arrays.asList(
            "triggerIncrementalIndex",
            "batchSaveFileRecords",
            "indexDirectory",
            "indexFile"
    ));

    /**
     * Methods that currently VIOLATE idempotency (duplicate-key / non-idempotent on
     * retry). These are I2 red-list / I4 fix targets. They are locked by
     * {@code testKnownRedList_*} assertions; when fixed, move them to IDEMPOTENCE_TABLE.
     *
     * <p>red-list reference: {@code ai-dev/audits/nop-code-invariants/gate-baseline-I1.md}
     *
     * <p>I4 fix: indexDirectory/indexFile moved here → IDEMPOTENCE_TABLE after
     * saveReplacingExisting was extended to catch JDBC duplicate-key (nop.err.dao.sql.duplicate-key).
     */
    private static final Set<String> KNOWN_NON_IDEMPOTENT = new HashSet<>(Arrays.asList(
    ));

    /**
     * Delete methods — excluded from idempotency (covered by INV-02 delete-contract).
     */
    private static final Set<String> DELETE_METHODS = new HashSet<>(Arrays.asList(
            "deleteIndex",
            "batchDeleteFileRecords"
    ));

    /**
     * Read-only query methods — idempotency-irrelevant (a read is trivially retry-safe).
     * Listed explicitly so that a rename surfaces for re-classification.
     */
    private static final Set<String> QUERY_METHODS = new HashSet<>(Arrays.asList(
            "getFiles", "getFile", "getFileSourceCode", "getFileSymbols", "getFileTypes",
            "getFileOutline", "getFileTree", "getModuleDigest", "getPublicSurface",
            "getSymbolById", "findSymbolByQualifiedName", "findSymbols", "findSymbolsPage",
            "getSymbolUsages", "getSymbolSourceCode", "showSymbolSource", "searchCode",
            "getTypeOutline", "batchGetTypeOutlines", "getTypeHierarchy", "getCallHierarchy",
            "findReferencedBy", "detectCommunities", "getGraphAnalysis", "getImpactAnalysis",
            "getCriticalNodes", "getKnowledgeGaps", "exportGraph", "diffGraph",
            "getDeps", "getReverseDeps", "findCycles", "getDepGraph",
            "detectFlows", "listFlows", "getFlow", "getAffectedFlows",
            "analyzeChanges", "detectDeadCode", "batchLoadFileRecords",
            "getIndexStats", "getIndexIds", "findFilesPage",
            "findByAnnotation", "findImplementations", "findDependentFiles"
    ));

    static Stream<Arguments> incrementalUpdateMethods() {
        return IDEMPOTENCE_TABLE.stream().map(Arguments::of);
    }

    // ==================== 1. Green parameterized idempotency gate ====================

    @ParameterizedTest(name = "{0}")
    @MethodSource("incrementalUpdateMethods")
    void testIncrementalUpdateIsIdempotent(String methodName) throws Exception {
        switch (methodName) {
            case "triggerIncrementalIndex":
                verifyTriggerIncrementalIndexIdempotent();
                break;
            case "batchSaveFileRecords":
                verifyBatchSaveFileRecordsIdempotent();
                break;
            case "indexDirectory":
                verifyIndexDirectoryIdempotent();
                break;
            case "indexFile":
                verifyIndexFileIdempotent();
                break;
            default:
                fail("IDEMPOTENCE_TABLE entry \"" + methodName + "\" has no verification branch — "
                        + "add a verifyXxx method and wire it here (table-completeness without coverage "
                        + "is a hollow gate, Rule #22/#25)");
        }
    }

    /**
     * triggerIncrementalIndex with no on-disk changes must be a no-op on every retry:
     * returns 0 changed and leaves file/symbol counts unchanged. This locks in the
     * AR-124 fix (path-comparison idempotency via MappedPathResource).
     */
    private void verifyTriggerIncrementalIndexIdempotent() throws Exception {
        Path projectDir = tempDir.resolve("incr-dir");
        Files.createDirectories(projectDir);
        writeJavaFile(projectDir, "Gamma.java", "public class Gamma { int g; }");
        writeJavaFile(projectDir, "Delta.java", "public class Delta { String d; }");
        Thread.sleep(50);

        String indexId = "idem-incr";
        String dirPath = projectDir.toAbsolutePath().toString();
        String vfsPath = toVfsPath(projectDir);

        // baseline full index (also seeds fingerprints)
        codeIndexService.indexDirectory(indexId, dirPath, "**/*.java");
        int filesAfterFull = countFiles(indexId, NopCodeFile.class);
        int symbolsAfterFull = countFiles(indexId, NopCodeSymbol.class);
        assertTrue(filesAfterFull >= 2, "baseline indexDirectory: >=2 file records");

        // first incremental — no changes
        int changed1 = codeIndexService.triggerIncrementalIndex(indexId, vfsPath, "none");
        int files1 = countFiles(indexId, NopCodeFile.class);
        int symbols1 = countFiles(indexId, NopCodeSymbol.class);

        // retry incremental — still no changes, must be no-op
        int changed2 = assertDoesNotThrow(() -> codeIndexService.triggerIncrementalIndex(indexId, vfsPath, "none"),
                "retry triggerIncrementalIndex must not throw");
        int files2 = countFiles(indexId, NopCodeFile.class);
        int symbols2 = countFiles(indexId, NopCodeSymbol.class);

        assertEquals(0, changed1, "first incremental with no changes should report 0 changed, got " + changed1);
        assertEquals(0, changed2, "retry incremental with no changes should report 0 changed, got " + changed2);
        assertEquals(filesAfterFull, files1, "first incremental must not change file count");
        assertEquals(filesAfterFull, files2, "retry incremental must not change file count");
        assertEquals(symbolsAfterFull, symbols1, "first incremental must not change symbol count");
        assertEquals(symbolsAfterFull, symbols2, "retry incremental must not change symbol count");
    }

    /**
     * batchSaveFileRecords on the same fingerprints twice must not duplicate records
     * (deterministic IDs + saveReplacingExisting).
     */
    private void verifyBatchSaveFileRecordsIdempotent() {
        String indexId = "idem-batch";
        List<FileFingerprint> fps = new ArrayList<>();
        fps.add(new FileFingerprint("a/Foo.java", "hash-a", 100L, 10L));
        fps.add(new FileFingerprint("b/Bar.java", "hash-b", 200L, 20L));

        codeIndexService.batchSaveFileRecords(indexId, fps);
        int files1 = countFiles(indexId, NopCodeFile.class);
        assertTrue(files1 >= 2, "first batchSaveFileRecords: >=2 file records, got " + files1);

        // retry — same fingerprints
        assertDoesNotThrow(() -> codeIndexService.batchSaveFileRecords(indexId, fps),
                "retry batchSaveFileRecords must not throw");
        int files2 = countFiles(indexId, NopCodeFile.class);

        assertEquals(files1, files2,
                "batchSaveFileRecords retry must not duplicate (expected " + files1 + ", got " + files2 + ")");
    }

    /**
     * indexDirectory on the same directory twice (without clearing) must be idempotent:
     * the retry must not throw and file/symbol counts must stay stable. This locks the
     * I4 fix where saveReplacingExisting was extended to catch JDBC duplicate-key
     * (nop.err.dao.sql.duplicate-key) in addition to the ORM-level replace-existing error.
     */
    private void verifyIndexDirectoryIdempotent() throws Exception {
        Path projectDir = tempDir.resolve("idem-dir");
        Files.createDirectories(projectDir);
        writeJavaFile(projectDir, "Eta.java", "public class Eta { int e; }");
        writeJavaFile(projectDir, "Theta.java", "public class Theta { String t; }");
        Thread.sleep(50);

        String indexId = "idem-indexdir";
        String dirPath = projectDir.toAbsolutePath().toString();

        codeIndexService.indexDirectory(indexId, dirPath, "**/*.java");
        int filesAfterFirst = countFiles(indexId, NopCodeFile.class);
        int symbolsAfterFirst = countFiles(indexId, NopCodeSymbol.class);
        assertTrue(filesAfterFirst >= 2, "first indexDirectory: >=2 file records");

        // retry on the same index WITHOUT clearing — must not throw (was red-list before I4)
        assertDoesNotThrow(() -> codeIndexService.indexDirectory(indexId, dirPath, "**/*.java"),
                "retry indexDirectory must not throw after I4 duplicate-key fix");
        int filesAfterRetry = countFiles(indexId, NopCodeFile.class);
        int symbolsAfterRetry = countFiles(indexId, NopCodeSymbol.class);

        assertEquals(filesAfterFirst, filesAfterRetry,
                "indexDirectory retry must keep file count stable (expected " + filesAfterFirst + ", got " + filesAfterRetry + ")");
        assertEquals(symbolsAfterFirst, symbolsAfterRetry,
                "indexDirectory retry must keep symbol count stable (expected " + symbolsAfterFirst + ", got " + symbolsAfterRetry + ")");
    }

    /**
     * indexFile on the same file twice must be idempotent: retry must not throw and
     * symbol count must stay stable. I4 fix target (saveReplacingExisting duplicate-key catch).
     */
    private void verifyIndexFileIdempotent() {
        String indexId = "idem-indexfile";
        String filePath = "com/example/Iota.java";
        String source = "public class Iota { int x; void m() {} }";

        codeIndexService.indexFile(indexId, filePath, source);
        int symbolsAfterFirst = countFiles(indexId, NopCodeSymbol.class);

        // retry — same file, must not throw (was red-list before I4)
        assertDoesNotThrow(() -> codeIndexService.indexFile(indexId, filePath, source),
                "retry indexFile must not throw after I4 duplicate-key fix");
        int symbolsAfterRetry = countFiles(indexId, NopCodeSymbol.class);

        assertEquals(symbolsAfterFirst, symbolsAfterRetry,
                "indexFile retry must keep symbol count stable (expected " + symbolsAfterFirst + ", got " + symbolsAfterRetry + ")");
    }

    // ==================== 2. Executable red-list locks (KNOWN_NON_IDEMPOTENT) ====================
    //
    // I4 resolution: indexDirectory and indexFile were the two red-listed methods.
    // After the saveReplacingExisting duplicate-key catch fix, both are now retry-safe
    // and have been moved to IDEMPOTENCE_TABLE with dedicated verify branches
    // (verifyIndexDirectoryIdempotent / verifyIndexFileIdempotent). KNOWN_NON_IDEMPOTENT
    // is now empty. The two former assertThrows locks were removed because the methods
    // no longer throw on retry.

    // ==================== 3. Table-completeness gate ====================

    /**
     * Gate: every public method declared on {@link ICodeIndexService} must be
     * classified into exactly one of: {@link #IDEMPOTENCE_TABLE} (green-tested above),
     * {@link #KNOWN_NON_IDEMPOTENT} (red-list, locked), {@link #DELETE_METHODS}
     * (INV-02), or {@link #QUERY_METHODS} (read-only). A new method added to the
     * interface that is not classified turns this red.
     */
    @Test
    void testTableCompletenessGate() {
        Set<String> classified = new HashSet<>();
        classified.addAll(IDEMPOTENCE_TABLE);
        classified.addAll(KNOWN_NON_IDEMPOTENT);
        classified.addAll(DELETE_METHODS);
        classified.addAll(QUERY_METHODS);

        Set<String> objectMethods = new HashSet<>(Arrays.asList(
                "equals", "hashCode", "toString", "getClass", "notify", "notifyAll",
                "wait", "clone", "finalize"));

        Set<String> unclassified = new HashSet<>();
        for (Method m : ICodeIndexService.class.getMethods()) {
            String name = m.getName();
            if (objectMethods.contains(name)) continue;
            if (!classified.contains(name)) {
                unclassified.add(name);
            }
        }

        assertTrue(unclassified.isEmpty(),
                "ICodeIndexService has public method(s) not classified in the idempotency gate: "
                        + unclassified + ". A new method MUST be added to IDEMPOTENCE_TABLE (and given a "
                        + "verify branch), KNOWN_NON_IDEMPOTENT (with a red-list lock), DELETE_METHODS, "
                        + "or QUERY_METHODS. Leaving it unclassified breaks the exhaustive-enumeration "
                        + "invariant (新增方法不入表即红).");
    }

    /**
     * Gate: every entry in {@link #IDEMPOTENCE_TABLE} and {@link #KNOWN_NON_IDEMPOTENT}
     * must be a real public method on {@link ICodeIndexService} (wiring verification —
     * no orphan table entries / test stubs).
     */
    @Test
    void testClassifiedEntriesAreRealInterfaceMethods() {
        Set<String> ifaceNames = Arrays.stream(ICodeIndexService.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> classified = new HashSet<>();
        classified.addAll(IDEMPOTENCE_TABLE);
        classified.addAll(KNOWN_NON_IDEMPOTENT);
        for (String entry : classified) {
            assertTrue(ifaceNames.contains(entry),
                    "classified entry \"" + entry + "\" is not a public method of ICodeIndexService "
                            + "(orphan entry — remove it or wire to a real method)");
        }
    }

    /**
     * Gate: IDEMPOTENCE_TABLE and KNOWN_NON_IDEMPOTENT must be disjoint — a method
     * cannot be both green-idempotent and red-listed.
     */
    @Test
    void testGreenAndRedListsAreDisjoint() {
        Set<String> intersection = new HashSet<>(IDEMPOTENCE_TABLE);
        intersection.retainAll(KNOWN_NON_IDEMPOTENT);
        assertTrue(intersection.isEmpty(),
                "IDEMPOTENCE_TABLE and KNOWN_NON_IDEMPOTENT must be disjoint, overlap: " + intersection);
    }

    // ==================== Helpers ====================

    private void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String toVfsPath(Path p) {
        String abs = p.toAbsolutePath().toString().replace('\\', '/');
        if (abs.length() >= 2 && abs.charAt(1) == ':') abs = "/" + abs;
        return "file:" + abs;
    }

    private <T extends IDaoEntity> int countFiles(String indexId, Class<T> entityClass) {
        IEntityDao<T> dao = daoProvider.daoFor(entityClass);
        QueryBean q = new QueryBean();
        q.addFilter(eq("indexId", indexId));
        q.setLimit(10000);
        return dao.findAllByQuery(q).size();
    }
}
