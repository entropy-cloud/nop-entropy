/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search;

import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchType;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test vector search functionality
 */
@NopTestProperty(name = "nop.search.index-dir", value = "./target")
public class TestVectorSearch extends JunitBaseTestCase {

    @Inject
    ISearchEngine searchEngine;

    @Test
    public void testTextSearch() {
        searchEngine.removeTopic("testVec");

        // Add document
        SearchableDoc doc = new SearchableDoc();
        doc.setId("doc1");
        doc.setName("doc1");
        doc.setTitle("Machine Learning Tutorial");
        doc.setContent("This is a comprehensive tutorial about machine learning algorithms and techniques.");
        doc.setStoreContent(true);

        searchEngine.addDoc("testVec", doc);

        // Search with TEXT type
        SearchRequest request = new SearchRequest();
        request.setTopic("testVec");
        request.setQuery("machine learning");
        request.setSearchType(SearchType.TEXT);
        request.setLimit(10);

        SearchResponse response = searchEngine.search(request);
        assertEquals(1, response.getTotal());
        assertNotNull(response.getItems());
        assertEquals(1, response.getItems().size());
        assertNotNull(response.getItems().get(0));
        assertEquals("doc1", response.getItems().get(0).getId());

        searchEngine.removeTopic("testVec");
    }

    @Test
    public void testVectorSearch() {
        searchEngine.removeTopic("testVec");

        // Add document with embedding
        SearchableDoc doc = new SearchableDoc();
        doc.setId("doc2");
        doc.setName("doc2");
        doc.setTitle("Deep Learning Guide");
        doc.setContent("Deep learning is a subset of machine learning.");
        doc.setStoreContent(true);
        
        // Set a sample embedding vector (normalized)
        float[] embedding = new float[768];
        for (int i = 0; i < 768; i++) {
            embedding[i] = (float) Math.random();
        }
        // Normalize
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < 768; i++) {
            embedding[i] /= norm;
        }
        doc.setEmbedding(embedding);

        searchEngine.addDoc("testVec", doc);

        // VECTOR search should now work
        SearchRequest request = new SearchRequest();
        request.setTopic("testVec");
        request.setQuery("deep learning");
        request.setSearchType(SearchType.VECTOR);
        request.setLimit(10);
        request.setSimilarityThreshold(0.0);

        // Should not throw exception now
        SearchResponse response = searchEngine.search(request);
        assertNotNull(response);
        // May return results or empty list depending on vector similarity
        
        searchEngine.removeTopic("testVec");
    }

    @Test
    public void testHybridSearch() {
        searchEngine.removeTopic("testHyb");

        // Add document with embedding
        SearchableDoc doc = new SearchableDoc();
        doc.setId("doc3");
        doc.setName("doc3");
        doc.setTitle("AI and Neural Networks");
        doc.setContent("Artificial intelligence and neural networks are transforming technology.");
        doc.setStoreContent(true);
        
        // Set embedding
        float[] embedding = new float[768];
        for (int i = 0; i < 768; i++) {
            embedding[i] = (float) Math.random();
        }
        // Normalize
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < 768; i++) {
            embedding[i] /= norm;
        }
        doc.setEmbedding(embedding);

        searchEngine.addDoc("testHyb", doc);

        // HYBRID search (should not throw exception now)
        SearchRequest request = new SearchRequest();
        request.setTopic("testHyb");
        request.setQuery("artificial intelligence");
        request.setSearchType(SearchType.HYBRID);
        request.setLimit(10);
        request.setSimilarityThreshold(0.0);

        // Should not throw exception now
        SearchResponse response = searchEngine.search(request);
        assertNotNull(response);
        
        searchEngine.removeTopic("testHyb");
    }

    @Test
    public void testSearchTypeDefault() {
        searchEngine.removeTopic("testDef");

        // Add document
        SearchableDoc doc = new SearchableDoc();
        doc.setId("doc4");
        doc.setName("doc4");
        doc.setTitle("Default Search Test");
        doc.setContent("Testing default search type behavior.");
        doc.setStoreContent(true);

        searchEngine.addDoc("testDef", doc);

        // Search without setting searchType (should default to TEXT)
        SearchRequest request = new SearchRequest();
        request.setTopic("testDef");
        request.setQuery("search test");
        request.setLimit(10);

        SearchResponse response = searchEngine.search(request);
        assertEquals(1, response.getTotal());
        assertNotNull(response.getItems());
        assertEquals(1, response.getItems().size());

        searchEngine.removeTopic("testDef");
    }

    @Test
    public void testEmbeddingField() {
        SearchableDoc doc = new SearchableDoc();
        doc.setId("testEmb");
        
        // Test embedding field
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
        doc.setEmbedding(embedding);
        
        assertArrayEquals(embedding, doc.getEmbedding());
        
        // Test autoGenerateEmbedding field
        doc.setAutoGenerateEmbedding(true);
        assertTrue(doc.isAutoGenerateEmbedding());
        
        doc.setAutoGenerateEmbedding(false);
        assertFalse(doc.isAutoGenerateEmbedding());
    }
}
