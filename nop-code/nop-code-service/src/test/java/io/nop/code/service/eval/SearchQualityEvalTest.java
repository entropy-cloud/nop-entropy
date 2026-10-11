package io.nop.code.service.eval;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N8.1 搜索质量评测（真实链路，P1-7.4-002 / plan 2402）：corpus 经生产
 * {@link ISearchEngine}（Lucene）索引与检索，MRR 由真实命中的 docId 计算——
 * 不再使用测试内自实现的 token 评分器。
 * <p>
 * <b>边界</b>：语料为 10 条确定性短文档、10 条 ground-truth 查询；指标用于验证
 * 真实检索链的排序行为，不代表生产语料规模下的全量质量结论。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
@NopTestProperty(name = "nop.search.index-dir", value = "./target/nop-code-search-quality-eval")
public class SearchQualityEvalTest extends JunitAutoTestCase {

    private static final String TOPIC = "search-quality-eval";

    @Inject
    ISearchEngine searchEngine;

    private static final Map<String, String> DOCS = new LinkedHashMap<>();

    private static final Map<String, String> GROUND_TRUTH = new LinkedHashMap<>();

    static {
        DOCS.put("doc-orm", "ORM entity model mapping database schema table column relation CRUD");
        DOCS.put("doc-graphql", "GraphQL API query mutation schema type resolver endpoint");
        DOCS.put("doc-ioc", "IoC container bean dependency injection lifecycle configuration");
        DOCS.put("doc-delta", "Delta customization merge overlay base custom delta layer");
        DOCS.put("doc-wf", "Workflow engine process definition task approval transition state");
        DOCS.put("doc-task", "Task scheduler batch job execution quartz cron trigger");
        DOCS.put("doc-xlang", "XLang script expression template transform code");
        DOCS.put("doc-codegen", "Code generation template generator Java source output");
        DOCS.put("doc-security", "Security authentication authorization permission role user login");
        DOCS.put("doc-stream", "Stream processing data flow window aggregation streaming event");

        GROUND_TRUTH.put("ORM entity model database", "doc-orm");
        GROUND_TRUTH.put("GraphQL API schema query", "doc-graphql");
        GROUND_TRUTH.put("IoC container bean inject", "doc-ioc");
        GROUND_TRUTH.put("Delta merge overlay custom", "doc-delta");
        GROUND_TRUTH.put("Workflow process approval task", "doc-wf");
        GROUND_TRUTH.put("Task scheduler batch quartz", "doc-task");
        GROUND_TRUTH.put("XLang script expression", "doc-xlang");
        GROUND_TRUTH.put("Code generation template output", "doc-codegen");
        GROUND_TRUTH.put("Security auth permission login", "doc-security");
        GROUND_TRUTH.put("Stream window aggregation event", "doc-stream");
    }

    @BeforeEach
    void seedIndex() {
        searchEngine.removeTopic(TOPIC);
        List<SearchableDoc> docs = new ArrayList<>();
        for (Map.Entry<String, String> entry : DOCS.entrySet()) {
            SearchableDoc doc = new SearchableDoc();
            doc.setId(entry.getKey());
            doc.setName(entry.getKey());
            doc.setTitle(entry.getKey());
            doc.setContent(entry.getValue());
            docs.add(doc);
        }
        searchEngine.addDocs(TOPIC, docs);
        searchEngine.refreshBlocking(TOPIC);
    }

    @Test
    void searchQualityMrrAtLeastHalf() {
        // The eval must run against the production Lucene engine, not a test-local scorer.
        assertInstanceOf(io.nop.search.lucene.LuceneSearchEngine.class, searchEngine,
                "search quality eval must use the production Lucene engine bean");

        double totalMrr = 0;
        int queries = 0;
        for (Map.Entry<String, String> entry : GROUND_TRUTH.entrySet()) {
            SearchRequest request = new SearchRequest();
            request.setTopic(TOPIC);
            request.setQuery(entry.getKey());
            request.setLimit(10);
            SearchResponse response = searchEngine.search(request);
            List<SearchHit> hits = response.getItems();
            assertNotNull(hits, "real engine must return a hit list for query: " + entry.getKey());
            assertFalse(hits.isEmpty(), "real engine must return hits for query: " + entry.getKey());
            double mrr = 0;
            for (int i = 0; i < hits.size(); i++) {
                if (entry.getValue().equals(hits.get(i).getId())) {
                    mrr = 1.0 / (i + 1);
                    break;
                }
            }
            totalMrr += mrr;
            queries++;
            System.out.println("  " + entry.getKey() + " → MRR=" + mrr);
        }
        double avgMrr = totalMrr / queries;
        System.out.println("N8.1 Search Quality MRR (real Lucene chain) = " + avgMrr + " (" + queries + " queries)");
        assertTrue(avgMrr >= 0.5, "Search MRR must be >= 0.5, got: " + avgMrr);
    }
}
