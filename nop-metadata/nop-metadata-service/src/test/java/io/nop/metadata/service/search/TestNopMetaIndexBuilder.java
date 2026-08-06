package io.nop.metadata.service.search;

import io.nop.dao.api.IEntityDao;
import io.nop.metadata.api.dto.IndexResult;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.search.api.ISearchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestNopMetaIndexBuilder {

    @Mock
    ISearchEngine searchEngine;

    @Mock
    io.nop.dao.api.IDaoProvider daoProvider;

    NopMetaIndexBuilder builder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        builder = new NopMetaIndexBuilder();
        builder.searchEngine = searchEngine;
        builder.daoProvider = daoProvider;
    }

    @Test
    void testBuildFullIndex_nullEntityTypes() {
        mockNonEmptyDaos();

        List<IndexResult> results = builder.buildFullIndex(null);
        assertNotNull(results);
        assertEquals(6, results.size());

        verify(searchEngine, times(6)).addDocs(eq(NopMetaSearchProcessor.TOPIC), anyList());
        verify(searchEngine, times(6)).refreshBlocking(NopMetaSearchProcessor.TOPIC);
    }

    @Test
    void testBuildFullIndex_singleEntityType() {
        NopMetaClassification classification = new NopMetaClassification();
        classification.setClassificationId("cls-1");
        classification.setName("test-cls");
        classification.setDisplayName("Test Classification");

        IEntityDao<NopMetaClassification> dao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaClassification.class)).thenReturn(dao);
        when(dao.findAll()).thenReturn(List.of(classification));

        List<IndexResult> results = builder.buildFullIndex(List.of("Classification"));
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Classification", results.get(0).getEntityType());

        verify(searchEngine).addDocs(eq(NopMetaSearchProcessor.TOPIC), anyList());
        verify(searchEngine).refreshBlocking(NopMetaSearchProcessor.TOPIC);
    }

    @Test
    void testBuildFullIndex_engineNull() {
        builder.searchEngine = null;
        List<IndexResult> results = builder.buildFullIndex(null);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("ALL", results.get(0).getEntityType());
        assertEquals(1, results.get(0).getFailed());
    }

    /**
     * AR-23③（R8.2）：refreshBlocking 失败必须写入 IndexResult（failed + errors 含 refresh 信息），
     * indexed 如实反映已 addDocs 数——索引 rebuild 不再静默报"成功"（修复前仅 LOG.warn）。
     */
    @Test
    void testBuildFullIndex_refreshFailureReportedInResult() {
        mockNonEmptyDaos();
        doThrow(new RuntimeException("index refresh down"))
                .when(searchEngine).refreshBlocking(NopMetaSearchProcessor.TOPIC);

        List<IndexResult> results = builder.buildFullIndex(null);
        assertEquals(6, results.size(), "each entity type keeps its own result row");
        for (IndexResult r : results) {
            assertEquals(1, r.getIndexed(),
                    "docs already added must still be counted as indexed (refresh failure is not addDocs failure)");
            assertEquals(1, r.getFailed(),
                    "refresh failure must be recorded as failed (was silent LOG.warn before AR-23③)");
            assertNotNull(r.getErrors(), "refresh failure must carry errors list");
            assertTrue(r.getErrors().stream().anyMatch(e -> e.contains("refresh")),
                    "errors must mention index refresh, got: " + r.getErrors());
        }
        verify(searchEngine, times(6)).addDocs(eq(NopMetaSearchProcessor.TOPIC), anyList());
    }

    @Test
    void testBuildFullIndex_entityConversionFailure() {
        IEntityDao<NopMetaClassification> dao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaClassification.class)).thenReturn(dao);
        when(dao.findAll()).thenThrow(new RuntimeException("DB error"));

        mockEmptyDaosSkipping("Classification");

        List<IndexResult> results = builder.buildFullIndex(null);
        assertNotNull(results);
        assertEquals(6, results.size());
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

    private void mockEmptyDaosSkipping(String... skip) {
        List<String> skipList = List.of(skip);
        if (!skipList.contains("Classification")) mockEmptyDao(NopMetaClassification.class);
        if (!skipList.contains("Tag")) mockEmptyDao(NopMetaTag.class);
        if (!skipList.contains("GlossaryTerm")) mockEmptyDao(NopMetaGlossaryTerm.class);
        if (!skipList.contains("MetaTable")) mockEmptyDao(NopMetaTable.class);
        if (!skipList.contains("MetaEntity")) mockEmptyDao(NopMetaEntity.class);
        if (!skipList.contains("MetaEntityField")) mockEmptyDao(NopMetaEntityField.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockEmptyDao(Class entityClass) {
        IEntityDao dao = mock(IEntityDao.class);
        when(daoProvider.daoFor(entityClass)).thenReturn(dao);
        when(dao.findAll()).thenReturn(Collections.emptyList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockNonEmptyDaos() {
        // Each entity type gets a single entity
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
