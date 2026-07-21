package io.nop.metadata.service.search;

import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestNopMetaSearchService {

    @Mock
    ISearchEngine searchEngine;

    NopMetaSearchService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new NopMetaSearchService();
        service.searchEngine = searchEngine;
    }

    @Test
    void testAddToIndex() {
        SearchableDoc doc = new SearchableDoc();
        doc.setId("test-id");
        doc.setName("test-name");
        doc.setTitle("test-title");

        service.addToIndex("TestEntity", "test-id", doc);

        verify(searchEngine).addDoc(eq("nop-meta-metadata"), eq(doc));
    }

    @Test
    void testRemoveFromIndex() {
        service.removeFromIndex("TestEntity", "test-id");

        verify(searchEngine).removeDocs(eq("nop-meta-metadata"), eq(List.of("test-id")));
    }

    @Test
    void testAddToIndex_engineNull() {
        service.searchEngine = null;
        SearchableDoc doc = new SearchableDoc();
        doc.setId("test-id");

        service.addToIndex("TestEntity", "test-id", doc);
    }

    @Test
    void testRemoveFromIndex_engineNull() {
        service.searchEngine = null;

        service.removeFromIndex("TestEntity", "test-id");
    }

    @Test
    void testAddToIndex_engineThrows() {
        SearchableDoc doc = new SearchableDoc();
        doc.setId("test-id");
        doThrow(new RuntimeException("engine error")).when(searchEngine).addDoc(anyString(), any());

        service.addToIndex("TestEntity", "test-id", doc);
    }

    @Test
    void testRemoveFromIndex_engineThrows() {
        doThrow(new RuntimeException("engine error")).when(searchEngine).removeDocs(anyString(), anyList());

        service.removeFromIndex("TestEntity", "test-id");
    }
}
