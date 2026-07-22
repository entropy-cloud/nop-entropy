package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTagLabelApproval extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private Timestamp now = new Timestamp(System.currentTimeMillis());

    private String ensureTag(String tagId, String classificationId) {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);
        if (clsDao.getEntityById(classificationId) == null) {
            NopMetaClassification cls = clsDao.newEntity();
            cls.setClassificationId(classificationId);
            cls.setName("ApprovalTest");
            cls.setDisplayName("Approval Test");
            cls.setMutuallyExclusive((byte) 0);
            cls.setProvider("system");
            cls.setVersion(1L);
            cls.setCreatedBy("autotest");
            cls.setUpdatedBy("autotest");
            cls.setCreateTime(now);
            cls.setUpdateTime(now);
            clsDao.saveEntity(cls);
        }

        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);
        if (tagDao.getEntityById(tagId) == null) {
            NopMetaTag tag = tagDao.newEntity();
            tag.setTagId(tagId);
            tag.setClassificationId(classificationId);
            tag.setName("approval-tag");
            tag.setFullyQualifiedName("ApprovalTest.approval-tag");
            tag.setVersion(1L);
            tag.setCreatedBy("autotest");
            tag.setUpdatedBy("autotest");
            tag.setCreateTime(now);
            tag.setUpdateTime(now);
            tagDao.saveEntity(tag);
        }
        return tagId;
    }

    @Test
    public void testManualLabelStateConfirmed() {
        String tagId = ensureTag("tag-approval-manual", "cls-approval-manual");

        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", "tlabel-manual-001");
        data.put("source", "Classification");
        data.put("tagId", tagId);
        data.put("labelType", "Manual");
        data.put("entityType", "NopMetaEntityField");
        data.put("entityId", "field-manual-001");
        data.put("appliedBy", "autotest");
        data.put("appliedAt", now);

        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state labelType } }",
                Map.of("data", data));
        assertFalse(resp.hasError(), "save Manual TagLabel should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById("tlabel-manual-001");
        assertNotNull(saved);
        assertEquals("Confirmed", saved.getState(), "Manual labelType should result in state=Confirmed");
    }

    @Test
    public void testDerivedLabelStateSuggested() {
        String tagId = ensureTag("tag-approval-derived", "cls-approval-derived");

        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", "tlabel-derived-001");
        data.put("source", "Classification");
        data.put("tagId", tagId);
        data.put("labelType", "Derived");
        data.put("entityType", "NopMetaEntityField");
        data.put("entityId", "field-derived-001");

        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state labelType } }",
                Map.of("data", data));
        assertFalse(resp.hasError(), "save Derived TagLabel should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById("tlabel-derived-001");
        assertNotNull(saved);
        assertEquals("Suggested", saved.getState(), "Derived labelType should result in state=Suggested");
    }

    @Test
    public void testApproveMutation() {
        String id = "tlabel-approve-001";
        saveTagLabel(id, "Manual", "Suggested");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaTagLabel__approve(id: \"" + id + "\") { tagLabelId state approveStatus } }",
                Map.of());
        assertFalse(resp.hasError(), "approve mutation should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById(id);
        assertNotNull(saved);
        assertEquals("Confirmed", saved.getState(), "After approve, state should be Confirmed");
        assertEquals("APPROVED", saved.getApproveStatus(), "After approve, approveStatus should be APPROVED");
        assertNotNull(saved.getApprovedAt(), "approvedAt should be set after approve");
    }

    @Test
    public void testRejectMutation() {
        String id = "tlabel-reject-001";
        saveTagLabel(id, "Manual", "Suggested");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaTagLabel__reject(id: \"" + id + "\") { tagLabelId state approveStatus } }",
                Map.of());
        assertFalse(resp.hasError(), "reject mutation should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById(id);
        assertNotNull(saved);
        assertEquals("Suggested", saved.getState(), "After reject, state should remain Suggested");
        assertEquals("REJECTED", saved.getApproveStatus(), "After reject, approveStatus should be REJECTED");
        assertNotNull(saved.getRemark(), "remark should be set after reject");
        assertTrue(saved.getRemark().contains("Rejected:"), "remark should contain rejection reason");
    }

    @Test
    public void testErrorCodesDefined() {
        assertNotNull(NopMetadataErrors.ERR_TAG_LABEL_NOT_FOUND);
        assertEquals("nop.err.metadata.tag-label-not-found",
                NopMetadataErrors.ERR_TAG_LABEL_NOT_FOUND.getErrorCode());
        assertNotNull(NopMetadataErrors.ERR_TAG_LABEL_INVALID_LABEL_TYPE);
        assertEquals("nop.err.metadata.tag-label-invalid-label-type",
                NopMetadataErrors.ERR_TAG_LABEL_INVALID_LABEL_TYPE.getErrorCode());
    }

    @Test
    public void testNotFoundError() {
        NopException ex = new NopMetadataException(NopMetadataErrors.ERR_TAG_LABEL_NOT_FOUND)
                .param(NopMetadataErrors.ARG_TAG_LABEL_ID, "nonexistent");
        assertEquals("nop.err.metadata.tag-label-not-found", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    private void saveTagLabel(String id, String labelType, String state) {
        String tagId = ensureTag("tag-approval-base", "cls-approval-base");
        NopMetaTagLabel label = daoProvider.daoFor(NopMetaTagLabel.class).newEntity();
        label.setTagLabelId(id);
        label.setSource("Classification");
        label.setTagId(tagId);
        label.setLabelType(labelType);
        label.setState(state);
        label.setEntityType("NopMetaEntityField");
        label.setEntityId("field-approval-001");
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        label.setCreateTime(now);
        label.setUpdateTime(now);
        daoProvider.daoFor(NopMetaTagLabel.class).saveEntity(label);
    }

    private GraphQLResponseBean execute(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
