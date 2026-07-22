package io.nop.metadata.service.search;

import io.nop.metadata.service.NopMetadataException;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
        // Default: fail-close = false (propagate exceptions)
        service.setSearchIndexFailOpen(false);
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

    // ===== fail-close (default): exceptions propagate =====

    @Test
    void testAddToIndex_engineThrows_failClose() {
        service.setSearchIndexFailOpen(false);
        SearchableDoc doc = new SearchableDoc();
        doc.setId("test-id");
        doThrow(new RuntimeException("engine error")).when(searchEngine).addDoc(anyString(), any());

        assertThrows(NopMetadataException.class,
                () -> service.addToIndex("TestEntity", "test-id", doc));
    }

    @Test
    void testRemoveFromIndex_engineThrows_failClose() {
        service.setSearchIndexFailOpen(false);
        doThrow(new RuntimeException("engine error")).when(searchEngine).removeDocs(anyString(), anyList());

        assertThrows(NopMetadataException.class,
                () -> service.removeFromIndex("TestEntity", "test-id"));
    }

    // ===== fail-open: exceptions logged, not propagated =====

    @Test
    void testAddToIndex_engineThrows_failOpen() {
        service.setSearchIndexFailOpen(true);
        SearchableDoc doc = new SearchableDoc();
        doc.setId("test-id");
        doThrow(new RuntimeException("engine error")).when(searchEngine).addDoc(anyString(), any());

        assertDoesNotThrow(() ->
                service.addToIndex("TestEntity", "test-id", doc));
    }

    @Test
    void testRemoveFromIndex_engineThrows_failOpen() {
        service.setSearchIndexFailOpen(true);
        doThrow(new RuntimeException("engine error")).when(searchEngine).removeDocs(anyString(), anyList());

        assertDoesNotThrow(() ->
                service.removeFromIndex("TestEntity", "test-id"));
    }

    @Test
    void testSearchIndexFailOpen_defaultIsFalse() {
        NopMetaSearchService fresh = new NopMetaSearchService();
        assertFalse(fresh.isSearchIndexFailOpen(), "default fail-close");
    }
}
