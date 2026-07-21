
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.api.core.beans.graphql.GraphQLErrorBean;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMetadataPropagationIntegration extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private Timestamp now = new Timestamp(System.currentTimeMillis());

    @Test
    public void testPropagateTagsEndToEnd() {
        // Setup: Table A with Manual TagLabel + DIRECT edge A -> B
        String tableAId = "prop-table-a-" + UUID.randomUUID().toString().substring(0, 8);
        String tableBId = "prop-table-b-" + UUID.randomUUID().toString().substring(0, 8);
        String clsId = "prop-cls-" + UUID.randomUUID().toString().substring(0, 8);
        String tagId = "prop-tag-" + UUID.randomUUID().toString().substring(0, 8);
        String edgeId = "prop-edge-" + UUID.randomUUID().toString().substring(0, 8);

        createClassification(clsId, "PropagationTest");
        createTag(tagId, clsId, "propagation-tag", "PropagationTest.propagation-tag");
        createTable(tableAId, "prop_table_a", "entity", null);
        createTable(tableBId, "prop_table_b", "entity", null);
        createLineageEdge(edgeId, tableAId, tableBId, "DIRECT");
        createManualTagLabel("tlabel-prop-m-" + UUID.randomUUID().toString().substring(0, 8),
                "NopMetaTable", tableAId, tagId);

        // Call propagateTags mutation
        GraphQLResponseBean resp = execute(
                "mutation($entityType:String,$entityId:String,$tagId:String) { " +
                        "NopMetaTagLabel__propagateTags(entityType:$entityType,entityId:$entityId,tagId:$tagId) { " +
                        "tagLabelId source tagId labelType state entityType entityId } }",
                Map.of("entityType", "NopMetaTable", "entityId", tableAId, "tagId", tagId));
        assertFalse(resp.hasError(), "propagateTags failed: " + getErrorMessages(resp));

        // Verify TagLabel created on table B with state=Suggested, source=lineage-propagation
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        List<NopMetaTagLabel> labelsB = findTagLabels("NopMetaTable", tableBId);
        boolean found = labelsB.stream().anyMatch(l ->
                "lineage-propagation".equals(l.getSource())
                        && "Propagated".equals(l.getLabelType())
                        && "Suggested".equals(l.getState())
                        && tagId.equals(l.getTagId()));
        assertTrue(found, "Propagated TagLabel should exist on table B: " + labelsB);
    }

    @Test
    public void testPropagateTagsWithSpecificTag() {
        String tableAId = "prop-ta-" + UUID.randomUUID().toString().substring(0, 8);
        String tableBId = "prop-tb-" + UUID.randomUUID().toString().substring(0, 8);
        String clsId = "prop-cls2-" + UUID.randomUUID().toString().substring(0, 8);
        String tag1Id = "prop-tag1-" + UUID.randomUUID().toString().substring(0, 8);
        String tag2Id = "prop-tag2-" + UUID.randomUUID().toString().substring(0, 8);
        String edgeId = "prop-ed2-" + UUID.randomUUID().toString().substring(0, 8);

        createClassification(clsId, "PropagationTest2");
        createTag(tag1Id, clsId, "tag-one", "PropagationTest2.tag-one");
        createTag(tag2Id, clsId, "tag-two", "PropagationTest2.tag-two");
        createTable(tableAId, "prop_ta", "entity", null);
        createTable(tableBId, "prop_tb", "entity", null);
        createLineageEdge(edgeId, tableAId, tableBId, "DIRECT");
        createManualTagLabel("tlabel-pm2-" + UUID.randomUUID().toString().substring(0, 8),
                "NopMetaTable", tableAId, tag1Id);
        createManualTagLabel("tlabel-pm3-" + UUID.randomUUID().toString().substring(0, 8),
                "NopMetaTable", tableAId, tag2Id);

        // Propagate only tag1
        GraphQLResponseBean resp = execute(
                "mutation($entityType:String,$entityId:String,$tagId:String) { " +
                        "NopMetaTagLabel__propagateTags(entityType:$entityType,entityId:$entityId,tagId:$tagId) { tagLabelId tagId } }",
                Map.of("entityType", "NopMetaTable", "entityId", tableAId, "tagId", tag1Id));
        assertFalse(resp.hasError(), "propagateTags specific tag failed: " + getErrorMessages(resp));

        List<NopMetaTagLabel> labelsB = findTagLabels("NopMetaTable", tableBId);
        boolean hasTag1 = labelsB.stream().anyMatch(l -> tag1Id.equals(l.getTagId()) && "lineage-propagation".equals(l.getSource()));
        boolean hasTag2 = labelsB.stream().anyMatch(l -> tag2Id.equals(l.getTagId()) && "lineage-propagation".equals(l.getSource()));
        assertTrue(hasTag1, "Tag1 should be propagated: " + labelsB);
        assertFalse(hasTag2, "Tag2 should NOT be propagated: " + labelsB);
    }

    @Test
    public void testSuggestTagsEndToEnd() {
        String clsId = "ac-cls-" + UUID.randomUUID().toString().substring(0, 8);
        String tagId = "ac-tag-" + UUID.randomUUID().toString().substring(0, 8);
        String entityId = "ac-entity-" + UUID.randomUUID().toString().substring(0, 8);
        String tableId = "ac-table-" + UUID.randomUUID().toString().substring(0, 8);
        String fieldId = "ac-field-" + UUID.randomUUID().toString().substring(0, 8);

        String configJson = "[{\"pattern\":\"phone\",\"tagFQN\":\"AutoClassifyTest.phone-tag\",\"priority\":1,\"fieldTypeFilter\":\"VARCHAR\"}]";

        createClassification(clsId, "AutoClassifyTest", configJson);
        createTag(tagId, clsId, "phone-tag", "AutoClassifyTest.phone-tag");
        createEntity(entityId, "test_entity");
        createEntityField(fieldId, entityId, "phone_number", "VARCHAR");
        createTable(tableId, "test_table", "entity", entityId);

        createManualTagLabel("tlabel-acm-" + UUID.randomUUID().toString().substring(0, 8),
                "NopMetaTable", tableId, tagId);

        GraphQLResponseBean resp = execute(
                "mutation($entityType:String,$entityId:String) { " +
                        "NopMetaTagLabel__suggestTags(entityType:$entityType,entityId:$entityId) { tagId source labelType state entityId } }",
                Map.of("entityType", "NopMetaTable", "entityId", tableId));
        assertFalse(resp.hasError(), "suggestTags failed: " + getErrorMessages(resp));

        List<NopMetaTagLabel> labels = findTagLabels("NopMetaTable", tableId);
        boolean found = labels.stream().anyMatch(l ->
                "auto-classify".equals(l.getSource())
                        && "Automated".equals(l.getLabelType())
                        && "Suggested".equals(l.getState())
                        && tagId.equals(l.getTagId()));
        assertTrue(found, "Automated TagLabel from auto-classify should exist: " + labels);
    }

    @Test
    public void testSuggestTagsNoMatchingField() {
        String clsId = "ac-cls2-" + UUID.randomUUID().toString().substring(0, 8);
        String tagId = "ac-tag2-" + UUID.randomUUID().toString().substring(0, 8);
        String entityId = "ac-entity2-" + UUID.randomUUID().toString().substring(0, 8);
        String tableId = "ac-table2-" + UUID.randomUUID().toString().substring(0, 8);
        String fieldId = "ac-field2-" + UUID.randomUUID().toString().substring(0, 8);

        String configJson = "[{\"pattern\":\"email\",\"tagFQN\":\"AutoClassifyTest.email-tag\",\"priority\":1}]";

        createClassification(clsId, "AutoClassifyTest2", configJson);
        createTag(tagId, clsId, "email-tag", "AutoClassifyTest2.email-tag");
        createEntity(entityId, "test_entity_no_match");
        createEntityField(fieldId, entityId, "phone_number", "VARCHAR");
        createTable(tableId, "test_table_no_match", "entity", entityId);

        createManualTagLabel("tlabel-acm2-" + UUID.randomUUID().toString().substring(0, 8),
                "NopMetaTable", tableId, tagId);

        GraphQLResponseBean resp = execute(
                "mutation($entityType:String,$entityId:String) { " +
                        "NopMetaTagLabel__suggestTags(entityType:$entityType,entityId:$entityId) { tagId } }",
                Map.of("entityType", "NopMetaTable", "entityId", tableId));
        assertFalse(resp.hasError(), "suggestTags no match failed: " + getErrorMessages(resp));
    }

    // ===== helpers =====

    private void createClassification(String id, String name) {
        createClassification(id, name, null);
    }

    private void createClassification(String id, String name, String autoClassificationConfig) {
        IEntityDao<NopMetaClassification> dao = daoProvider.daoFor(NopMetaClassification.class);
        NopMetaClassification cls = dao.newEntity();
        cls.setClassificationId(id);
        cls.setName(name);
        cls.setDisplayName(name);
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("system");
        cls.setAutoClassificationConfig(autoClassificationConfig);
        dao.saveEntity(cls);
        dao.flushSession();
    }

    private void createTag(String tagId, String classificationId, String name, String fqn) {
        IEntityDao<NopMetaTag> dao = daoProvider.daoFor(NopMetaTag.class);
        NopMetaTag tag = dao.newEntity();
        tag.setTagId(tagId);
        tag.setClassificationId(classificationId);
        tag.setName(name);
        tag.setFullyQualifiedName(fqn);
        dao.saveEntity(tag);
        dao.flushSession();
    }

    private String ensureTestModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        String moduleName = "test-module-propagation";
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(NopMetaModule.PROP_NAME_moduleName, moduleName));
        NopMetaModule existing = dao.findFirstByQuery(q);
        if (existing != null) {
            return existing.getMetaModuleId();
        }
        NopMetaModule module = dao.newEntity();
        module.setModuleId("nop/" + moduleName);
        module.setModuleName(moduleName);
        module.setDisplayName(moduleName);
        module.setModuleVersion(1L);
        module.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(module);
        dao.flushSession();
        return module.getMetaModuleId();
    }

    private String createTable(String id, String tableName, String tableType, String baseEntityId) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable table = dao.newEntity();
        table.setMetaTableId(id);
        table.setMetaModuleId(ensureTestModuleId());
        table.setTableName(tableName);
        table.setTableType(tableType);
        table.setBaseEntityId(baseEntityId);
        dao.saveEntity(table);
        dao.flushSession();
        return id;
    }

    private void createLineageEdge(String edgeId, String sourceTableId, String targetTableId, String transformType) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        NopMetaLineageEdge edge = dao.newEntity();
        edge.setLineageEdgeId(edgeId);
        edge.setSourceTableId(sourceTableId);
        edge.setTargetTableId(targetTableId);
        edge.setTransformType(transformType);
        dao.saveEntity(edge);
        dao.flushSession();
    }

    private void createManualTagLabel(String labelId, String entityType, String entityId, String tagId) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        NopMetaTagLabel label = dao.newEntity();
        label.setTagLabelId(labelId);
        label.setSource("Classification");
        label.setTagId(tagId);
        label.setLabelType("Manual");
        label.setState("Confirmed");
        label.setEntityType(entityType);
        label.setEntityId(entityId);
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        label.setCreateTime(now);
        label.setUpdateTime(now);
        dao.saveEntity(label);
        dao.flushSession();
    }

    private String ensureOrmModelId() {
        IEntityDao<NopMetaOrmModel> dao = daoProvider.daoFor(NopMetaOrmModel.class);
        String modelName = "test-orm-propagation";
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(NopMetaOrmModel.PROP_NAME_modelName, modelName));
        NopMetaOrmModel existing = dao.findFirstByQuery(q);
        if (existing != null) {
            return existing.getOrmModelId();
        }
        NopMetaOrmModel orm = dao.newEntity();
        orm.setMetaModuleId(ensureTestModuleId());
        orm.setModelName(modelName);
        orm.setIsDelta((byte) 0);
        orm.setVersion(1L);
        orm.setCreatedBy("autotest");
        orm.setUpdatedBy("autotest");
        orm.setCreateTime(now);
        orm.setUpdateTime(now);
        dao.saveEntity(orm);
        dao.flushSession();
        return orm.getOrmModelId();
    }

    private void createEntity(String entityId, String entityName) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.newEntity();
        entity.setMetaEntityId(entityId);
        entity.setOrmModelId(ensureOrmModelId());
        entity.setEntityName(entityName);
        entity.setTableName("tbl_" + entityName);
        entity.setClassName("io.test." + entityName);
        entity.setVersion(1L);
        entity.setCreatedBy("autotest");
        entity.setUpdatedBy("autotest");
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        dao.saveEntity(entity);
        dao.flushSession();
    }

    private void createEntityField(String fieldId, String metaEntityId, String fieldName, String stdDataType) {
        IEntityDao<NopMetaEntityField> dao = daoProvider.daoFor(NopMetaEntityField.class);
        NopMetaEntityField field = dao.newEntity();
        field.setEntityFieldId(fieldId);
        field.setMetaEntityId(metaEntityId);
        field.setFieldName(fieldName);
        field.setStdDataType(stdDataType);
        field.setVersion(1L);
        field.setCreatedBy("autotest");
        field.setUpdatedBy("autotest");
        field.setCreateTime(now);
        field.setUpdateTime(now);
        dao.saveEntity(field);
        dao.flushSession();
    }

    private List<NopMetaTagLabel> findTagLabels(String entityType, String entityId) {
        return daoProvider.daoFor(NopMetaTagLabel.class).findAllByQuery(
                new io.nop.api.core.beans.query.QueryBean()
                        .addFilter(io.nop.api.core.beans.FilterBeans.eq("entityType", entityType))
                        .addFilter(io.nop.api.core.beans.FilterBeans.eq("entityId", entityId)));
    }

    private GraphQLResponseBean execute(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private String getErrorMessages(GraphQLResponseBean resp) {
        if (!resp.hasError()) return "";
        StringBuilder sb = new StringBuilder();
        for (GraphQLErrorBean e : resp.getErrors()) {
            sb.append(e.getMessage()).append("; ");
        }
        return sb.toString();
    }
}
