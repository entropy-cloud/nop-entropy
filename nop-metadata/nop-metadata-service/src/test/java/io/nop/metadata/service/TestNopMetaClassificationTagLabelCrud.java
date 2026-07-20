package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Phase 1 新增实体（Classification / Tag / TagLabel）的 GraphQL CRUD 操作，
 * 以及 TagLabel 按 entityType+entityId 查询能力。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaClassificationTagLabelCrud extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testClassificationCrud() {
        IEntityDao<NopMetaClassification> dao = daoProvider.daoFor(NopMetaClassification.class);

        // Create via DAO
        NopMetaClassification classification = dao.newEntity();
        String id = "cls-test-001";
        classification.setClassificationId(id);
        classification.setName("PII");
        classification.setDisplayName("敏感数据");
        classification.setMutuallyExclusive((byte) 0);
        classification.setProvider("system");
        classification.setDisabled((byte) 0);
        classification.setVersion(1L);
        classification.setCreatedBy("autotest");
        classification.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        classification.setCreateTime(now);
        classification.setUpdateTime(now);
        dao.saveEntity(classification);

        // Read via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaClassification__get(id: \"" + id + "\") { classificationId name displayName provider mutuallyExclusive } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        assertTrue(response.getData().toString().contains("PII"), "should contain name PII: " + response.getData());
        assertTrue(response.getData().toString().contains("system"), "should contain provider system: " + response.getData());

        // Verify created in DB
        NopMetaClassification created = dao.getEntityById(id);
        assertNotNull(created);
        assertEquals("PII", created.getName());

        // Delete via GraphQL
        response = execute(
                "mutation { NopMetaClassification__delete(id: \"" + id + "\") }");
        assertFalse(response.hasError(), "delete should not error: " + response);

        // Verify deleted
        assertNull(dao.getEntityById(id));
    }

    @Test
    public void testTagCrudWithParentAndClassification() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);

        // Create Classification first
        NopMetaClassification cls = clsDao.newEntity();
        cls.setClassificationId("cls-tag-001");
        cls.setName("Tier");
        cls.setDisplayName("服务等级");
        cls.setMutuallyExclusive((byte) 1);
        cls.setProvider("user");
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        clsDao.saveEntity(cls);

        // Create parent tag
        NopMetaTag parentTag = tagDao.newEntity();
        parentTag.setTagId("tag-parent-001");
        parentTag.setClassificationId("cls-tag-001");
        parentTag.setName("Platinum");
        parentTag.setFullyQualifiedName("Tier.Platinum");
        parentTag.setDisplayName("白金");
        parentTag.setVersion(1L);
        parentTag.setCreatedBy("autotest");
        parentTag.setUpdatedBy("autotest");
        parentTag.setCreateTime(now);
        parentTag.setUpdateTime(now);
        tagDao.saveEntity(parentTag);

        // Create child tag
        NopMetaTag childTag = tagDao.newEntity();
        childTag.setTagId("tag-child-001");
        childTag.setClassificationId("cls-tag-001");
        childTag.setParentTagId("tag-parent-001");
        childTag.setName("Priority1");
        childTag.setFullyQualifiedName("Tier.Platinum.Priority1");
        childTag.setDisplayName("优先级1");
        childTag.setVersion(1L);
        childTag.setCreatedBy("autotest");
        childTag.setUpdatedBy("autotest");
        childTag.setCreateTime(now);
        childTag.setUpdateTime(now);
        tagDao.saveEntity(childTag);

        // Read parent tag via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaTag__get(id: \"tag-parent-001\") { tagId name fullyQualifiedName classificationId parentTagId } }");
        assertFalse(response.hasError(), "get parent tag should not error: " + response);
        assertTrue(response.getData().toString().contains("Tier.Platinum"), "should contain FQN: " + response.getData());

        // Read child tag via GraphQL
        response = execute(
                "query { NopMetaTag__get(id: \"tag-child-001\") { tagId name fullyQualifiedName parentTagId } }");
        assertFalse(response.hasError(), "get child tag should not error: " + response);
        assertTrue(response.getData().toString().contains("Tier.Platinum.Priority1"), "should contain FQN: " + response.getData());
        assertTrue(response.getData().toString().contains("tag-parent-001"), "should contain parentTagId: " + response.getData());

        // Delete child then parent via GraphQL
        response = execute("mutation { NopMetaTag__delete(id: \"tag-child-001\") }");
        assertFalse(response.hasError(), "delete child tag should not error: " + response);
        response = execute("mutation { NopMetaTag__delete(id: \"tag-parent-001\") }");
        assertFalse(response.hasError(), "delete parent tag should not error: " + response);

        assertNull(tagDao.getEntityById("tag-child-001"));
        assertNull(tagDao.getEntityById("tag-parent-001"));
    }

    @Test
    public void testTagLabelCrudAndEntityQuery() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);

        // Setup: Classification + Tag
        NopMetaClassification cls = clsDao.newEntity();
        cls.setClassificationId("cls-label-001");
        cls.setName("DataQuality");
        cls.setDisplayName("数据质量");
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("system");
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        clsDao.saveEntity(cls);

        NopMetaTag tag = tagDao.newEntity();
        tag.setTagId("tag-label-001");
        tag.setClassificationId("cls-label-001");
        tag.setName("Trusted");
        tag.setFullyQualifiedName("DataQuality.Trusted");
        tag.setDisplayName("可信");
        tag.setVersion(1L);
        tag.setCreatedBy("autotest");
        tag.setUpdatedBy("autotest");
        tag.setCreateTime(now);
        tag.setUpdateTime(now);
        tagDao.saveEntity(tag);

        // Create TagLabel via DAO
        NopMetaTagLabel label = labelDao.newEntity();
        String labelId = "tlabel-001";
        label.setTagLabelId(labelId);
        label.setSource("Classification");
        label.setTagId("tag-label-001");
        label.setLabelType("Manual");
        label.setState("Confirmed");
        label.setEntityType("NopMetaEntityField");
        label.setEntityId("field-001");
        label.setAppliedBy("autotest");
        label.setAppliedAt(now);
        label.setReason("test annotation");
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        label.setCreateTime(now);
        label.setUpdateTime(now);
        labelDao.saveEntity(label);

        // Read via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaTagLabel__get(id: \"" + labelId + "\") { tagLabelId source tagId labelType state entityType entityId } }");
        assertFalse(response.hasError(), "get tagLabel should not error: " + response);
        assertTrue(response.getData().toString().contains("Classification"), "should contain source: " + response.getData());
        assertTrue(response.getData().toString().contains("Confirmed"), "should contain state: " + response.getData());

        // Query by entityType + entityId via DAO
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, "NopMetaEntityField"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, "field-001"));
        List<NopMetaTagLabel> labels = labelDao.findAllByQuery(q);
        assertEquals(1, labels.size(), "should find 1 TagLabel by entityType+entityId");
        assertEquals(labelId, labels.get(0).getTagLabelId());

        // Delete via GraphQL
        response = execute("mutation { NopMetaTagLabel__delete(id: \"" + labelId + "\") }");
        assertFalse(response.hasError(), "delete tagLabel should not error: " + response);

        // Verify deleted
        assertNull(labelDao.getEntityById(labelId));
    }

    @Test
    public void testTagLabelEntityQueryReturnsMultiple() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);

        // Setup
        NopMetaClassification cls = clsDao.newEntity();
        cls.setClassificationId("cls-multi-001");
        cls.setName("MultiTag");
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("user");
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        clsDao.saveEntity(cls);

        for (int i = 1; i <= 3; i++) {
            NopMetaTag t = tagDao.newEntity();
            t.setTagId("tag-multi-" + i);
            t.setClassificationId("cls-multi-001");
            t.setName("Tag" + i);
            t.setFullyQualifiedName("MultiTag.Tag" + i);
            t.setVersion(1L);
            t.setCreatedBy("autotest");
            t.setUpdatedBy("autotest");
            t.setCreateTime(now);
            t.setUpdateTime(now);
            tagDao.saveEntity(t);

            NopMetaTagLabel l = labelDao.newEntity();
            l.setTagLabelId("tlabel-multi-" + i);
            l.setSource("Classification");
            l.setTagId("tag-multi-" + i);
            l.setLabelType("Manual");
            l.setState("Confirmed");
            l.setEntityType("MetaTable");
            l.setEntityId("multi-table-001");
            l.setVersion(1L);
            l.setCreatedBy("autotest");
            l.setUpdatedBy("autotest");
            l.setCreateTime(now);
            l.setUpdateTime(now);
            labelDao.saveEntity(l);
        }

        // Query all TagLabels for entityType=MetaTable + entityId=multi-table-001
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, "MetaTable"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, "multi-table-001"));
        List<NopMetaTagLabel> labels = labelDao.findAllByQuery(q);
        assertEquals(3, labels.size(), "should find 3 TagLabels by entityType+entityId");
        for (NopMetaTagLabel l : labels) {
            assertEquals("MetaTable", l.getEntityType());
            assertEquals("multi-table-001", l.getEntityId());
        }
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
