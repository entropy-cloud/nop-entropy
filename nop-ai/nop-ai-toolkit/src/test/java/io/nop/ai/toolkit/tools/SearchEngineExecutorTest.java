package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchType;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(testBeansFile = "/nop/ai/beans/test-mock.beans.xml")
public class SearchEngineExecutorTest extends JunitBaseTestCase {

    private SearchEngineExecutor executor;
    private MockSearchEngine mockSearchEngine;

    @BeforeEach
    public void setUp() {
        executor = new SearchEngineExecutor();
        mockSearchEngine = new MockSearchEngine();
        executor.setSearchEngine(mockSearchEngine);
    }

    @Test
    public void testToolName() {
        assertEquals("search-engine", executor.getToolName());
    }

    @Test
    public void testExecuteWithoutSearchEngine() {
        executor.setSearchEngine(null);
        AiToolCall call = createCall("test query");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Search engine not available"));
    }

    @Test
    public void testExecuteWithEmptyQuery() {
        AiToolCall call = createCall("");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Search query (input) is required"));
    }

    @Test
    public void testExecuteSuccess() {
        mockSearchEngine.addHit("Test Result", "This is a test snippet", 0.95f);
        AiToolCall call = createCall("test");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Test Result"));
        assertTrue(result.getOutput().getBody().contains("0.95"));
    }

    @Test
    public void testExecuteWithCollections() {
        mockSearchEngine.addHit("Doc1", "Content", 0.8f);
        XNode node = XNode.make("search-engine");
        node.setAttr("id", "1");
        XNode input = node.makeChild("input");
        input.setContentValue("query");
        XNode scope = node.makeChild("scope");
        XNode col1 = XNode.make("collection");
        col1.setAttr("name", "docs");
        scope.appendChild(col1);
        XNode col2 = XNode.make("collection");
        col2.setAttr("name", "wiki");
        scope.appendChild(col2);
        
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        SearchRequest req = mockSearchEngine.getLastRequest();
        assertNotNull(req.getTags(), "Tags should not be null");
        assertTrue(req.getTags().contains("docs"), "Tags should contain 'docs'");
        assertTrue(req.getTags().contains("wiki"), "Tags should contain 'wiki'");
    }

    @Test
    public void testExecuteWithMaxResults() {
        mockSearchEngine.addHit("Result", "Content", 0.9f);
        XNode node = XNode.make("search-engine");
        node.setAttr("id", "1");
        node.setAttr("maxResults", "5");
        XNode input = node.makeChild("input");
        input.setContentValue("test");
        
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertEquals(5, mockSearchEngine.getLastRequest().getLimit());
    }

    @Test
    public void testExecuteWithSearchType() {
        mockSearchEngine.addHit("Result", "Content", 0.9f);
        XNode node = XNode.make("search-engine");
        node.setAttr("id", "1");
        node.setAttr("mode", "vector");
        XNode input = node.makeChild("input");
        input.setContentValue("test");
        
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertEquals(SearchType.VECTOR, mockSearchEngine.getLastRequest().getSearchType());
    }

    @Test
    public void testExecuteException() {
        mockSearchEngine.setException(new RuntimeException("Connection failed"));
        AiToolCall call = createCall("test");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Connection failed"));
    }

    private AiToolCall createCall(String query) {
        XNode node = XNode.make("search-engine");
        node.setAttr("id", "1");
        XNode input = node.makeChild("input");
        input.setContentValue(query);
        return AiToolCall.fromNode(node);
    }

    static class MockContext implements IToolExecuteContext {
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return null; }

        @Override
        public IThreadPoolExecutor getExecutor() {
            return SyncThreadPoolExecutor.INSTANCE;
        }
    }

    static class MockSearchEngine implements ISearchEngine {
        private final List<SearchHit> hits = new ArrayList<>();
        private SearchRequest lastRequest;
        private RuntimeException exception;

        void addHit(String title, String snippet, float score) {
            SearchHit hit = new SearchHit();
            hit.setTitle(title);
            hit.setSummary(snippet);
            hit.setScore(score);
            hit.setPath("/test/" + title);
            hits.add(hit);
        }

        void setException(RuntimeException e) {
            this.exception = e;
        }

        SearchRequest getLastRequest() { return lastRequest; }

        @Override
        public CompletionStage<SearchResponse> searchAsync(SearchRequest request) {
            this.lastRequest = request;
            if (exception != null) {
                return CompletableFuture.failedFuture(exception);
            }
            SearchResponse response = new SearchResponse();
            response.setItems(hits);
            response.setTotal(hits.size());
            response.setProcessTime(10);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public SearchResponse search(SearchRequest request) {
            this.lastRequest = request;
            if (exception != null) throw exception;
            SearchResponse response = new SearchResponse();
            response.setItems(hits);
            response.setTotal(hits.size());
            return response;
        }

        @Override
        public SearchableDoc getDoc(String docId) { return null; }

        @Override
        public List<SearchableDoc> getDocsByTerm(String topic, String term) { return Collections.emptyList(); }

        @Override
        public Map<String, List<String>> analyzeDoc(SearchableDoc doc) { return Collections.emptyMap(); }

        @Override
        public List<String> analyzeQuery(String query) { return Collections.emptyList(); }

        @Override
        public void refreshBlocking(String topic) {}

        @Override
        public void addDocs(String topic, List<SearchableDoc> docs) {}

        @Override
        public void removeDocs(String topic, List<String> docIds) {}

        @Override
        public void removeTopic(String topic) {}
    }
}
