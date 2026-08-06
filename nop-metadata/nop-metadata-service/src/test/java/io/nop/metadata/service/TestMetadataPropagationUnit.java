
package io.nop.metadata.service;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.entity.AutoClassificationProcessor;
import io.nop.metadata.service.entity.LineageTagPropagationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestMetadataPropagationUnit {

    private LineageTagPropagationProcessor propagationService;
    private AutoClassificationProcessor classificationService;
    private IDaoProvider daoProvider;
    private IEntityDao<NopMetaTagLabel> tagLabelDao;
    private IEntityDao<NopMetaLineageEdge> edgeDao;
    private IEntityDao<NopMetaTable> tableDao;
    private IEntityDao<NopMetaClassification> clsDao;
    private IEntityDao<NopMetaTag> tagDao;
    private IEntityDao<NopMetaEntityField> fieldDao;
    private IBizObjectManager bizObjectManager;
    private IServiceContext context;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        daoProvider = mock(IDaoProvider.class);
        tagLabelDao = (IEntityDao<NopMetaTagLabel>) mock(IEntityDao.class);
        edgeDao = (IEntityDao<NopMetaLineageEdge>) mock(IEntityDao.class);
        tableDao = (IEntityDao<NopMetaTable>) mock(IEntityDao.class);
        clsDao = (IEntityDao<NopMetaClassification>) mock(IEntityDao.class);
        tagDao = (IEntityDao<NopMetaTag>) mock(IEntityDao.class);
        fieldDao = (IEntityDao<NopMetaEntityField>) mock(IEntityDao.class);
        bizObjectManager = mock(IBizObjectManager.class);
        context = mock(IServiceContext.class);

        when(daoProvider.daoFor(NopMetaTagLabel.class)).thenReturn(tagLabelDao);
        when(daoProvider.daoFor(NopMetaLineageEdge.class)).thenReturn(edgeDao);
        when(daoProvider.daoFor(NopMetaTable.class)).thenReturn(tableDao);
        when(daoProvider.daoFor(NopMetaClassification.class)).thenReturn(clsDao);
        when(daoProvider.daoFor(NopMetaTag.class)).thenReturn(tagDao);
        when(daoProvider.daoFor(NopMetaEntityField.class)).thenReturn(fieldDao);

        propagationService = new LineageTagPropagationProcessor();
        propagationService.setDaoProvider(daoProvider);
        propagationService.setBizObjectManager(bizObjectManager);

        classificationService = new AutoClassificationProcessor();
        classificationService.setDaoProvider(daoProvider);
        classificationService.setBizObjectManager(bizObjectManager);
    }

    // ===== LineageTagPropagationProcessor Tests =====

    @Test
    public void testPropagationRejectsNonNopMetaTableEntityType() {
        NopException ex = assertThrows(NopException.class, () ->
                propagationService.propagateTags("OtherEntity", "id-1", null, context));
        assertTrue(ex.getErrorCode().contains("propagate-unsupported-entity-type"));
    }

    @Test
    public void testPropagationEmptyLabels() {
        when(tagLabelDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.emptyList());

        List<NopMetaTagLabel> result = propagationService.propagateTags("NopMetaTable", "table-1", null, context);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testPropagationIdempotentDuplicateCall() {
        NopMetaTagLabel sourceLabel = createTagLabel("tlabel-1", "NopMetaTable", "table-1", "tag-1", "Manual");
        when(tagLabelDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.singletonList(sourceLabel))
                .thenReturn(Collections.singletonList(sourceLabel));

        NopMetaLineageEdge edge = createLineageEdge("edge-1", "table-1", "table-2");
        when(edgeDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.singletonList(edge))
                .thenReturn(Collections.singletonList(edge));

        when(tagLabelDao.findFirstByQuery(any(QueryBean.class)))
                .thenReturn(null)
                .thenReturn(createTagLabel("tlabel-p-1", "NopMetaTable", "table-2", "tag-1", "Propagated"));

        when(bizObjectManager.getBizObject("NopMetaTagLabel")).thenReturn(null);

        List<NopMetaTagLabel> firstResult = propagationService.propagateTags("NopMetaTable", "table-1", "tag-1", context);
        assertNotNull(firstResult);

        List<NopMetaTagLabel> secondResult = propagationService.propagateTags("NopMetaTable", "table-1", "tag-1", context);
        assertNotNull(secondResult);
    }

    /**
     * AR-21 re-adjudication（plan 2026-08-06-1228-1 Phase 3）：propagateEdge 单边失败 → 不中断整批
     * （per-edge 隔离保持），但失败已 LOG.error 可观测（不再"静默返回空结果"——内层 doCreatePropagatedLabel
     * 不再 catch-all 吞掉后返回 null，而是抛 ERR_TAG_LABEL_SAVE_FAILED 到外层 catch 留证后继续）。
     */
    @Test
    public void testPropagationPerEdgeIsolation() {
        NopMetaTagLabel sourceLabel = createTagLabel("tlabel-1", "NopMetaTable", "table-1", "tag-1", "Manual");
        when(tagLabelDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.singletonList(sourceLabel));

        NopMetaLineageEdge edge1 = createLineageEdge("edge-1", "table-1", "table-2");
        NopMetaLineageEdge edge2 = createLineageEdge("edge-2", "table-1", "table-3");
        when(edgeDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(List.of(edge1, edge2));

        when(tagLabelDao.findFirstByQuery(any(QueryBean.class)))
                .thenReturn(sourceLabel) // getSourceLabels(tagId path) — 旧测试此处返回 null 致 sourceLabels 为空，
                // propagateTags 早退返回空，原"静默空结果"断言是假绿（真实行为从未到达边缘循环）
                .thenReturn(null) // edge-1: hasExistingPropagatedLabel
                .thenReturn(null); // edge-2: hasExistingPropagatedLabel

        IBizObject bizObject = mock(IBizObject.class);
        when(bizObjectManager.getBizObject("NopMetaTagLabel"))
                .thenThrow(new RuntimeException("edge-1 fail")) // first edge fails (inner catch-all no longer swallows)
                .thenReturn(bizObject); // second edge succeeds
        NopMetaTagLabel created = createTagLabel("tl-edge2", "NopMetaTable", "table-3", "tag-1", "Propagated");
        when(bizObject.invoke(eq("save"), any(), any(), any())).thenReturn(created);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LineageTagPropagationProcessor.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            List<NopMetaTagLabel> result = propagationService.propagateTags("NopMetaTable", "table-1", "tag-1", context);

            // 隔离保持：edge-1 失败不中断 edge-2 的传播
            assertEquals(1, result.size(),
                    "edge-1 failure must not interrupt edge-2 propagation (per-edge isolation kept): " + result);
            assertEquals("tag-1", result.get(0).getTagId());
            assertEquals("tl-edge2", result.get(0).getTagLabelId());

            // 失败可观测：LOG.error 含 edgeId + 上下文（不再静默返回空结果）
            boolean errLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.ERROR
                            && e.getFormattedMessage().contains("propagation failed for edge")
                            && e.getFormattedMessage().contains("edge-1"));
            assertTrue(errLogged,
                    "edge-1 failure must be observable via LOG.error (AR-21), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ===== AutoClassificationProcessor Tests =====

    @Test
    public void testAutoClassifyRejectsNonNopMetaTable() {
        NopException ex = assertThrows(NopException.class, () ->
                classificationService.suggestTags("OtherEntity", "id-1", context));
        assertTrue(ex.getErrorCode().contains("autoclassify-unsupported-entity-type"));
    }

    @Test
    public void testAutoClassifyRejectsNonEntityTableType() {
        NopMetaTable sqlTable = new NopMetaTable();
        sqlTable.setMetaTableId("sql-table-1");
        sqlTable.setTableType("sql");
        when(tableDao.getEntityById("sql-table-1")).thenReturn(sqlTable);

        NopException ex = assertThrows(NopException.class, () ->
                classificationService.suggestTags("NopMetaTable", "sql-table-1", context));
        assertTrue(ex.getErrorCode().contains("autoclassify-unsupported-table-type"));
    }

    @Test
    public void testAutoClassifyBaseEntityIdNull() {
        NopMetaTable entityTable = new NopMetaTable();
        entityTable.setMetaTableId("entity-table-1");
        entityTable.setTableType("entity");
        entityTable.setBaseEntityId(null);
        when(tableDao.getEntityById("entity-table-1")).thenReturn(entityTable);

        List<NopMetaTagLabel> result = classificationService.suggestTags("NopMetaTable", "entity-table-1", context);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testAutoClassifyEmptyConfig() {
        NopMetaTable entityTable = new NopMetaTable();
        entityTable.setMetaTableId("entity-table-1");
        entityTable.setTableType("entity");
        entityTable.setBaseEntityId("entity-1");
        when(tableDao.getEntityById("entity-table-1")).thenReturn(entityTable);

        NopMetaClassification cls = new NopMetaClassification();
        cls.setClassificationId("cls-1");
        cls.setAutoClassificationConfig(null);
        when(clsDao.getEntityById("cls-1")).thenReturn(cls);

        List<NopMetaTagLabel> result = classificationService.suggestTags("NopMetaTable", "entity-table-1", context);
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAutoClassifyEmptyRules() {
        NopMetaTable entityTable = new NopMetaTable();
        entityTable.setMetaTableId("entity-table-1");
        entityTable.setTableType("entity");
        entityTable.setBaseEntityId("entity-1");
        when(tableDao.getEntityById("entity-table-1")).thenReturn(entityTable);

        IEntityDao<NopMetaTagLabel> localTagLabelDao = (IEntityDao<NopMetaTagLabel>) mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaTagLabel.class)).thenReturn(localTagLabelDao);

        NopMetaClassification cls = new NopMetaClassification();
        cls.setClassificationId("cls-1");
        cls.setAutoClassificationConfig("[]");
        when(clsDao.getEntityById("cls-1")).thenReturn(cls);

        NopMetaTagLabel manualLabel = createTagLabel("ml-1", "NopMetaTable", "entity-table-1", "tag-1", "Manual");
        NopMetaTag tag = new NopMetaTag();
        tag.setTagId("tag-1");
        tag.setClassificationId("cls-1");
        when(localTagLabelDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.singletonList(manualLabel));
        when(tagDao.getEntityById("tag-1")).thenReturn(tag);

        List<NopMetaTagLabel> result = classificationService.suggestTags("NopMetaTable", "entity-table-1", context);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testAutoClassifyNoFieldsNoResult() {
        NopMetaTable entityTable = new NopMetaTable();
        entityTable.setMetaTableId("entity-table-1");
        entityTable.setTableType("entity");
        entityTable.setBaseEntityId("entity-1");
        when(tableDao.getEntityById("entity-table-1")).thenReturn(entityTable);

        when(fieldDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.emptyList());

        List<NopMetaTagLabel> result = classificationService.suggestTags("NopMetaTable", "entity-table-1", context);
        assertTrue(result.isEmpty());
    }

    /**
     * P2-02 回归：含非法正则 pattern 的规则必须被跳过但可观测（LOG.warn 记录 pattern +
     * classificationId + ruleIndex + throwable），合法规则仍生效；同一非法 pattern 在
     * field×rule 双层循环内按 (classificationId, pattern) 单次调用去重，不逐字段刷屏。
     *
     * <p>正路径断言（非法规则跳过 + 合法规则命中 → 标签生成）当前仓库不存在，本测试从零
     * 搭建：沿 :213-219 discoverClassification 装配雏形（Manual 标签 → tag → classification
     * → config），bizObjectManager 嵌套 mock（getBizObject().invoke("save",...) 返回标签）。
     * 日志断言复用 Logback ListAppender 模式（TestMetaTableProfilerSecurity 先例）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAutoClassifyInvalidPatternSkippedValidRuleStillWorks() {
        NopMetaTable entityTable = new NopMetaTable();
        entityTable.setMetaTableId("entity-table-1");
        entityTable.setTableType("entity");
        entityTable.setBaseEntityId("entity-1");
        when(tableDao.getEntityById("entity-table-1")).thenReturn(entityTable);

        NopMetaTagLabel manualLabel = createTagLabel("ml-1", "NopMetaTable", "entity-table-1", "tag-1", "Manual");
        NopMetaTag manualTag = new NopMetaTag();
        manualTag.setTagId("tag-1");
        manualTag.setClassificationId("cls-1");
        when(tagLabelDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.singletonList(manualLabel));
        when(tagDao.getEntityById("tag-1")).thenReturn(manualTag);

        NopMetaClassification cls = new NopMetaClassification();
        cls.setClassificationId("cls-1");
        cls.setAutoClassificationConfig(
                "[{\"pattern\":\"[unclosed\",\"tagFQN\":\"cls-1:tag-a\"},"
                        + "{\"pattern\":\"user\",\"tagFQN\":\"cls-1:tag-b\"}]");
        when(clsDao.getEntityById("cls-1")).thenReturn(cls);

        NopMetaEntityField field1 = new NopMetaEntityField();
        field1.setFieldName("user_name");
        NopMetaEntityField field2 = new NopMetaEntityField();
        field2.setFieldName("created_at");
        when(fieldDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(List.of(field1, field2));

        NopMetaTag tagB = new NopMetaTag();
        tagB.setTagId("tag-b");
        tagB.setClassificationId("cls-1");
        when(tagDao.findFirstByQuery(any(QueryBean.class))).thenReturn(tagB);

        when(tagLabelDao.findFirstByQuery(any(QueryBean.class))).thenReturn(null);

        NopMetaTagLabel created = createTagLabel("tl-new", "NopMetaTable", "entity-table-1", "tag-b", "Automated");
        IBizObject bizObject = mock(IBizObject.class);
        when(bizObjectManager.getBizObject("NopMetaTagLabel")).thenReturn(bizObject);
        when(bizObject.invoke(eq("save"), any(), any(), any())).thenReturn(created);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AutoClassificationProcessor.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            List<NopMetaTagLabel> result = classificationService.suggestTags("NopMetaTable", "entity-table-1", context);

            // 正路径：非法规则被跳过，合法规则命中并生成标签
            assertNotNull(result);
            assertEquals(1, result.size(),
                    "invalid-pattern rule must be skipped while valid rule still applies (P2-02)");
            assertEquals("tag-b", result.get(0).getTagId());

            // 非法 pattern 必须可观测（LOG.warn 含 pattern + 上下文）
            boolean warnLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("Invalid auto-classification rule pattern")
                            && e.getFormattedMessage().contains("[unclosed")
                            && e.getFormattedMessage().contains("cls-1"));
            assertTrue(warnLogged,
                    "invalid pattern must be logged with WARN including pattern and classificationId (P2-02), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));

            // 同一非法 pattern 跨多个字段不刷屏（单次调用内去重）
            long invalidPatternWarns = appender.list.stream().filter(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("Invalid auto-classification rule pattern")
                            && e.getFormattedMessage().contains("[unclosed")).count();
            assertEquals(1L, invalidPatternWarns,
                    "same invalid pattern must be warned once per invocation despite multiple fields (P2-02)");
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * AR-21（plan 2026-08-06-1228-1 Phase 3）：用户触发路径 suggestTags 标签保存/提审失败 →
     * fail-loud——内层 doCreateAutomatedLabel 的 catch-all（LOG.error + return null）已删除，异常
     * 以 ERR_TAG_LABEL_SAVE_FAILED 上抛到请求边界（cause 保留），不再静默吞掉（修复前返回空结果实测 red）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testSuggestTagsSaveFailureFailsLoud() {
        NopMetaTable entityTable = new NopMetaTable();
        entityTable.setMetaTableId("entity-table-1");
        entityTable.setTableType("entity");
        entityTable.setBaseEntityId("entity-1");
        when(tableDao.getEntityById("entity-table-1")).thenReturn(entityTable);

        NopMetaTagLabel manualLabel = createTagLabel("ml-1", "NopMetaTable", "entity-table-1", "tag-1", "Manual");
        NopMetaTag manualTag = new NopMetaTag();
        manualTag.setTagId("tag-1");
        manualTag.setClassificationId("cls-1");
        when(tagLabelDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(Collections.singletonList(manualLabel));
        when(tagDao.getEntityById("tag-1")).thenReturn(manualTag);

        NopMetaClassification cls = new NopMetaClassification();
        cls.setClassificationId("cls-1");
        cls.setAutoClassificationConfig("[{\"pattern\":\"user\",\"tagFQN\":\"cls-1:tag-a\"}]");
        when(clsDao.getEntityById("cls-1")).thenReturn(cls);

        NopMetaEntityField field = new NopMetaEntityField();
        field.setFieldName("user_name");
        when(fieldDao.findAllByQuery(any(QueryBean.class)))
                .thenReturn(List.of(field));

        NopMetaTag tagA = new NopMetaTag();
        tagA.setTagId("tag-a");
        tagA.setClassificationId("cls-1");
        when(tagDao.findFirstByQuery(any(QueryBean.class))).thenReturn(tagA);

        when(tagLabelDao.findFirstByQuery(any(QueryBean.class))).thenReturn(null);

        RuntimeException boom = new RuntimeException("save boom");
        IBizObject bizObject = mock(IBizObject.class);
        when(bizObjectManager.getBizObject("NopMetaTagLabel")).thenReturn(bizObject);
        when(bizObject.invoke(eq("save"), any(), any(), any())).thenThrow(boom);

        NopException ex = assertThrows(NopException.class,
                () -> classificationService.suggestTags("NopMetaTable", "entity-table-1", context),
                "user-triggered suggestTags must fail loudly to the request boundary (AR-21)");
        assertEquals(NopMetadataErrors.ERR_TAG_LABEL_SAVE_FAILED.getErrorCode(), ex.getErrorCode());
        assertSame(boom, ex.getCause(), "original exception must be preserved as cause");
        assertEquals("tag-a", ex.getParam(NopMetadataErrors.ARG_TAG_ID));
        assertEquals("entity-table-1", ex.getParam(NopMetadataErrors.ARG_ENTITY_ID));
    }

    /**
     * AR-21（plan 2026-08-06-1228-1 Phase 3）：无 Manual 标签绑定分类 → 不再任意回退
     * lexicographically-first 全局分类（修复前 allEnabled.sort(...).get(0) 会为无关表套用任意分类规则，
     * 该分类规则命中即产标签——本测试的 cls-a 规则必命中 user_name 字段，修复前返回非空实测 red）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAutoClassifyNoBoundClassificationNoArbitraryFallback() {
        NopMetaTable entityTable = new NopMetaTable();
        entityTable.setMetaTableId("entity-table-1");
        entityTable.setTableType("entity");
        entityTable.setBaseEntityId("entity-1");
        when(tableDao.getEntityById("entity-table-1")).thenReturn(entityTable);

        // 无 Manual 标签（tagLabelDao.findAllByQuery 默认返回空列表）
        NopMetaClassification clsA = new NopMetaClassification();
        clsA.setClassificationId("cls-a");
        clsA.setAutoClassificationConfig("[{\"pattern\":\"user\",\"tagFQN\":\"cls-a:tag-x\"}]");
        NopMetaClassification clsB = new NopMetaClassification();
        clsB.setClassificationId("cls-b");
        clsB.setAutoClassificationConfig("[{\"pattern\":\"user\",\"tagFQN\":\"cls-b:tag-y\"}]");
        when(clsDao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(clsA, clsB));

        NopMetaEntityField field = new NopMetaEntityField();
        field.setFieldName("user_name");
        when(fieldDao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(field));

        List<NopMetaTagLabel> result = classificationService.suggestTags("NopMetaTable", "entity-table-1", context);

        assertTrue(result.isEmpty(),
                "no bound classification must not fall back to lexicographically-first classification (AR-21): "
                        + result);
        // 未触发任何标签保存/提审（任意回退路径全被移除）
        Mockito.verify(bizObjectManager, Mockito.never()).getBizObject(any());
    }

    private static NopMetaTagLabel createTagLabel(String id, String entityType, String entityId,
                                                    String tagId, String labelType) {
        NopMetaTagLabel label = new NopMetaTagLabel();
        label.setTagLabelId(id);
        label.setEntityType(entityType);
        label.setEntityId(entityId);
        label.setTagId(tagId);
        label.setLabelType(labelType);
        label.setSource("test");
        return label;
    }

    private static NopMetaLineageEdge createLineageEdge(String edgeId, String sourceId, String targetId) {
        NopMetaLineageEdge edge = new NopMetaLineageEdge();
        edge.setLineageEdgeId(edgeId);
        edge.setSourceTableId(sourceId);
        edge.setTargetTableId(targetId);
        edge.setTransformType("DIRECT");
        return edge;
    }
}
