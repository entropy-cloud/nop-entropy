package io.nop.search.core.biz;

import io.nop.core.resource.impl.FileResource;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: SearchEngineBizModel's pure wiring faces —
 * pattern-to-filter translation (empty pattern falls back to the default
 * indexable filter, a regex pattern drives path matching), path resolution
 * through the optional resource locator (unresolvable paths must fail
 * loudly), and the delegation of search/addDoc onto the engine.
 */
public class TestSearchEngineBizModel {

    static final class StubEngine implements ISearchEngine {
        SearchRequest lastSearchRequest;
        final List<SearchableDoc> addedDocs = new java.util.ArrayList<>();
        String addedTopic;

        @Override
        public SearchResponse search(SearchRequest request) {
            lastSearchRequest = request;
            return new SearchResponse();
        }

        @Override
        public SearchableDoc getDoc(String docId) {
            return null;
        }

        @Override
        public List<SearchableDoc> getDocsByTerm(String topic, String term) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, List<String>> analyzeDoc(SearchableDoc doc) {
            return new HashMap<>();
        }

        @Override
        public List<String> analyzeQuery(String query) {
            return Collections.emptyList();
        }

        @Override
        public void refreshBlocking(String topic) {
            // no-op
        }

        @Override
        public void addDocs(String topic, List<SearchableDoc> docs) {
            addedTopic = topic;
            addedDocs.addAll(docs);
        }

        @Override
        public void removeDocs(String topic, List<String> docIds) {
            // no-op
        }

        @Override
        public void removeTopic(String topic) {
            // no-op
        }
    }

    private static SearchEngineBizModel newBizModel(ISearchEngine engine) {
        SearchEngineBizModel bizModel = new SearchEngineBizModel();
        bizModel.setSearchEngine(engine);
        return bizModel;
    }

    @Test
    public void testEmptyPatternFallsBackToDefaultIndexableFilter(@TempDir Path dir) throws Exception {
        SearchEngineBizModel bizModel = new SearchEngineBizModel();

        java.util.function.BiPredicate<String, File> filter = bizModel.getFilter(null);
        assertNotNull(filter, "null pattern yields the default filter");
        // NOTE(product finding): the default filter keys on the File's own
        // extension (isDefaultIndexable ignores the path argument), so the
        // file handle's extension drives the verdict.
        File javaFile = Files.write(dir.resolve("A.java"), "class A {}".getBytes()).toFile();
        File exeFile = Files.write(dir.resolve("B.exe"), "binary".getBytes()).toFile();
        assertTrue(filter.test("A.java", javaFile), "default filter accepts .java files");
        assertFalse(filter.test("A.exe", exeFile), "default filter rejects non-indexable extensions");

        assertNotNull(bizModel.getFilter(""), "empty pattern behaves like null");
    }

    @Test
    public void testRegexPatternDrivesFullPathMatching(@TempDir Path dir) throws Exception {
        SearchEngineBizModel bizModel = new SearchEngineBizModel();
        java.util.function.BiPredicate<String, File> filter = bizModel.getFilter("docs/.+\\.md");

        File anyFile = Files.write(dir.resolve("x.md"), "# x".getBytes()).toFile();
        assertTrue(filter.test("docs/readme.md", anyFile), "regex matches the whole relative path");
        assertFalse(filter.test("other/readme.md", anyFile), "paths outside docs/ are rejected");
        assertFalse(filter.test("docs/readme.java", anyFile), "extension must match the pattern too");
    }

    @Test
    public void testLocalFileResolvesThroughResourceLocator(@TempDir Path dir) throws Exception {
        SearchEngineBizModel bizModel = new SearchEngineBizModel();
        File target = Files.write(dir.resolve("doc.md"), "# doc".getBytes()).toFile();
        bizModel.setResourceLocator(path -> new FileResource(target));

        assertSame(target, bizModel.getLocalFile("whatever.md"),
                "the locator's resource file wins when a locator is registered");
    }

    @Test
    public void testLocalFileFailsLoudlyWhenLocatorCannotResolve() {
        SearchEngineBizModel bizModel = new SearchEngineBizModel();
        bizModel.setResourceLocator(path -> null);

        // NOTE(product finding): the guard `if (file == null)` in
        // getLocalFile is dead code — getResource(...).toFile() NPEs before
        // it. We pin only the fail-loud face (RuntimeException), not the
        // exception type, so the intended IllegalArgumentException can land
        // without a test change.
        assertThrows(RuntimeException.class,
                () -> bizModel.getLocalFile("nope.md"),
                "an unresolvable path must fail loudly, never return null");
    }

    @Test
    public void testSearchAsyncDelegatesToEngine() throws Exception {
        StubEngine engine = new StubEngine();
        SearchEngineBizModel bizModel = newBizModel(engine);

        SearchRequest request = new SearchRequest();
        request.setTopic("docs");
        java.util.concurrent.CompletionStage<SearchResponse> future = bizModel.searchAsync(request);
        assertNotNull(future);
        future.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertSame(request, engine.lastSearchRequest, "searchAsync forwards the request untouched");
    }

    @Test
    public void testAddDocDelegatesToEngine() {
        StubEngine engine = new StubEngine();
        SearchEngineBizModel bizModel = newBizModel(engine);

        SearchableDoc doc = new SearchableDoc();
        doc.setId("d1");
        bizModel.addDoc("topics", doc);

        assertEquals("topics", engine.addedTopic);
        assertSame(doc, engine.addedDocs.get(0), "addDoc reaches the engine as a single-doc batch");
    }

    @Test
    public void testEnhancerAndLocatorAccessorsRoundTrip() {
        SearchEngineBizModel bizModel = new SearchEngineBizModel();
        org.junit.jupiter.api.Assertions.assertNull(bizModel.getResourceLocator(),
                "no locator registered by default");

        io.nop.search.core.index.ISearchableDocEnhancer enhancer = (baseDir, doc) -> {
        };
        bizModel.setSearchableDocEnhancer(enhancer);
        assertSame(enhancer, bizModel.getSearchableDocEnhancer(),
                "enhancer setter/getter round-trips");

        io.nop.core.resource.IResourceLocator locator = path -> null;
        bizModel.setResourceLocator(locator);
        assertSame(locator, bizModel.getResourceLocator());
    }
}
