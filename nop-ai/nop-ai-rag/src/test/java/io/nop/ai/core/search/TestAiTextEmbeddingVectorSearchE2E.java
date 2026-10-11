package io.nop.ai.core.search;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchType;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N4.2（plan nop-code/23）Phase 2 引擎端到端（Anti-Hollow）：addDocs
 * （autoGenerateEmbedding=true）→ 桥接嵌入真实写入 Lucene 向量字段 →
 * SearchType.VECTOR 文本查询经同一桥接生成查询向量 → 命中预期文档。
 * indexDir 显式覆盖（N4.1 实证陷阱：默认值解析到文件系统根）。
 */
class TestAiTextEmbeddingVectorSearchE2E {

    private static final String TOPIC = "n42-embed-e2e";

    @BeforeAll
    static void init() {
        AppConfig.getConfigProvider().assignConfigValue("nop.search.index-dir", "./target/n42-embed-e2e");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.merged-beans-file.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.auto-config.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans.files",
                "/nop/ai/beans/n42-test-embedding.beans.xml,"
                        + "/nop/search/beans/search-defaults.beans.xml");
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        ISearchEngine engine = BeanContainer.instance().getBeanByType(ISearchEngine.class);
        if (engine != null) {
            engine.removeTopic(TOPIC);
        }
        CoreInitialization.destroy();
        AppConfig.getConfigProvider().assignConfigValue("nop.search.index-dir", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.merged-beans-file.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.auto-config.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans.files", null);
    }

    @Test
    void autoEmbeddedDocsAreReachableByVectorTextQuery() {
        ISearchEngine engine = BeanContainer.instance().getBeanByType(ISearchEngine.class);
        assertNotNull(engine);
        engine.removeTopic(TOPIC);

        StubEmbeddingModel model = (StubEmbeddingModel) BeanContainer.instance()
                .getBean("nopAiEmbeddingModel");
        int callsBefore = model.embedCalls.get() + model.embedAllCalls.get();

        SearchableDoc doc1 = new SearchableDoc();
        doc1.setId("n42-ml");
        doc1.setName("n42-ml");
        doc1.setTitle("Machine Learning Tutorial");
        doc1.setContent("A comprehensive machine learning algorithms tutorial.");
        doc1.setStoreContent(true);
        doc1.setAutoGenerateEmbedding(true);

        SearchableDoc doc2 = new SearchableDoc();
        doc2.setId("n42-dl");
        doc2.setName("n42-dl");
        doc2.setTitle("Deep Learning Guide");
        doc2.setContent("A deep learning neural networks guide.");
        doc2.setStoreContent(true);
        doc2.setAutoGenerateEmbedding(true);

        engine.addDocs(TOPIC, List.of(doc1, doc2));

        int callsAfterIndex = model.embedCalls.get() + model.embedAllCalls.get();
        assertTrue(callsAfterIndex > callsBefore,
                "addDocs with autoGenerateEmbedding must route through the bridged embedding model "
                        + "(before=" + callsBefore + ", after=" + callsAfterIndex + ")");

        SearchRequest request = new SearchRequest();
        request.setTopic(TOPIC);
        request.setQuery("machine learning");
        request.setSearchType(SearchType.VECTOR);
        request.setLimit(10);

        SearchResponse response = engine.search(request);
        assertNotNull(response);
        assertNotNull(response.getItems());
        // kNN 返回全部近邻（无距离过滤），断言 rank-1 排序：query 向量 e0 与 machine 文档距离 0
        assertTrue(!response.getItems().isEmpty(), "vector query must return hits");
        SearchHit hit = response.getItems().get(0);
        assertEquals("n42-ml", hit.getId(),
                "machine-learning doc must rank first for its own query vector");

        SearchRequest deepRequest = new SearchRequest();
        deepRequest.setTopic(TOPIC);
        deepRequest.setQuery("deep learning");
        deepRequest.setSearchType(SearchType.VECTOR);
        deepRequest.setLimit(10);
        SearchResponse deepResponse = engine.search(deepRequest);
        assertTrue(!deepResponse.getItems().isEmpty());
        assertEquals("n42-dl", deepResponse.getItems().get(0).getId(),
                "deep-learning doc must rank first for its own query vector");

        engine.removeTopic(TOPIC);
    }
}
