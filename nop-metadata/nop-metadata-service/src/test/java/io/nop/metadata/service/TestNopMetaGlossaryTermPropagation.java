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
import io.nop.metadata.dao.entity.NopMetaGlossary;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaGlossaryTermPropagation extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private Timestamp now = new Timestamp(System.currentTimeMillis());

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private GraphQLResponseBean executeWithVars(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private GraphQLResponseBean saveGlossaryTermViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaGlossaryTerm__save(data:$data) { glossaryTermId tags } }",
                vars);
    }

    private GraphQLResponseBean saveTagLabelViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId source glossaryTermId } }",
                vars);
    }

    private GraphQLResponseBean updateGlossaryTermViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaGlossaryTerm__update(data:$data) { glossaryTermId tags } }",
                vars);
    }

    private GraphQLResponseBean updateTagLabelViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaTagLabel__update(data:$data) { tagLabelId source glossaryTermId } }",
                vars);
    }

    private void createGlossary(String glossaryId, String name) {
        IEntityDao<NopMetaGlossary> dao = daoProvider.daoFor(NopMetaGlossary.class);
        NopMetaGlossary g = dao.newEntity();
        g.setGlossaryId(glossaryId);
        g.setName(name);
        g.setDisplayName(name);
        g.setVersion(1L);
        g.setCreatedBy("autotest");
        g.setUpdatedBy("autotest");
        g.setCreateTime(now);
        g.setUpdateTime(now);
        dao.saveEntity(g);
    }

    private void createClassification(String classificationId, String name) {
        IEntityDao<NopMetaClassification> dao = daoProvider.daoFor(NopMetaClassification.class);
        NopMetaClassification c = dao.newEntity();
        c.setClassificationId(classificationId);
        c.setName(name);
        c.setDisplayName(name);
        c.setMutuallyExclusive((byte) 0);
        c.setProvider("system");
        c.setDisabled((byte) 0);
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
    }

    private void createTag(String tagId, String classificationId, String name) {
        IEntityDao<NopMetaTag> dao = daoProvider.daoFor(NopMetaTag.class);
        NopMetaTag t = dao.newEntity();
        t.setTagId(tagId);
        t.setClassificationId(classificationId);
        t.setName(name);
        t.setFullyQualifiedName(name);
        t.setDisplayName(name);
        t.setVersion(1L);
        t.setCreatedBy("autotest");
        t.setUpdatedBy("autotest");
        t.setCreateTime(now);
        t.setUpdateTime(now);
        dao.saveEntity(t);
    }

    private long countDerivedLabels(String glossaryTermId) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, "NopMetaGlossaryTerm"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, glossaryTermId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Derived"));
        return dao.findAllByQuery(q).size();
    }

    private long countDerivedLabelsForTarget(String entityType, String entityId) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Derived"));
        return dao.findAllByQuery(q).size();
    }

    // ===== Path A: GlossaryTerm.save → TagLabel propagation =====

    @Test
    public void testPathACreateGlossaryTermWithTags() {
        createGlossary("gl-prop-a-1", "PropTestA");
        createClassification("cls-prop-a-1", "DataClass");
        createTag("tag-prop-a-1", "cls-prop-a-1", "Confidential");

        Map<String, Object> data = new HashMap<>();
        data.put("glossaryTermId", "gt-prop-a-1");
        data.put("glossaryId", "gl-prop-a-1");
        data.put("name", "TermA");
        data.put("tags", "[\"tag-prop-a-1\"]");
        data.put("version", 1);

        GraphQLResponseBean resp = saveGlossaryTermViaGql(data);
        assertFalse(resp.hasError(), "save should succeed: " + resp);

        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, "NopMetaGlossaryTerm"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, "gt-prop-a-1"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Derived"));
        List<NopMetaTagLabel> labels = labelDao.findAllByQuery(q);
        assertEquals(1, labels.size(), "should create 1 derived TagLabel");
        NopMetaTagLabel label = labels.get(0);
        assertEquals("tag-prop-a-1", label.getTagId());
        assertEquals("Classification", label.getSource());
        assertEquals("Suggested", label.getState());
    }

    @Test
    public void testPathAUpdateTagsAddsNewTagLabel() {
        createGlossary("gl-prop-a-2", "PropTestA2");
        createClassification("cls-prop-a-2", "DataClass2");
        createTag("tag-prop-a-2a", "cls-prop-a-2", "TagA");
        createTag("tag-prop-a-2b", "cls-prop-a-2", "TagB");

        Map<String, Object> data1 = new HashMap<>();
        data1.put("glossaryTermId", "gt-prop-a-2");
        data1.put("glossaryId", "gl-prop-a-2");
        data1.put("name", "TermA");
        data1.put("tags", "[\"tag-prop-a-2a\"]");
        data1.put("version", 1);
        saveGlossaryTermViaGql(data1);
        assertEquals(1, countDerivedLabels("gt-prop-a-2"), "should have 1 label after first save");

        Map<String, Object> data2 = new HashMap<>();
        data2.put("id", "gt-prop-a-2");
        data2.put("glossaryId", "gl-prop-a-2");
        data2.put("name", "TermA");
        data2.put("tags", "[\"tag-prop-a-2a\",\"tag-prop-a-2b\"]");
        data2.put("version", 1);
        GraphQLResponseBean resp2 = updateGlossaryTermViaGql(data2);
        assertFalse(resp2.hasError(), "update should succeed: " + resp2);
        assertEquals(2, countDerivedLabels("gt-prop-a-2"), "should have 2 labels after adding tag-prop-a-2b");
    }

    @Test
    public void testPathAUpdateTagsRemovesStaleLabels() {
        createGlossary("gl-prop-a-3", "PropTestA3");
        createClassification("cls-prop-a-3", "DataClass3");
        createTag("tag-prop-a-3a", "cls-prop-a-3", "TagA");
        createTag("tag-prop-a-3b", "cls-prop-a-3", "TagB");

        Map<String, Object> data1 = new HashMap<>();
        data1.put("glossaryTermId", "gt-prop-a-3");
        data1.put("glossaryId", "gl-prop-a-3");
        data1.put("name", "TermA");
        data1.put("tags", "[\"tag-prop-a-3a\",\"tag-prop-a-3b\"]");
        data1.put("version", 1);
        saveGlossaryTermViaGql(data1);
        assertEquals(2, countDerivedLabels("gt-prop-a-3"), "should have 2 labels initially");

        Map<String, Object> data2 = new HashMap<>();
        data2.put("id", "gt-prop-a-3");
        data2.put("glossaryId", "gl-prop-a-3");
        data2.put("name", "TermA");
        data2.put("tags", "[]");
        data2.put("version", 1);
        updateGlossaryTermViaGql(data2);
        assertEquals(0, countDerivedLabels("gt-prop-a-3"), "should clean up all labels when tags cleared");
    }

    @Test
    public void testPathAIdempotentSaveNoDuplicates() {
        createGlossary("gl-prop-a-4", "PropTestA4");
        createClassification("cls-prop-a-4", "DataClass4");
        createTag("tag-prop-a-4a", "cls-prop-a-4", "TagA");

        Map<String, Object> data = new HashMap<>();
        data.put("glossaryTermId", "gt-prop-a-4");
        data.put("glossaryId", "gl-prop-a-4");
        data.put("name", "TermA");
        data.put("tags", "[\"tag-prop-a-4a\"]");
        data.put("version", 1);

        saveGlossaryTermViaGql(data);
        assertEquals(1, countDerivedLabels("gt-prop-a-4"), "should have 1 label after first save");

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", "gt-prop-a-4");
        updateData.put("glossaryId", "gl-prop-a-4");
        updateData.put("name", "TermA");
        updateData.put("tags", "[\"tag-prop-a-4a\"]");
        updateData.put("version", 1);
        updateGlossaryTermViaGql(updateData);
        assertEquals(1, countDerivedLabels("gt-prop-a-4"), "should still have 1 label after idempotent update");
    }

    // ===== Path B: TagLabel with source=Glossary → derived TagLabels =====

    @Test
    public void testPathBGlossaryTagLabelCreatesDerivedLabels() {
        createGlossary("gl-prop-b-1", "PropTestB1");
        createClassification("cls-prop-b-1", "DataClassB1");
        createTag("tag-prop-b-1a", "cls-prop-b-1", "TagPII");

        Map<String, Object> termData = new HashMap<>();
        termData.put("glossaryTermId", "gt-prop-b-1");
        termData.put("glossaryId", "gl-prop-b-1");
        termData.put("name", "TermPII");
        termData.put("tags", "[\"tag-prop-b-1a\"]");
        termData.put("version", 1);
        saveGlossaryTermViaGql(termData);

        Map<String, Object> labelData = new HashMap<>();
        labelData.put("tagLabelId", "tl-prop-b-1");
        labelData.put("source", "Glossary");
        labelData.put("glossaryTermId", "gt-prop-b-1");
        labelData.put("tagId", "tag-prop-b-1a");
        labelData.put("labelType", "Manual");
        labelData.put("state", "Confirmed");
        labelData.put("entityType", "NopMetaEntityField");
        labelData.put("entityId", "field-b-001");
        labelData.put("version", 1);

        GraphQLResponseBean resp = saveTagLabelViaGql(labelData);
        assertFalse(resp.hasError(), "save TagLabel should succeed: " + resp);

        assertEquals(1, countDerivedLabelsForTarget("NopMetaEntityField", "field-b-001"),
                "should create 1 derived TagLabel");

        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, "NopMetaEntityField"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, "field-b-001"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Derived"));
        List<NopMetaTagLabel> derived = labelDao.findAllByQuery(q);
        assertEquals(1, derived.size());
        NopMetaTagLabel d = derived.get(0);
        assertEquals("Classification", d.getSource());
        assertEquals("Suggested", d.getState());
        assertEquals("tag-prop-b-1a", d.getTagId());
        assertTrue(d.getReason().contains("propagated from glossary term"),
                "reason should mention propagation: " + d.getReason());
    }

    @Test
    public void testPathBIdempotentCheck() {
        createGlossary("gl-prop-b-2", "PropTestB2");
        createClassification("cls-prop-b-2", "DataClassB2");
        createTag("tag-prop-b-2a", "cls-prop-b-2", "TagPII");

        Map<String, Object> termData = new HashMap<>();
        termData.put("glossaryTermId", "gt-prop-b-2");
        termData.put("glossaryId", "gl-prop-b-2");
        termData.put("name", "TermPII");
        termData.put("tags", "[\"tag-prop-b-2a\"]");
        termData.put("version", 1);
        saveGlossaryTermViaGql(termData);

        Map<String, Object> labelData = new HashMap<>();
        labelData.put("tagLabelId", "tl-prop-b-2");
        labelData.put("source", "Glossary");
        labelData.put("glossaryTermId", "gt-prop-b-2");
        labelData.put("tagId", "tag-prop-b-2a");
        labelData.put("labelType", "Manual");
        labelData.put("state", "Confirmed");
        labelData.put("entityType", "NopMetaEntityField");
        labelData.put("entityId", "field-b-002");
        labelData.put("version", 1);

        GraphQLResponseBean resp1 = saveTagLabelViaGql(labelData);
        assertFalse(resp1.hasError(), "first save should succeed: " + resp1);

        Map<String, Object> labelData2 = new HashMap<>();
        labelData2.put("id", "tl-prop-b-2");
        labelData2.put("source", "Glossary");
        labelData2.put("glossaryTermId", "gt-prop-b-2");
        labelData2.put("tagId", "tag-prop-b-2a");
        labelData2.put("labelType", "Manual");
        labelData2.put("state", "Confirmed");
        labelData2.put("entityType", "NopMetaEntityField");
        labelData2.put("entityId", "field-b-002");
        labelData2.put("version", 1);
        GraphQLResponseBean resp2 = updateTagLabelViaGql(labelData2);
        assertFalse(resp2.hasError(), "update should succeed (idempotent): " + resp2);

        assertEquals(1, countDerivedLabelsForTarget("NopMetaEntityField", "field-b-002"),
                "should not create duplicate derived TagLabels");
    }
}
