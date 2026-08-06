package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * AR-08（plan 2026-08-06-0553-3 Phase 3）：导入路径三态一致性（DB 行 / 搜索索引 / 事件）+
 * 级联删除索引清理。
 *
 * <p>注入点沿 {@code TestNopMetadataSearchIntegration} 先例：测试内直接构造
 * {@link NopMetaSearchProcessor} 注入 mock {@link ISearchEngine}（searchEngine 为 search 包
 * protected 字段，经嵌套子类暴露 setter）；再替换容器 bean {@code NopMetaModuleBizModel.searchService}
 * （protected 字段，同包 entity 可访问）。每次测试后还原，避免污染共享容器 bean。
 *
 * <p>判别性：修复前单路径索引失败 → DB 回滚但索引幽灵（无 removeDocs 对账）；批量路径 catch +
 * clearSession 只清会话缓存，flush 已送出 SQL 在外层事务提交时照常落库（报失败但数据已提交）——
 * 两断言修复前均 red。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaModuleImportConsistency extends JunitBaseTestCase {

    public TestNopMetaModuleImportConsistency() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    private static final String IMPORT_PATH = "/nop/metadata/orm/app.orm.xml";
    private static final String MODULE_BIZ_ID = "nop/metadata";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopMetaModuleBizModel moduleBizModel;

    @Inject
    NopMetaEntityBizModel entityBizModel;

    private NopMetaSearchProcessor originalSearchService;
    private NopMetaSearchProcessor originalEntitySearchService;
    private ISearchEngine mockEngine;
    private TestSearchProcessor testSearchService;

    @BeforeEach
    void setUp() {
        originalSearchService = moduleBizModel.searchService;
        originalEntitySearchService = entityBizModel.searchService;
        mockEngine = mock(ISearchEngine.class);
        testSearchService = new TestSearchProcessor();
        testSearchService.setEngine(mockEngine);
        moduleBizModel.searchService = testSearchService;
        entityBizModel.searchService = testSearchService;
    }

    @AfterEach
    void tearDown() {
        moduleBizModel.searchService = originalSearchService;
        entityBizModel.searchService = originalEntitySearchService;
    }

    /** 单路径：索引写入失败（fail-closed）→ DB 回滚 + 已写索引文档反向清理（三态一致回滚）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testImportIndexFailureRollsBackDbAndCleansIndex() {
        long modulesBefore = countModuleRows(MODULE_BIZ_ID);
        long entitiesBefore = countRows(NopMetaEntity.class);

        // 第 3 次 addDoc 起抛异常：前 2 个文档已写入（幽灵候选）
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() >= 3) {
                throw new IllegalStateException("search engine down");
            }
            return null;
        }).when(mockEngine).addDoc(anyString(), any(SearchableDoc.class));

        GraphQLResponseBean resp = execute(importMutation());
        assertTrue(resp.hasError(), "index failure must fail the import (fail-closed): " + resp);

        // DB 一致回滚：模块/实体零残留
        assertEquals(modulesBefore, countModuleRows(MODULE_BIZ_ID),
                "failed import must leave no module rows");
        assertEquals(entitiesBefore, countRows(NopMetaEntity.class),
                "failed import must leave no entity rows");

        // 索引对账：恰好已写 2 个文档被 removeDocs 反向清理（修复前无清理 → 幽灵文档 → red）
        ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEngine, atLeastOnce()).removeDocs(eq(NopMetaSearchProcessor.TOPIC), idCaptor.capture());
        List<String> removed = new ArrayList<>();
        for (List<String> ids : idCaptor.getAllValues()) {
            removed.addAll(ids);
        }
        assertEquals(2, removed.size(),
                "exactly the 2 already-written docs must be cleaned up (no ghosts): " + removed);
    }

    /** 批量路径：第 2 个路径索引失败 → per-path 独立事务隔离（路径 1 提交，路径 2 回滚不留部分状态）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testImportBatchPathFailureIsolation() {
        // 预跑：统计单次导入 addDoc 次数（同一资源确定性）
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            return null;
        }).when(mockEngine).addDoc(anyString(), any(SearchableDoc.class));

        GraphQLResponseBean pre = execute(importMutation());
        assertFalse(pre.hasError(), "pre-run import must succeed: " + pre);
        int docsPerImport = calls.get();
        assertTrue(docsPerImport > 0, "import must index docs");

        // 批次 [app, app]：第 2 个路径首个 addDoc 抛错（fail-closed）→ 该路径回滚
        reset(mockEngine);
        AtomicInteger calls2 = new AtomicInteger();
        doAnswer(inv -> {
            if (calls2.incrementAndGet() > docsPerImport) {
                throw new IllegalStateException("search engine down");
            }
            return null;
        }).when(mockEngine).addDoc(anyString(), any(SearchableDoc.class));

        long modulesBefore = countModuleRows(MODULE_BIZ_ID);
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaModule__importOrmModels(paths: [\"" + IMPORT_PATH + "\", \"" + IMPORT_PATH
                        + "\"]) { success error moduleName } }");
        assertFalse(resp.hasError(), "batch must not fail globally: " + resp);

        List<Map<String, Object>> results = parseList(resp, "NopMetaModule__importOrmModels");
        assertEquals(2, results.size(), "batch must return per-path results: " + resp);
        assertEquals(true, results.get(0).get("success"), "path 1 must succeed: " + results);
        assertEquals(false, results.get(1).get("success"), "path 2 must report failure: " + results);

        // DB：仅路径 1 的模块提交（+1），路径 2 无残留（修复前外层事务提交使路径 2 也落库 → +2 → red）
        assertEquals(modulesBefore + 1, countModuleRows(MODULE_BIZ_ID),
                "only path 1 module must be committed (no 'reported failed but committed' split)");
        // 索引：路径 1 全部写入（≥docsPerImport——路径 2 首个 addDoc 调用计入 mock 交互后抛错）；
        // 路径 2 首写即抛（0 已写 → 0 清理）
        verify(mockEngine, never()).removeDocs(anyString(), anyList());
        verify(mockEngine, org.mockito.Mockito.atLeast(docsPerImport)).addDoc(anyString(), any(SearchableDoc.class));
    }

    /** 端到端：导入成功 → 索引写入；级联删除 → 索引无残留（搜索不再返回已删实体）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testImportThenDeleteLeavesNoIndexResidue() {
        List<String> indexedDocIds = new ArrayList<>();
        doAnswer(inv -> {
            indexedDocIds.add(((SearchableDoc) inv.getArgument(1)).getId());
            return null;
        }).when(mockEngine).addDoc(anyString(), any(SearchableDoc.class));

        GraphQLResponseBean imp = execute(importMutation());
        assertFalse(imp.hasError(), "import must succeed: " + imp);
        String moduleId = String.valueOf(parseMap(imp, "NopMetaModule__importOrmModel").get("metaModuleId"));
        assertTrue(indexedDocIds.size() > 100,
                "import must index entities+fields+tables: " + indexedDocIds.size());

        // 级联删除模块 → 索引清理（MetaEntity/MetaEntityField/MetaTable 全部 removeDocs）
        reset(mockEngine);
        GraphQLResponseBean del = execute("mutation { NopMetaModule__delete(id: \"" + moduleId + "\") }");
        assertFalse(del.hasError(), "module delete must succeed: " + del);

        ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEngine, atLeastOnce()).removeDocs(eq(NopMetaSearchProcessor.TOPIC), idCaptor.capture());
        List<String> removed = new ArrayList<>();
        for (List<String> ids : idCaptor.getAllValues()) {
            removed.addAll(ids);
        }
        assertTrue(removed.containsAll(indexedDocIds),
                "all imported docs must be removed from index (search must not return deleted entities): missing="
                        + minus(indexedDocIds, removed));
        assertEquals(indexedDocIds.size(), removed.size(),
                "removed count must match imported docs: " + removed.size() + " vs " + indexedDocIds.size());
        // DB 级联删除无残留：模块行 + 其 ormModel 下的实体行全部删除
        assertEquals(0L, countModuleRowsByPk(moduleId), "module row must be deleted");
        assertEquals(0L, countEntitiesForModule(moduleId), "cascade-deleted entities must be gone");
    }

    /** 实体级联删除：MetaEntity + 其 MetaEntityField 索引均清理。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testEntityDeleteCleansFieldIndex() {
        doAnswer(inv -> null).when(mockEngine).addDoc(anyString(), any(SearchableDoc.class));
        GraphQLResponseBean imp = execute(importMutation());
        assertFalse(imp.hasError(), "import must succeed: " + imp);

        // 取一个真实实体及其字段 id
        IEntityDao<NopMetaEntity> entityDao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity entity = entityDao.findAll().get(0);
        assertTrue(entity != null, "imported entity must exist");
        String entityId = entity.getMetaEntityId();
        List<String> fieldIds = findFieldIdsByEntity(entityId);
        assertTrue(fieldIds.size() > 0, "entity must have fields");

        reset(mockEngine);
        GraphQLResponseBean del = execute("mutation { NopMetaEntity__delete(id: \"" + entityId + "\") }");
        assertFalse(del.hasError(), "entity delete must succeed: " + del);

        ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEngine, atLeastOnce()).removeDocs(eq(NopMetaSearchProcessor.TOPIC), idCaptor.capture());
        List<String> removed = new ArrayList<>();
        for (List<String> ids : idCaptor.getAllValues()) {
            removed.addAll(ids);
        }
        assertTrue(removed.contains(entityId), "deleted entity doc must be removed: " + removed);
        assertTrue(removed.containsAll(fieldIds),
                "cascade-deleted field docs must be removed: missing=" + minus(fieldIds, removed));
    }

    // ===== helpers =====

    /** 测试用 search 处理器子类：暴露 searchEngine 注入点（search 包 protected 字段，子类可访问）。 */
    static final class TestSearchProcessor extends NopMetaSearchProcessor {
        void setEngine(ISearchEngine engine) {
            this.searchEngine = engine;
        }
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    private static String importMutation() {
        return "mutation { NopMetaModule__importOrmModel(path: \"" + IMPORT_PATH + "\") { metaModuleId } }";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(GraphQLResponseBean resp, String key) {
        return (Map<String, Object>) ((Map<String, Object>) resp.getData()).get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseList(GraphQLResponseBean resp, String key) {
        return (List<Map<String, Object>>) ((Map<String, Object>) resp.getData()).get(key);
    }

    private long countModuleRows(String bizModuleId) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        long count = 0;
        for (NopMetaModule m : dao.findAll()) {
            if (bizModuleId.equals(m.getModuleId())) {
                count++;
            }
        }
        return count;
    }

    private long countModuleRowsByPk(String metaModuleId) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        long count = 0;
        for (NopMetaModule m : dao.findAll()) {
            if (metaModuleId.equals(m.getMetaModuleId())) {
                count++;
            }
        }
        return count;
    }

    private long countRows(Class<?> entityClass) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        IEntityDao dao = daoProvider.daoFor((Class) entityClass);
        return dao.findAll().size();
    }

    /** 模块删除后其 ormModel 下的实体行应全部级联删除（ormModelId ∈ 该模块的 orm 模型 id 集合）。 */
    private long countEntitiesForModule(String moduleId) {
        IEntityDao<NopMetaOrmModel> ormDao = daoProvider.daoFor(NopMetaOrmModel.class);
        List<String> ormModelIds = new ArrayList<>();
        for (NopMetaOrmModel om : ormDao.findAll()) {
            if (moduleId.equals(om.getMetaModuleId())) {
                ormModelIds.add(om.getOrmModelId());
            }
        }
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        long count = 0;
        for (NopMetaEntity e : dao.findAll()) {
            if (e.getOrmModelId() != null && ormModelIds.contains(e.getOrmModelId())) {
                count++;
            }
        }
        return count;
    }

    private List<String> findFieldIdsByEntity(String entityId) {
        IEntityDao<NopMetaEntityField> dao = daoProvider.daoFor(NopMetaEntityField.class);
        List<String> ids = new ArrayList<>();
        for (NopMetaEntityField f : dao.findAll()) {
            if (entityId.equals(f.getMetaEntityId())) {
                ids.add(f.getEntityFieldId());
            }
        }
        return ids;
    }

    private static List<String> minus(List<String> a, List<String> b) {
        List<String> result = new ArrayList<>(a);
        result.removeAll(b);
        return result;
    }
}
