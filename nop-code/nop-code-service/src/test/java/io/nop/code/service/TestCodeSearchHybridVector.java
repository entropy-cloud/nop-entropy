package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N4.3: searchType=VECTOR/HYBRID 贯通到 nop-search 引擎（RRF）的端到端验证。
 *
 * <p>测试类路径经 aaa- 前缀 autoconfig 注册确定性 {@link StubTestTextEmbedding}
 * （nop-code-service 生产面无 nop-ai 依赖）——索引侧 autoGenerateEmbedding 与查询侧
 * parseQueryVector 同源同词项语义，VECTOR/HYBRID 的向量腿真实参与检索：
 * "gallop" 查询的词项向量与含 gallop 的 doc 向量同桶，必须命中 {@code Zebra.gallop}。
 * matchType=SEARCH_ENGINE 仍是引擎路径的唯一判别（对照 DB-LIKE 降级）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
@NopTestProperty(name = "nop.search.index-dir", value = "./target/nop-code-search-hybrid-test-indices")
public class TestCodeSearchHybridVector extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @TempDir
    Path tempDir;

    private static final String ZEBRA = """
            package demo;

            public class Zebra {
                public String gallop(int speed) { return "fast"; }
            }
            """;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchCode(String indexId, String query, String searchType) {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("query", query);
        data.put("searchType", searchType);
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);
        assertTrue(response.isOk(), "searchCode should succeed, got: " + response.getMsg());
        return (List<Map<String, Object>>) response.getData();
    }

    private ApiResponse<?> rpcQuery(String operation, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operation, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    /**
     * Wiring evidence: the stub embedding bean must be by-type injected into the
     * Lucene engine (test-classpath production mechanism identical to N4.2's
     * AiModelTextEmbedding wiring).
     */
    @Test
    void testEmbeddingBeanWiredIntoEngine() throws Exception {
        Field engineField = io.nop.code.service.impl.CodeIndexService.class.getDeclaredField("searchEngine");
        engineField.setAccessible(true);
        Object luceneEngine = engineField.get(codeIndexService);
        assertNotNull(luceneEngine, "engine must be wired");

        Field embeddingField = luceneEngine.getClass().getDeclaredField("textEmbedding");
        embeddingField.setAccessible(true);
        Object embedding = embeddingField.get(luceneEngine);
        assertNotNull(embedding, "test embedding bean must be injected into the engine");
        assertInstanceOf(StubTestTextEmbedding.class, embedding);
    }

    /**
     * VECTOR path: query tokens shared with the indexed symbol produce same-bucket
     * vectors — the vector leg must rank the gallop symbol first.
     */
    @Test
    void testVectorSearchFindsSymbolByTokenVector() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("demo"));
        Files.writeString(src.resolve("demo/Zebra.java"), ZEBRA);

        String indexId = "n43_vector_e2e";
        assertTrue(codeIndexService.indexDirectory(indexId, src.toString(), null) >= 1);

        List<Map<String, Object>> hits = searchCode(indexId, "gallop", "VECTOR");
        assertFalse(hits.isEmpty(), "vector search must return hits (stub embedding on both sides)");
        assertTrue(hits.stream().allMatch(h -> "SEARCH_ENGINE".equals(h.get("matchType"))),
                "all hits must come from the search engine, got: " + hits);
        assertEquals("gallop",
                String.valueOf(hits.get(0).get("matchedSymbolName")),
                "the gallop symbol must rank first by its own token vector, got: " + hits);

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * HYBRID path: text leg (BM25) + vector leg (kNN) fused by RRF — both legs alive,
     * result must still be engine-backed and rank the gallop symbol first.
     */
    @Test
    void testHybridSearchFusesTextAndVectorLegs() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("demo"));
        Files.writeString(src.resolve("demo/Zebra.java"), ZEBRA);

        String indexId = "n43_hybrid_e2e";
        assertTrue(codeIndexService.indexDirectory(indexId, src.toString(), null) >= 1);

        List<Map<String, Object>> hits = searchCode(indexId, "gallop", "HYBRID");
        assertFalse(hits.isEmpty(), "hybrid search must return hits");
        assertTrue(hits.stream().allMatch(h -> "SEARCH_ENGINE".equals(h.get("matchType"))),
                "all hits must come from the search engine, got: " + hits);
        assertEquals("gallop",
                String.valueOf(hits.get(0).get("matchedSymbolName")),
                "gallop symbol must rank first in the RRF-fused result, got: " + hits);

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Control: legacy TEXT behavior unchanged by the mapping (zero regression).
     */
    @Test
    void testTextSearchStillWorks() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("demo"));
        Files.writeString(src.resolve("demo/Zebra.java"), ZEBRA);

        String indexId = "n43_text_ctrl";
        assertTrue(codeIndexService.indexDirectory(indexId, src.toString(), null) >= 1);

        List<Map<String, Object>> hits = searchCode(indexId, "gallop", "TEXT");
        assertFalse(hits.isEmpty(), "text search must keep working");
        assertTrue(hits.stream().allMatch(h -> "SEARCH_ENGINE".equals(h.get("matchType"))),
                "text search must stay engine-backed, got: " + hits);

        List<Map<String, Object>> legacy = searchCode(indexId, "gallop", "COMBINED");
        assertFalse(legacy.isEmpty(), "legacy searchType values must still return results");
        assertTrue(legacy.stream().allMatch(h -> "SEARCH_ENGINE".equals(h.get("matchType"))),
                "legacy values normalize to engine TEXT path when engine present, got: " + legacy);

        codeIndexService.deleteIndex(indexId);
    }
}
