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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTagLabelApprovalIntegration extends JunitBaseTestCase {

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
            cls.setName("IntTestCls");
            cls.setDisplayName("Integration Test");
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
            tag.setName("int-test-tag");
            tag.setFullyQualifiedName("IntTestCls.int-test-tag");
            tag.setVersion(1L);
            tag.setCreatedBy("autotest");
            tag.setUpdatedBy("autotest");
            tag.setCreateTime(now);
            tag.setUpdateTime(now);
            tagDao.saveEntity(tag);
        }
        return tagId;
    }

    // (c) Manual TagLabel 创建 → 直接 state=Confirmed，不触发审批
    @Test
    public void testManualLabelDirectConfirmed() {
        String tagId = ensureTag("tag-int-manual", "cls-int-manual");

        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", "tlabel-int-manual-001");
        data.put("source", "Classification");
        data.put("tagId", tagId);
        data.put("labelType", "Manual");
        data.put("entityType", "NopMetaEntityField");
        data.put("entityId", "field-int-manual-001");
        data.put("appliedBy", "autotest");
        data.put("appliedAt", now);

        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state labelType } }",
                Map.of("data", data));
        assertFalse(resp.hasError(), "Manual save should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class)
                .getEntityById("tlabel-int-manual-001");
        assertNotNull(saved);
        assertEquals("Confirmed", saved.getState());
        assertNull(saved.getApproveStatus(), "Manual should not have approveStatus set");
    }

    // approve via GraphQL mutation -> state=Confirmed + approveStatus=APPROVED
    @Test
    public void testApproveViaGraphQL() {
        String id = "tlabel-int-approve-001";
        saveTagLabelRaw(id, "Manual", "Suggested");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaTagLabel__approve(id: \"" + id + "\") { tagLabelId state approveStatus } }",
                Map.of());
        assertFalse(resp.hasError(), "approve should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById(id);
        assertEquals("Confirmed", saved.getState());
        assertEquals("APPROVED", saved.getApproveStatus());
    }

    // reject via GraphQL mutation -> state stays Suggested + approveStatus=REJECTED
    @Test
    public void testRejectViaGraphQL() {
        String id = "tlabel-int-reject-001";
        saveTagLabelRaw(id, "Manual", "Suggested");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaTagLabel__reject(id: \"" + id + "\") { tagLabelId state approveStatus } }",
                Map.of());
        assertFalse(resp.hasError(), "reject should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById(id);
        assertEquals("Suggested", saved.getState(), "reject should keep state=Suggested");
        assertEquals("REJECTED", saved.getApproveStatus());
        assertNotNull(saved.getRemark());
        assertTrue(saved.getRemark().startsWith("Rejected:"));
    }

    // (d) 唯一约束：同一资产上的同一 (entityType, entityId, tagId, source) 组合只能有一条 TagLabel
    @Test
    public void testUniqueConstraintDuplicatePrevented() {
        String tagId = ensureTag("tag-int-uniq", "cls-int-uniq");

        // 首次创建应成功
        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", "tlabel-int-uniq-001");
        data.put("source", "Classification");
        data.put("tagId", tagId);
        data.put("labelType", "Derived");
        data.put("state", "Suggested");
        data.put("entityType", "NopMetaEntityField");
        data.put("entityId", "field-int-uniq-001");

        GraphQLResponseBean resp1 = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state } }",
                Map.of("data", data));
        assertFalse(resp1.hasError(), "first save should succeed: " + resp1);

        // 重复创建同一 (entityType, entityId, tagId, source) 应失败（唯一约束）
        Map<String, Object> dupData = new HashMap<>();
        dupData.put("tagLabelId", "tlabel-int-uniq-002");
        dupData.put("source", "Classification");
        dupData.put("tagId", tagId);
        dupData.put("labelType", "Derived");
        dupData.put("state", "Suggested");
        dupData.put("entityType", "NopMetaEntityField");
        dupData.put("entityId", "field-int-uniq-001");

        GraphQLResponseBean resp2 = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state } }",
                Map.of("data", dupData));
        assertTrue(resp2.hasError(), "duplicate save should be rejected by unique constraint: " + resp2);

        // 不同 entityId 应成功
        Map<String, Object> otherData = new HashMap<>();
        otherData.put("tagLabelId", "tlabel-int-uniq-003");
        otherData.put("source", "Classification");
        otherData.put("tagId", tagId);
        otherData.put("labelType", "Derived");
        otherData.put("state", "Suggested");
        otherData.put("entityType", "NopMetaEntityField");
        otherData.put("entityId", "field-int-uniq-002");

        GraphQLResponseBean resp3 = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state } }",
                Map.of("data", otherData));
        assertFalse(resp3.hasError(), "save with different entityId should succeed: " + resp3);
    }

    private void saveTagLabelRaw(String id, String labelType, String state) {
        String tagId = ensureTag("tag-int-raw", "cls-int-raw");
        NopMetaTagLabel label = daoProvider.daoFor(NopMetaTagLabel.class).newEntity();
        label.setTagLabelId(id);
        label.setSource("Classification");
        label.setTagId(tagId);
        label.setLabelType(labelType);
        label.setState(state);
        label.setEntityType("NopMetaEntityField");
        label.setEntityId("field-int-raw-001");
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
