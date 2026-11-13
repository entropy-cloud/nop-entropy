package io.nop.code.service.impl;

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

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-07 + W-09 (plan 2565, adjudications E and F, same-file diff group with W-08):
 * - W-07: findSymbolIdsByFileId must paginate to exhaustion (single MAX_QUERY_RESULTS page
 *   truncated the symbol set -> delete/stale-doc residue for files with more symbols);
 * - W-09: deleteFileRecords must clear the Dependency targetFilePath side symmetrically
 *   (edges from OTHER files pointing INTO the deleted file were left dangling).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestDeleteFileRecordsExhaustiveW07W09 extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    /** file declaring 5 distinct symbols (> the injected pageSize=2) */
    private static final String MANY_SYMBOLS = """
            package w07;
            public class A { public void m1() {} }
            class B { public void m2() {} }
            class C { public void m3() {} }
            class D { public void m4() {} }
            class E { public void m5() {} }
            """;

    private CodeIndexService impl() {
        return (CodeIndexService) codeIndexService;
    }

    private List<String> findSymbolIds(String fileId) throws Exception {
        Method m = CodeIndexService.class.getDeclaredMethod("findSymbolIdsByFileId", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) m.invoke(impl(), fileId);
        return ids;
    }

    /** W-07 red0: with pageSize=2 and 5 symbols, the full id set must be returned and delete must leave zero residue */
    @Test
    void testFindSymbolIdsByFileIdPaginatesToExhaustion() throws Exception {
        String indexId = "w07_exhaust";
        impl().setSymbolIdPageSizeForTest(2);
        try {
            codeIndexService.indexFile(indexId, "w07/Many.java", MANY_SYMBOLS);

            String fileId = fileIdOf(indexId, "w07/Many.java");
            List<String> ids = findSymbolIds(fileId);
            assertTrue(ids.size() >= 5, "expected >=5 symbols, got " + ids.size()
                    + " — single-page truncation regression (W-07)");

            IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
            codeIndexService.batchDeleteFileRecords(indexId, List.of("w07/Many.java"));

            assertEquals(0, symbolDao.findAllByQuery(new QueryBean()
                            .addFilter(FilterBeans.eq("fileId", fileId))).size(),
                    "deleteFileRecords must clear ALL symbols past the page boundary (W-07)");
        } finally {
            impl().setSymbolIdPageSizeForTest(10000);
        }
    }

    /** W-07 boundary guard: short batch must terminate the loop (no infinite offset advance) */
    @Test
    void testPaginationTerminatesOnShortBatch() throws Exception {
        String indexId = "w07_short";
        impl().setSymbolIdPageSizeForTest(2);
        try {
            codeIndexService.indexFile(indexId, "w07/Short.java",
                    "package w07;\npublic class Solo { public void go() {} }\n");
            String fileId = fileIdOf(indexId, "w07/Short.java");
            List<String> ids = findSymbolIds(fileId);
            assertFalse(ids.isEmpty());
            assertTrue(ids.size() < 3, "short-batch loop must terminate");
        } finally {
            impl().setSymbolIdPageSizeForTest(10000);
        }
    }

    /** W-09 red0: (source=C, target=A) — deleting A must remove the incoming edge */
    @Test
    void testDependencyTargetSideSymmetricCleanup() {
        IEntityDao<NopCodeDependency> depDao = daoProvider.daoFor(NopCodeDependency.class);
        String indexId = "w09_target";
        String fileA = "w09/A.java";
        String fileC = "w09/C.java";

        NopCodeDependency incoming = depDao.newEntity();
        incoming.setId("w09-incoming");
        incoming.setIndexId(indexId);
        incoming.setSourceFilePath(fileC);
        incoming.setTargetFilePath(fileA);

        NopCodeDependency outgoing = depDao.newEntity();
        outgoing.setId("w09-outgoing");
        outgoing.setIndexId(indexId);
        outgoing.setSourceFilePath(fileA);
        outgoing.setTargetFilePath(fileC);

        depDao.saveEntity(incoming);
        depDao.saveEntity(outgoing);
        depDao.flushSession();

        codeIndexService.batchDeleteFileRecords(indexId, List.of(fileA));

        assertNull(depDao.getEntityById("w09-incoming"),
                "target-side (incoming) dependency edge must be deleted with file A (W-09)");
        assertNull(depDao.getEntityById("w09-outgoing"),
                "source-side (outgoing) dependency edge must still be deleted (P1-6.7-011 kept)");
    }

    /** W-09 chain2 guard: target-side leg must keep the indexId scoping (other index untouched) */
    @Test
    void testTargetSideCleanupStaysIndexScoped() {
        IEntityDao<NopCodeDependency> depDao = daoProvider.daoFor(NopCodeDependency.class);
        String fileA = "w09s/A.java";

        NopCodeDependency otherIndex = depDao.newEntity();
        otherIndex.setId("w09s-other");
        otherIndex.setIndexId("w09s-idx-b");
        otherIndex.setSourceFilePath("w09s/C.java");
        otherIndex.setTargetFilePath(fileA);

        NopCodeDependency thisIndex = depDao.newEntity();
        thisIndex.setId("w09s-this");
        thisIndex.setIndexId("w09s-idx-a");
        thisIndex.setSourceFilePath("w09s/C.java");
        thisIndex.setTargetFilePath(fileA);

        depDao.saveEntity(otherIndex);
        depDao.saveEntity(thisIndex);
        depDao.flushSession();

        codeIndexService.batchDeleteFileRecords("w09s-idx-a", List.of(fileA));

        assertNull(depDao.getEntityById("w09s-this"));
        assertNotNull(depDao.getEntityById("w09s-other"),
                "target-side leg must stay indexId-scoped (P1-6.7-011 frozen contract)");
    }

    private String fileIdOf(String indexId, String filePath) throws Exception {
        Method m = CodeIndexService.class.getDeclaredMethod("generateFileId", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(impl(), indexId, filePath);
    }
}
