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
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // ===== AR-23②（R8.4b）：重建前清理陈旧文档 =====

    /**
     * 全量重建（entityTypes=null）：removeTopic 恰好调用 1 次且先于任何 addDocs（不 per-type 循环内多次）；
     * 不走类型级枚举（无 search / 无 removeDocs）。
     */
    @Test
    void testBuildFullIndex_fullRebuildPurgesTopicOnceBeforeAddDocs() {
        mockNonEmptyDaos();

        List<IndexResult> results = builder.buildFullIndex(null);
        assertEquals(6, results.size());

        InOrder inOrder = inOrder(searchEngine);
        inOrder.verify(searchEngine).removeTopic(NopMetaSearchProcessor.TOPIC);
        inOrder.verify(searchEngine, times(6)).addDocs(eq(NopMetaSearchProcessor.TOPIC), anyList());
        verify(searchEngine, times(6)).refreshBlocking(NopMetaSearchProcessor.TOPIC);
        verify(searchEngine, never()).removeDocs(anyString(), anyList());
        verify(searchEngine, never()).search(any());
    }

    /**
     * 部分重建（entityTypes 子集）：按类型级枚举清理——空 query + tags 过滤的 search 枚举现有 docId →
     * removeDocs 仅目标类型 docId（非目标类型不受影响）；removeTopic 不被调用（不整个清 topic）。
     */
    @Test
    void testBuildFullIndex_partialRebuildPurgesOnlyTargetType() {
        NopMetaClassification classification = new NopMetaClassification();
        classification.setClassificationId("cls-1");
        classification.setName("test-cls");
        classification.setDisplayName("Test Classification");
        IEntityDao<NopMetaClassification> dao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaClassification.class)).thenReturn(dao);
        when(dao.findAll()).thenReturn(List.of(classification));

        SearchResponse resp = new SearchResponse();
        SearchHit stale1 = new SearchHit();
        stale1.setId("cls-stale-1");
        SearchHit stale2 = new SearchHit();
        stale2.setId("cls-stale-2");
        resp.setItems(List.of(stale1, stale2));
        when(searchEngine.search(any(SearchRequest.class))).thenReturn(resp);

        List<IndexResult> results = builder.buildFullIndex(List.of("Classification"));
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getIndexed());

        // 枚举请求规格：topic + 空 query + tags=目标类型 + 显式上限
        ArgumentCaptor<SearchRequest> cap = ArgumentCaptor.forClass(SearchRequest.class);
        verify(searchEngine).search(cap.capture());
        SearchRequest request = cap.getValue();
        assertEquals(NopMetaSearchProcessor.TOPIC, request.getTopic());
        assertEquals("", request.getQuery());
        assertEquals(Collections.singleton("Classification"), request.getTags());
        assertTrue(request.getLimit() > 0, "enumeration must carry an explicit positive limit");

        // removeDocs 仅目标类型 docId；removeTopic 不调用
        verify(searchEngine).removeDocs(eq(NopMetaSearchProcessor.TOPIC), eq(List.of("cls-stale-1", "cls-stale-2")));
        verify(searchEngine, never()).removeTopic(anyString());

        // 清理先于 addDocs（时序）
        InOrder inOrder = inOrder(searchEngine);
        inOrder.verify(searchEngine).search(any(SearchRequest.class));
        inOrder.verify(searchEngine).removeDocs(anyString(), anyList());
        inOrder.verify(searchEngine).addDocs(eq(NopMetaSearchProcessor.TOPIC), anyList());
    }

    /**
     * 全量重建 topic 清理失败 → 显式反映在 IndexResult（每个类型行 failed += 1 + errors 含 purge），不吞掉
     * （沿 R8.2 AR-23③ 先例）；addDocs 仍尝试（文档照常写入，失败可观测）。
     */
    @Test
    void testBuildFullIndex_fullRebuildPurgeFailureReportedInResult() {
        mockNonEmptyDaos();
        doThrow(new RuntimeException("topic purge down"))
                .when(searchEngine).removeTopic(NopMetaSearchProcessor.TOPIC);

        List<IndexResult> results = builder.buildFullIndex(null);
        assertEquals(6, results.size());
        for (IndexResult r : results) {
            assertEquals(1, r.getFailed(),
                    "topic purge failure must be recorded as failed in every type row (was silent before AR-23②)");
            assertTrue(r.getErrors().stream().anyMatch(e -> e.contains("purge")),
                    "errors must mention topic purge, got: " + r.getErrors());
        }
        verify(searchEngine, times(6)).addDocs(eq(NopMetaSearchProcessor.TOPIC), anyList());
    }

    /**
     * 部分重建枚举失败 → 该类型行 failed += 1 + errors 含 purge（不吞掉）；addDocs 仍尝试。
     */
    @Test
    void testBuildFullIndex_partialPurgeFailureReportedInResult() {
        NopMetaClassification classification = new NopMetaClassification();
        classification.setClassificationId("cls-1");
        classification.setName("test-cls");
        classification.setDisplayName("Test Classification");
        IEntityDao<NopMetaClassification> dao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaClassification.class)).thenReturn(dao);
        when(dao.findAll()).thenReturn(List.of(classification));
        doThrow(new RuntimeException("enumeration down"))
                .when(searchEngine).search(any(SearchRequest.class));

        List<IndexResult> results = builder.buildFullIndex(List.of("Classification"));
        assertEquals(1, results.size());
        IndexResult r = results.get(0);
        assertEquals(1, r.getFailed(),
                "partial purge failure must be recorded as failed (was silent before AR-23②)");
        assertTrue(r.getErrors().stream().anyMatch(e -> e.contains("purge")),
                "errors must mention purge, got: " + r.getErrors());
        verify(searchEngine).addDocs(eq(NopMetaSearchProcessor.TOPIC), anyList());
    }

    /**
     * <b>正向断言（防假绿）</b>：in-memory fake ISearchEngine 记录真实状态——重建后"已删除实体不在新索引、
     * 未删除实体仍在"（负向 + 正向双向断言，不只验证调用不验证效果）：
     * <ul>
     *   <li>部分重建（["MetaTable"]）：仅 MetaTable 类型被枚举清理（tb-deleted 移除），非目标类型
     *       （cls-1）原样保留</li>
     *   <li>全量重建（null）：removeTopic 清空 topic（cls-1 也被清掉）后重写，只含当前 DB 实体</li>
     * </ul>
     */
    @Test
    void testRebuildRemovesStaleDocsAndKeepsLiveOnes() {
        NopMetaTable t1 = new NopMetaTable();
        t1.setMetaTableId("tb-1");
        t1.setTableName("T1");
        t1.setDisplayName("T1");
        NopMetaTable t2 = new NopMetaTable();
        t2.setMetaTableId("tb-2");
        t2.setTableName("T2");
        t2.setDisplayName("T2");
        IEntityDao<NopMetaTable> tableDao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaTable.class)).thenReturn(tableDao);
        when(tableDao.findAll()).thenReturn(List.of(t1, t2));
        mockEmptyDaosSkipping("MetaTable");

        FakeSearchEngine fake = new FakeSearchEngine();
        builder.searchEngine = fake;
        // 预置陈旧索引：T1/T2（仍存在）+ tb-deleted（已被删除的幽灵）+ cls-1（其他类型文档）
        fake.docs.put("tb-1", doc("tb-1", "T1", "MetaTable"));
        fake.docs.put("tb-2", doc("tb-2", "T2", "MetaTable"));
        fake.docs.put("tb-deleted", doc("tb-deleted", "T_DELETED", "MetaTable"));
        fake.docs.put("cls-1", doc("cls-1", "Cls", "Classification"));

        // 部分重建（["MetaTable"]）：仅目标类型清理——幽灵 doc 移除、live doc 重写、非目标类型保留
        List<IndexResult> partialResults = builder.buildFullIndex(List.of("MetaTable"));
        assertEquals(1, partialResults.size());
        assertEquals(0, partialResults.get(0).getFailed());
        assertTrue(fake.docs.containsKey("tb-1"), "live table must be re-indexed (positive assertion)");
        assertTrue(fake.docs.containsKey("tb-2"), "live table must be re-indexed (positive assertion)");
        assertFalse(fake.docs.containsKey("tb-deleted"),
                "deleted entity's stale doc must be purged before rebuild (negative assertion)");
        assertTrue(fake.docs.containsKey("cls-1"),
                "non-target entity type must be untouched by partial rebuild (isolation)");
        assertTrue(fake.removeTopicCalls == 0, "partial rebuild must not clear the whole topic");

        // 全量重建（null）：removeTopic 清空 topic 后只含当前 DB 实体（cls-1 也被清理）
        List<IndexResult> fullResults = builder.buildFullIndex(null);
        assertEquals(6, fullResults.size());
        assertEquals(1, fake.removeTopicCalls, "full rebuild must removeTopic exactly once");
        assertTrue(fake.docs.containsKey("tb-1"));
        assertTrue(fake.docs.containsKey("tb-2"));
        assertFalse(fake.docs.containsKey("tb-deleted"), "stale doc must not survive full rebuild");
        assertFalse(fake.docs.containsKey("cls-1"),
                "full rebuild clears the whole topic (other types rebuilt from their daos which are empty)");
    }

    private static SearchableDoc doc(String id, String name, String type) {
        SearchableDoc d = new SearchableDoc();
        d.setId(id);
        d.setName(name);
        d.setTitle(name);
        d.setTagSet(Collections.singleton(type));
        return d;
    }

    /** in-memory fake：记录真实状态（doc 集合 / removeTopic 计数 / removeDocs 批次），search 按 tag 过滤。 */
    static class FakeSearchEngine implements ISearchEngine {
        final Map<String, SearchableDoc> docs = new LinkedHashMap<>();
        int removeTopicCalls = 0;
        final List<List<String>> removedDocIdBatches = new ArrayList<>();

        @Override
        public SearchResponse search(SearchRequest request) {
            List<SearchHit> items = new ArrayList<>();
            if (request.getTags() != null) {
                for (SearchableDoc d : docs.values()) {
                    if (d.getTagSet() != null && d.getTagSet().containsAll(request.getTags())) {
                        SearchHit hit = new SearchHit();
                        hit.setId(d.getId());
                        hit.setTags(d.getTagSet());
                        items.add(hit);
                    }
                }
            }
            if (items.size() > request.getLimit()) {
                items = items.subList(0, request.getLimit());
            }
            SearchResponse resp = new SearchResponse();
            resp.setItems(items);
            resp.setTotal(items.size());
            return resp;
        }

        @Override
        public SearchableDoc getDoc(String docId) {
            return docs.get(docId);
        }

        @Override
        public List<SearchableDoc> getDocsByTerm(String topic, String term) {
            return new ArrayList<>(docs.values());
        }

        @Override
        public Map<String, List<String>> analyzeDoc(SearchableDoc doc) {
            return Collections.emptyMap();
        }

        @Override
        public List<String> analyzeQuery(String query) {
            return Collections.emptyList();
        }

        @Override
        public void refreshBlocking(String topic) {
        }

        @Override
        public void addDocs(String topic, List<SearchableDoc> docList) {
            for (SearchableDoc d : docList) {
                docs.put(d.getId(), d);
            }
        }

        @Override
        public void removeDocs(String topic, List<String> docIds) {
            removedDocIdBatches.add(new ArrayList<>(docIds));
            for (String id : docIds) {
                docs.remove(id);
            }
        }

        @Override
        public void removeTopic(String topic) {
            removeTopicCalls++;
            docs.clear();
        }
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
