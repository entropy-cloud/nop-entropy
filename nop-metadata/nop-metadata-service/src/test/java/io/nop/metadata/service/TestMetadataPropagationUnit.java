
package io.nop.metadata.service;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
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
import io.nop.metadata.service.entity.AutoClassificationService;
import io.nop.metadata.service.entity.LineageTagPropagationService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestMetadataPropagationUnit {

    private LineageTagPropagationService propagationService;
    private AutoClassificationService classificationService;
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

        propagationService = new LineageTagPropagationService();
        propagationService.setDaoProvider(daoProvider);
        propagationService.setBizObjectManager(bizObjectManager);

        classificationService = new AutoClassificationService();
        classificationService.setDaoProvider(daoProvider);
        classificationService.setBizObjectManager(bizObjectManager);
    }

    // ===== LineageTagPropagationService Tests =====

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
                .thenReturn(null);

        when(bizObjectManager.getBizObject("NopMetaTagLabel"))
                .thenThrow(new RuntimeException("edge-1 fail")) // first edge fails
                .thenReturn(null); // second edge call - null means mock returns; but we need to handle the invoke

        List<NopMetaTagLabel> result = propagationService.propagateTags("NopMetaTable", "table-1", "tag-1", context);
        assertTrue(result.isEmpty());
    }

    // ===== AutoClassificationService Tests =====

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
