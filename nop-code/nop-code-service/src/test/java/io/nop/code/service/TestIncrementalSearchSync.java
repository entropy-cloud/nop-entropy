package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.impl.CodeIndexService;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-7: verifies the incremental re-index path keeps the search engine in sync with the DB.
 * Re-indexing a file that no longer declares a symbol must not leave a ghost doc behind.
 *
 * <p>Defence-in-depth: the entry points ({@code indexDirectory}/{@code indexFile}/
 * {@code triggerIncrementalIndex}) all call {@code deleteFileRecords} (which invokes
 * {@code removeDocs}) before re-persisting, and {@code saveFileResultInSession} additionally
 * purges stale symbol docs for the file right before {@code addDoc}. This test exercises the
 * end-to-end invariant through the {@code indexFile} re-index path.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIncrementalSearchSync extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Test
    void testReindexRemovesGhostSymbolDoc() {
        RecordingSearchEngine engine = new RecordingSearchEngine();
        ((CodeIndexService) codeIndexService).setSearchEngine(engine);

        String indexId = "incr-search-sync";
        String filePath = "com/example/Ghost.java";

        // Source A declares two methods; Source B keeps only one.
        String srcA = "package com.example;\npublic class Ghost {\n  void alpha() {}\n  void beta() {}\n}\n";
        String srcB = "package com.example;\npublic class Ghost {\n  void alpha() {}\n}\n";

        codeIndexService.indexFile(indexId, filePath, srcA);
        int docsAfterFirst = engine.docIds().size();
        assertTrue(docsAfterFirst > 1,
                "Engine should hold docs for every symbol in srcA, got " + docsAfterFirst);

        engine.removeCalls.set(0);
        codeIndexService.indexFile(indexId, filePath, srcB);
        int docsAfterReindex = engine.docIds().size();

        assertTrue(engine.removeCalls.get() > 0,
                "Re-index must invoke removeDocs to purge the file's prior symbol docs");
        assertTrue(docsAfterReindex < docsAfterFirst,
                "Re-indexing with fewer symbols must shrink the doc set (no ghost), "
                        + "before=" + docsAfterFirst + " after=" + docsAfterReindex);
        assertFalse(engine.docIds().isEmpty(),
                "Engine should still hold docs for the surviving symbols");
    }

    /** Minimal in-memory ISearchEngine that tracks add/remove calls and current doc ids. */
    static final class RecordingSearchEngine implements ISearchEngine {
        private final Set<String> docIds = new LinkedHashSet<>();
        final AtomicInteger removeCalls = new AtomicInteger();

        Set<String> docIds() {
            return Collections.unmodifiableSet(docIds);
        }

        @Override
        public SearchResponse search(SearchRequest request) {
            return new SearchResponse();
        }

        @Override
        public SearchableDoc getDoc(String docId) {
            return null;
        }

        @Override
        public List<SearchableDoc> getDocsByTerm(String topic, String term) {
            return new ArrayList<>();
        }

        @Override
        public Map<String, List<String>> analyzeDoc(SearchableDoc doc) {
            return Collections.emptyMap();
        }

        @Override
        public List<String> analyzeQuery(String query) {
            return new ArrayList<>();
        }

        @Override
        public void refreshBlocking(String topic) {
        }

        @Override
        public void addDocs(String topic, List<SearchableDoc> docs) {
            for (SearchableDoc doc : docs) {
                if (doc.getId() != null) {
                    docIds.add(doc.getId());
                }
            }
        }

        @Override
        public void removeDocs(String topic, List<String> docIds) {
            removeCalls.incrementAndGet();
            this.docIds.removeAll(docIds);
        }

        @Override
        public void removeTopic(String topic) {
            docIds.clear();
        }
    }
}
