package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.api.dto.CodeSearchResultDTO;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N4.1: pins the DB-LIKE degradation path of {@link CodeSearchService} structurally — the
 * service under test is constructed with {@code searchEngine=null}, independent of whatever
 * the container injects into the shared {@code CodeIndexService} singleton. The three LIKE
 * branches (SYMBOL_NAME / FULL_TEXT / COMBINED) are the production fallback whenever no
 * search engine is deployed, so they must stay covered even after the engine became the
 * default assembly.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestCodeSearchFallbackLike extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ICodeIndexService codeIndexService;

    @TempDir
    Path tempDir;

    private static final String FIXTURE = """
            package fb;

            /**
             * AlpacaTool documentation mentions quirqweaving.
             */
            public class AlpacaTool {
                public void spinWool() {}
            }
            """;

    private CodeSearchService fallbackService() {
        return new CodeSearchService(daoProvider, null, null);
    }

    private String indexFixture() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("fb"));
        Files.writeString(src.resolve("fb/AlpacaTool.java"), FIXTURE);
        String indexId = "n41_fallback_" + System.nanoTime();
        assertTrue(codeIndexService.indexDirectory(indexId, src.toString(), null) >= 1);
        return indexId;
    }

    @Test
    void testSymbolNameContainsMatch() throws Exception {
        String indexId = indexFixture();
        List<CodeSearchResultDTO> hits = fallbackService().searchCode(indexId, "pacaTool", "SYMBOL_NAME", null, null, 20);
        assertFalseSafe(hits);
        assertTrue(hits.stream().allMatch(h -> "SYMBOL_NAME".equals(h.getMatchType())),
                "SYMBOL_NAME branch must label its hits");
        assertTrue(hits.stream().anyMatch(h -> String.valueOf(h.getMatchedSymbolName()).contains("AlpacaTool")),
                "contains semantics must match mid-name token");

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testFullTextMatchesDocumentation() throws Exception {
        String indexId = indexFixture();
        List<CodeSearchResultDTO> hits = fallbackService().searchCode(indexId, "quirqweaving", "FULL_TEXT", null, null, 20);
        assertFalseSafe(hits);
        assertTrue(hits.stream().allMatch(h -> "FULL_TEXT".equals(h.getMatchType())),
                "FULL_TEXT branch must label its hits");

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testCombinedReturnsLabeledUnion() throws Exception {
        String indexId = indexFixture();
        List<CodeSearchResultDTO> hits = fallbackService().searchCode(indexId, "AlpacaTool", "COMBINED", null, null, 20);
        assertFalseSafe(hits);
        assertTrue(hits.stream().anyMatch(h -> "COMBINED".equals(h.getMatchType())),
                "COMBINED branch must label its hits");
        // union is deduplicated by qualified name
        long distinct = hits.stream().map(CodeSearchResultDTO::getMatchedQualifiedName).distinct().count();
        assertEquals(distinct, hits.size(), "COMBINED union must be deduplicated by qualified name");

        codeIndexService.deleteIndex(indexId);
    }

    private void assertFalseSafe(List<CodeSearchResultDTO> hits) {
        assertNotNull(hits, "fallback search must return a list, never null");
        assertTrue(!hits.isEmpty(), "LIKE fallback must find the fixture symbol");
    }
}
