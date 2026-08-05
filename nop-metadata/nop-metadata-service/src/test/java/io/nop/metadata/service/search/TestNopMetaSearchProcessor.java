package io.nop.metadata.service.search;


import io.nop.metadata.service.NopMetadataErrors;
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

class TestNopMetaSearchProcessor {

    @Mock
    ISearchEngine searchEngine;

    NopMetaSearchProcessor service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new NopMetaSearchProcessor();
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

        verify(searchEngine).addDoc(eq(NopMetaSearchProcessor.TOPIC), eq(doc));
    }

    @Test
    void testRemoveFromIndex() {
        service.removeFromIndex("TestEntity", "test-id");

        verify(searchEngine).removeDocs(eq(NopMetaSearchProcessor.TOPIC), eq(List.of("test-id")));
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

    // ===== fail-close (default): exceptions propagate with cause chain =====

    @Test
    void testAddToIndex_engineThrows_failClose() {
        service.setSearchIndexFailOpen(false);
        SearchableDoc doc = new SearchableDoc();
        doc.setId("test-id");
        RuntimeException cause = new RuntimeException("engine error");
        doThrow(cause).when(searchEngine).addDoc(anyString(), any());

        NopMetadataException ex = assertThrows(NopMetadataException.class,
                () -> service.addToIndex("TestEntity", "test-id", doc));

        assertSame(cause, ex.getCause(),
                "fail-closed exception must preserve the original engine cause (P2-01)");
        assertEquals(NopMetadataErrors.ERR_SEARCH_INDEX_ADD_FAILED.getErrorCode(), ex.getErrorCode(),
                "fail-closed exception must carry ERR_SEARCH_INDEX_ADD_FAILED error code");
    }

    @Test
    void testRemoveFromIndex_engineThrows_failClose() {
        service.setSearchIndexFailOpen(false);
        RuntimeException cause = new RuntimeException("engine error");
        doThrow(cause).when(searchEngine).removeDocs(anyString(), anyList());

        NopMetadataException ex = assertThrows(NopMetadataException.class,
                () -> service.removeFromIndex("TestEntity", "test-id"));

        assertSame(cause, ex.getCause(),
                "fail-closed exception must preserve the original engine cause (P2-01)");
        assertEquals(NopMetadataErrors.ERR_SEARCH_INDEX_REMOVE_FAILED.getErrorCode(), ex.getErrorCode(),
                "fail-closed exception must carry ERR_SEARCH_INDEX_REMOVE_FAILED error code");
    }

    // ===== fail-close: LOG.error must be emitted with root cause =====

    @Test
    void testAddToIndex_engineThrows_failClose_logsError() {
        service.setSearchIndexFailOpen(false);
        SearchableDoc doc = new SearchableDoc();
        doc.setId("test-id");
        doThrow(new RuntimeException("engine error")).when(searchEngine).addDoc(anyString(), any());

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(NopMetaSearchProcessor.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(NopMetadataException.class,
                    () -> service.addToIndex("TestEntity", "test-id", doc));

            boolean errorLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.ERROR
                            && e.getFormattedMessage().contains("addToIndex failed for entityType=TestEntity"));
            assertTrue(errorLogged,
                    "fail-closed path must emit LOG.error before rethrowing (P2-01), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void testRemoveFromIndex_engineThrows_failClose_logsError() {
        service.setSearchIndexFailOpen(false);
        doThrow(new RuntimeException("engine error")).when(searchEngine).removeDocs(anyString(), anyList());

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(NopMetaSearchProcessor.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(NopMetadataException.class,
                    () -> service.removeFromIndex("TestEntity", "test-id"));

            boolean errorLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.ERROR
                            && e.getFormattedMessage().contains("removeFromIndex failed for entityType=TestEntity"));
            assertTrue(errorLogged,
                    "fail-closed path must emit LOG.error before rethrowing (P2-01), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));
        } finally {
            logger.detachAppender(appender);
        }
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
        NopMetaSearchProcessor fresh = new NopMetaSearchProcessor();
        assertFalse(fresh.isSearchIndexFailOpen(), "default fail-close");
    }
}
