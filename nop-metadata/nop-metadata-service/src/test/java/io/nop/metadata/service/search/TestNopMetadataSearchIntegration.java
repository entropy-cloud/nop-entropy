package io.nop.metadata.service.search;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.service.NopMetadataException;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.core.dto.IndexResult;
import io.nop.metadata.core.dto.SearchResultDTO;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestNopMetadataSearchIntegration {

    @Mock
    ISearchEngine searchEngine;

    @Mock
    io.nop.dao.api.IDaoProvider daoProvider;

    NopMetaIndexBuilder indexBuilder;
    NopMetaSearchService searchService;
    NopMetaSearchBizModel searchBiz;

    @Captor
    ArgumentCaptor<SearchRequest> requestCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        indexBuilder = new NopMetaIndexBuilder();
        indexBuilder.searchEngine = searchEngine;
        indexBuilder.daoProvider = daoProvider;

        searchService = new NopMetaSearchService();
        searchService.searchEngine = searchEngine;

        searchBiz = new NopMetaSearchBizModel();
        searchBiz.searchEngine = searchEngine;
    }

    @Test
    void testFullIndexAndSearchRoundTrip() {
        mockNonEmptyDaos();

        List<IndexResult> results = indexBuilder.buildFullIndex(null);
        assertNotNull(results);
        assertEquals(6, results.size());

        verify(searchEngine, times(6)).addDocs(eq("nop-meta-metadata"), anyList());
        verify(searchEngine, times(6)).refreshBlocking(eq("nop-meta-metadata"));
    }

    @Test
    void testSearchByEntityType() {
        SearchHit hit = new SearchHit();
        hit.setId("test-id");
        hit.setName("test-name");
        hit.setTitle("Test Title");
        hit.setSummary("Test summary");
        hit.setScore(0.95f);
        hit.setTags(Set.of("MetaTable"));

        SearchResponse response = new SearchResponse();
        response.setQuery("test");
        response.setLimit(20);
        response.setTotal(1);
        response.setItems(List.of(hit));
        response.setProcessTime(10);

        when(searchEngine.search(any(SearchRequest.class))).thenReturn(response);

        SearchResultDTO result = searchBiz.searchMetadata("test", "MetaTable", 10, null);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getItems().size());
        assertEquals("test-id", result.getItems().get(0).getId());
        assertEquals("MetaTable", result.getItems().get(0).getEntityType());

        verify(searchEngine).search(requestCaptor.capture());
        SearchRequest req = requestCaptor.getValue();
        assertEquals("nop-meta-metadata", req.getTopic());
        assertEquals(Set.of("MetaTable"), req.getTags());
        assertEquals(10, req.getLimit());
    }

    @Test
    void testSearchWithoutEntityType() {
        SearchResponse response = new SearchResponse();
        response.setQuery("keyword");
        response.setLimit(20);
        response.setTotal(0);
        response.setItems(Collections.emptyList());
        response.setProcessTime(5);

        when(searchEngine.search(any(SearchRequest.class))).thenReturn(response);

        SearchResultDTO result = searchBiz.searchMetadata("keyword", null, null, null);
        assertNotNull(result);
        assertEquals(0, result.getTotal());

        verify(searchEngine).search(requestCaptor.capture());
        SearchRequest req = requestCaptor.getValue();
        assertEquals("nop-meta-metadata", req.getTopic());
        assertNull(req.getTags());
        assertEquals(20, req.getLimit());
    }

    @Test
    void testSearchMetadata_engineNull() {
        searchBiz.searchEngine = null;
        assertThrows(NopException.class,
                () -> searchBiz.searchMetadata("test", null, null, null));
    }

    @Test
    void testRebuildSearchIndex_engineNull() {
        searchBiz.searchEngine = null;
        assertThrows(NopException.class,
                () -> searchBiz.rebuildSearchIndex(null, null));
    }

    @Test
    void testAddToIndexAndVerify() {
        SearchableDoc doc = new SearchableDoc();
        doc.setId("entity-1");
        doc.setName("entity-name");
        doc.setTitle("Entity Title");
        doc.setTagSet(Set.of("MetaEntity"));

        searchService.addToIndex("MetaEntity", "entity-1", doc);

        verify(searchEngine).addDoc(eq("nop-meta-metadata"), eq(doc));
    }

    @Test
    void testRemoveFromIndexAndVerify() {
        searchService.removeFromIndex("MetaEntity", "entity-1");

        verify(searchEngine).removeDocs(eq("nop-meta-metadata"), eq(List.of("entity-1")));
    }

    // ===== Phase 2: error-path coverage — fail-close (default) =====

    @Test
    void testAddToIndex_engineThrows_failClose() {
        searchService.setSearchIndexFailOpen(false);
        doThrow(new RuntimeException("search engine down"))
                .when(searchEngine).addDoc(anyString(), any());

        SearchableDoc doc = new SearchableDoc();
        doc.setId("e-err");
        doc.setName("err");
        doc.setTitle("Err");

        assertThrows(NopMetadataException.class,
                () -> searchService.addToIndex("MetaEntity", "e-err", doc));
    }

    @Test
    void testRemoveFromIndex_engineThrows_failClose() {
        searchService.setSearchIndexFailOpen(false);
        doThrow(new RuntimeException("search engine down"))
                .when(searchEngine).removeDocs(anyString(), anyList());

        assertThrows(NopMetadataException.class,
                () -> searchService.removeFromIndex("MetaEntity", "e-err"));
    }

    // ===== Phase 2: fail-open mode =====

    @Test
    void testAddToIndex_engineThrows_failOpen() {
        searchService.setSearchIndexFailOpen(true);
        doThrow(new RuntimeException("search engine down"))
                .when(searchEngine).addDoc(anyString(), any());

        SearchableDoc doc = new SearchableDoc();
        doc.setId("e-err");
        doc.setName("err");
        doc.setTitle("Err");

        assertDoesNotThrow(() ->
                searchService.addToIndex("MetaEntity", "e-err", doc));

        verify(searchEngine).addDoc(eq("nop-meta-metadata"), eq(doc));
    }

    @Test
    void testRemoveFromIndex_engineThrows_failOpen() {
        searchService.setSearchIndexFailOpen(true);
        doThrow(new RuntimeException("search engine down"))
                .when(searchEngine).removeDocs(anyString(), anyList());

        assertDoesNotThrow(() ->
                searchService.removeFromIndex("MetaEntity", "e-err"));

        verify(searchEngine).removeDocs(eq("nop-meta-metadata"), eq(List.of("e-err")));
    }

    @Test
    void testAddToIndex_engineNull_doesNotThrow() {
        searchService.searchEngine = null;
        SearchableDoc doc = new SearchableDoc();
        doc.setId("e-null");

        assertDoesNotThrow(() ->
                searchService.addToIndex("MetaEntity", "e-null", doc));
    }

    @Test
    void testRemoveFromIndex_engineNull_doesNotThrow() {
        searchService.searchEngine = null;

        assertDoesNotThrow(() ->
                searchService.removeFromIndex("MetaEntity", "e-null"));
    }

    // ===== Phase 1: GraphQL annotation verification =====

    @Test
    void testBizModelAnnotationPresent() {
        BizModel bizModel = NopMetaSearchBizModel.class.getAnnotation(BizModel.class);
        assertNotNull(bizModel, "NopMetaSearchBizModel should have @BizModel annotation");
        assertEquals("NopMetaSearch", bizModel.value());
    }

    @Test
    void testRebuildSearchIndexHasBizMutation() throws NoSuchMethodException {
        var method = NopMetaSearchBizModel.class.getMethod("rebuildSearchIndex", List.class, IServiceContext.class);
        BizMutation mutation = method.getAnnotation(BizMutation.class);
        assertNotNull(mutation, "rebuildSearchIndex should have @BizMutation annotation");
    }

    @Test
    void testSearchMetadataHasBizQuery() throws NoSuchMethodException {
        var method = NopMetaSearchBizModel.class.getMethod("searchMetadata", String.class, String.class, Integer.class, IServiceContext.class);
        BizQuery query = method.getAnnotation(BizQuery.class);
        assertNotNull(query, "searchMetadata should have @BizQuery annotation");
    }

    @SuppressWarnings("unchecked")
    private void mockEmptyDaos() {
        mockEmptyDao(NopMetaClassification.class);
        mockEmptyDao(NopMetaTag.class);
        mockEmptyDao(NopMetaGlossaryTerm.class);
        mockEmptyDao(NopMetaTable.class);
        mockEmptyDao(NopMetaEntity.class);
        mockEmptyDao(NopMetaEntityField.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockEmptyDao(Class entityClass) {
        IEntityDao dao = mock(IEntityDao.class);
        when(daoProvider.daoFor(entityClass)).thenReturn(dao);
        when(dao.findAll()).thenReturn(Collections.emptyList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockNonEmptyDaos() {
        NopMetaClassification c = new NopMetaClassification();
        c.setClassificationId("c-1"); c.setName("cls"); c.setDisplayName("Cls");
        IEntityDao cd = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaClassification.class)).thenReturn(cd);
        when(cd.findAll()).thenReturn(List.of(c));

        NopMetaTag t = new NopMetaTag();
        t.setTagId("t-1"); t.setName("tag"); t.setDisplayName("Tag");
        IEntityDao td = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaTag.class)).thenReturn(td);
        when(td.findAll()).thenReturn(List.of(t));

        NopMetaGlossaryTerm g = new NopMetaGlossaryTerm();
        g.setGlossaryTermId("g-1"); g.setName("term"); g.setDisplayName("Term");
        IEntityDao gd = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaGlossaryTerm.class)).thenReturn(gd);
        when(gd.findAll()).thenReturn(List.of(g));

        NopMetaTable tb = new NopMetaTable();
        tb.setMetaTableId("tb-1"); tb.setTableName("table"); tb.setDisplayName("Table");
        IEntityDao tbd = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaTable.class)).thenReturn(tbd);
        when(tbd.findAll()).thenReturn(List.of(tb));

        NopMetaEntity e = new NopMetaEntity();
        e.setMetaEntityId("e-1"); e.setEntityName("entity"); e.setDisplayName("Entity");
        IEntityDao ed = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaEntity.class)).thenReturn(ed);
        when(ed.findAll()).thenReturn(List.of(e));

        NopMetaEntityField f = new NopMetaEntityField();
        f.setEntityFieldId("f-1"); f.setFieldName("field"); f.setDisplayName("Field");
        IEntityDao fd = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaEntityField.class)).thenReturn(fd);
        when(fd.findAll()).thenReturn(List.of(f));
    }
}
