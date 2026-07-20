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
import io.nop.metadata.dao.entity.NopMetaGlossary;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
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
 * 验证 Phase 1 新增实体（Glossary / GlossaryTerm）的 GraphQL CRUD 操作，
 * 以及 TagLabel 按 glossaryTermId 查询能力、Glossary.to-many childTerms 关系。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaGlossaryCrud extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testGlossaryCrud() {
        IEntityDao<NopMetaGlossary> dao = daoProvider.daoFor(NopMetaGlossary.class);

        // Create via DAO
        NopMetaGlossary glossary = dao.newEntity();
        String id = "glossary-test-001";
        glossary.setGlossaryId(id);
        glossary.setName("BusinessTerms");
        glossary.setDisplayName("业务术语表");
        glossary.setOwner("autotest");
        glossary.setMutuallyExclusive((byte) 0);
        glossary.setVersion(1L);
        glossary.setCreatedBy("autotest");
        glossary.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        glossary.setCreateTime(now);
        glossary.setUpdateTime(now);
        dao.saveEntity(glossary);

        // Read via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaGlossary__get(id: \"" + id + "\") { glossaryId name displayName owner mutuallyExclusive } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        assertTrue(response.getData().toString().contains("BusinessTerms"), "should contain name: " + response.getData());

        // Verify created in DB
        NopMetaGlossary created = dao.getEntityById(id);
        assertNotNull(created);
        assertEquals("BusinessTerms", created.getName());

        // Delete via GraphQL
        response = execute("mutation { NopMetaGlossary__delete(id: \"" + id + "\") }");
        assertFalse(response.hasError(), "delete should not error: " + response);

        // Verify deleted
        assertNull(dao.getEntityById(id));
    }

    @Test
    public void testGlossaryTermWithParent() {
        IEntityDao<NopMetaGlossary> glossaryDao = daoProvider.daoFor(NopMetaGlossary.class);
        IEntityDao<NopMetaGlossaryTerm> termDao = daoProvider.daoFor(NopMetaGlossaryTerm.class);

        // Create Glossary first
        NopMetaGlossary glossary = glossaryDao.newEntity();
        glossary.setGlossaryId("glossary-term-001");
        glossary.setName("DataDomains");
        glossary.setDisplayName("数据域");
        glossary.setVersion(1L);
        glossary.setCreatedBy("autotest");
        glossary.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        glossary.setCreateTime(now);
        glossary.setUpdateTime(now);
        glossaryDao.saveEntity(glossary);

        // Create parent term
        NopMetaGlossaryTerm parentTerm = termDao.newEntity();
        parentTerm.setGlossaryTermId("term-parent-001");
        parentTerm.setGlossaryId("glossary-term-001");
        parentTerm.setName("CustomerData");
        parentTerm.setFullyQualifiedName("CustomerData");
        parentTerm.setDisplayName("客户数据");
        parentTerm.setVersion(1L);
        parentTerm.setCreatedBy("autotest");
        parentTerm.setUpdatedBy("autotest");
        parentTerm.setCreateTime(now);
        parentTerm.setUpdateTime(now);
        termDao.saveEntity(parentTerm);

        // Create child term (self-ref hierarchy)
        NopMetaGlossaryTerm childTerm = termDao.newEntity();
        childTerm.setGlossaryTermId("term-child-001");
        childTerm.setGlossaryId("glossary-term-001");
        childTerm.setParentTermId("term-parent-001");
        childTerm.setName("CustomerName");
        childTerm.setFullyQualifiedName("CustomerData.CustomerName");
        childTerm.setDisplayName("客户姓名");
        childTerm.setVersion(1L);
        childTerm.setCreatedBy("autotest");
        childTerm.setUpdatedBy("autotest");
        childTerm.setCreateTime(now);
        childTerm.setUpdateTime(now);
        termDao.saveEntity(childTerm);

        // Read parent term via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaGlossaryTerm__get(id: \"term-parent-001\") { glossaryTermId name fullyQualifiedName } }");
        assertFalse(response.hasError(), "get parent term should not error: " + response);
        assertTrue(response.getData().toString().contains("CustomerData"), "should contain FQN: " + response.getData());

        // Read child term via GraphQL
        response = execute(
                "query { NopMetaGlossaryTerm__get(id: \"term-child-001\") { glossaryTermId name fullyQualifiedName parentTermId } }");
        assertFalse(response.hasError(), "get child term should not error: " + response);
        assertTrue(response.getData().toString().contains("CustomerData.CustomerName"), "should contain FQN: " + response.getData());
        assertTrue(response.getData().toString().contains("term-parent-001"), "should contain parentTermId: " + response.getData());

        // Delete child then parent via GraphQL
        response = execute("mutation { NopMetaGlossaryTerm__delete(id: \"term-child-001\") }");
        assertFalse(response.hasError(), "delete child term should not error: " + response);
        response = execute("mutation { NopMetaGlossaryTerm__delete(id: \"term-parent-001\") }");
        assertFalse(response.hasError(), "delete parent term should not error: " + response);

        assertNull(termDao.getEntityById("term-child-001"));
        assertNull(termDao.getEntityById("term-parent-001"));
    }

    @Test
    public void testTagLabelWithGlossaryTermId() {
        IEntityDao<NopMetaGlossary> glossaryDao = daoProvider.daoFor(NopMetaGlossary.class);
        IEntityDao<NopMetaGlossaryTerm> termDao = daoProvider.daoFor(NopMetaGlossaryTerm.class);
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);

        // Setup: Glossary + GlossaryTerm
        NopMetaGlossary glossary = glossaryDao.newEntity();
        glossary.setGlossaryId("glossary-label-001");
        glossary.setName("SecurityTerms");
        glossary.setDisplayName("安全术语");
        glossary.setVersion(1L);
        glossary.setCreatedBy("autotest");
        glossary.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        glossary.setCreateTime(now);
        glossary.setUpdateTime(now);
        glossaryDao.saveEntity(glossary);

        NopMetaGlossaryTerm term = termDao.newEntity();
        term.setGlossaryTermId("term-label-001");
        term.setGlossaryId("glossary-label-001");
        term.setName("PII");
        term.setFullyQualifiedName("PII");
        term.setDisplayName("个人隐私信息");
        term.setVersion(1L);
        term.setCreatedBy("autotest");
        term.setUpdatedBy("autotest");
        term.setCreateTime(now);
        term.setUpdateTime(now);
        termDao.saveEntity(term);

        // Create TagLabel with glossaryTermId reference
        NopMetaTagLabel label = labelDao.newEntity();
        String labelId = "tlabel-glossary-001";
        label.setTagLabelId(labelId);
        label.setSource("Glossary");
        label.setGlossaryTermId("term-label-001");
        label.setLabelType("Manual");
        label.setState("Confirmed");
        label.setEntityType("NopMetaEntityField");
        label.setEntityId("field-glossary-001");
        label.setAppliedBy("autotest");
        label.setAppliedAt(now);
        label.setReason("test glossary annotation");
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        label.setCreateTime(now);
        label.setUpdateTime(now);
        labelDao.saveEntity(label);

        // Read via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaTagLabel__get(id: \"" + labelId + "\") { tagLabelId source glossaryTermId labelType state entityType entityId } }");
        assertFalse(response.hasError(), "get tagLabel should not error: " + response);
        assertTrue(response.getData().toString().contains("term-label-001"), "should contain glossaryTermId: " + response.getData());
        assertTrue(response.getData().toString().contains("Glossary"), "should contain source: " + response.getData());

        // Query by glossaryTermId via DAO
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_glossaryTermId, "term-label-001"));
        List<NopMetaTagLabel> labels = labelDao.findAllByQuery(q);
        assertEquals(1, labels.size(), "should find 1 TagLabel by glossaryTermId");
        assertEquals(labelId, labels.get(0).getTagLabelId());

        // Delete via GraphQL
        response = execute("mutation { NopMetaTagLabel__delete(id: \"" + labelId + "\") }");
        assertFalse(response.hasError(), "delete tagLabel should not error: " + response);

        // Verify deleted
        assertNull(labelDao.getEntityById(labelId));
    }

    @Test
    public void testGlossaryToChildTermsRelation() {
        IEntityDao<NopMetaGlossary> glossaryDao = daoProvider.daoFor(NopMetaGlossary.class);
        IEntityDao<NopMetaGlossaryTerm> termDao = daoProvider.daoFor(NopMetaGlossaryTerm.class);

        // Create Glossary
        NopMetaGlossary glossary = glossaryDao.newEntity();
        glossary.setGlossaryId("glossary-relation-001");
        glossary.setName("RelationTest");
        glossary.setDisplayName("关系测试");
        glossary.setVersion(1L);
        glossary.setCreatedBy("autotest");
        glossary.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        glossary.setCreateTime(now);
        glossary.setUpdateTime(now);
        glossaryDao.saveEntity(glossary);

        // Create multiple GlossaryTerms under the same Glossary
        for (int i = 1; i <= 3; i++) {
            NopMetaGlossaryTerm term = termDao.newEntity();
            term.setGlossaryTermId("term-relation-" + i);
            term.setGlossaryId("glossary-relation-001");
            term.setName("Term" + i);
            term.setFullyQualifiedName("RelationTest.Term" + i);
            term.setDisplayName("术语" + i);
            term.setVersion(1L);
            term.setCreatedBy("autotest");
            term.setUpdatedBy("autotest");
            term.setCreateTime(now);
            term.setUpdateTime(now);
            termDao.saveEntity(term);
        }

        // Read Glossary with childTerms relation via GraphQL
        GraphQLResponseBean response = execute(
                "query { NopMetaGlossary__get(id: \"glossary-relation-001\") { glossaryId name childTerms { glossaryTermId name } } }");
        assertFalse(response.hasError(), "get glossary with childTerms should not error: " + response);
        String data = response.getData().toString();
        assertTrue(data.contains("term-relation-1"), "should contain child term 1: " + data);
        assertTrue(data.contains("term-relation-2"), "should contain child term 2: " + data);
        assertTrue(data.contains("term-relation-3"), "should contain child term 3: " + data);
        assertTrue(data.contains("Term1"), "should contain child name Term1: " + data);
        assertTrue(data.contains("Term2"), "should contain child name Term2: " + data);
        assertTrue(data.contains("Term3"), "should contain child name Term3: " + data);
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
