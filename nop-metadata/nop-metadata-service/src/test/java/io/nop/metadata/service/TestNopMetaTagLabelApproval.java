package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
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

    private static final String TEST_USER_ID = "u-approval-autotest";

    /**
     * wf 启动需要真实操作人（approval-support.xbiz ApprovalFlowHelper.start → start-step
     * invokeAction 的 allowCallByUser 校验）。P2-09 fail-loud 之前，wf 启动失败被 trySubmitForApproval
     * 吞掉（approveStatus=SUBMITTED 先于 wf 启动设置，旧测试因此误判"提审成功"）；fail-loud 之后
     * 测试环境必须能真实启动工作流，否则正路径 save 会如实失败。
     */
    private void ensureUser() {
        IEntityDao<NopAuthUser> userDao = daoProvider.daoFor(NopAuthUser.class);
        if (userDao.getEntityById(TEST_USER_ID) == null) {
            NopAuthUser user = userDao.newEntity();
            user.setUserName("approval-autotest");
            user.setUserId(TEST_USER_ID);
            user.setNickName(user.getUserName());
            user.setPassword("123");
            user.setOpenId(TEST_USER_ID);
            user.setUserType(1);
            user.setStatus(1);
            user.setGender(1);
            user.setTenantId("0");
            userDao.saveEntity(user);
        }
    }

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

    /**
     * P2-MA5-401：Derived 标签经 Java save 路径保存后必须自动提审（xmeta 根属性 wf:wfName 读取修复）。
     * 修复前 getWfNameFromMeta 用 SchemaImpl.getProp 只读 props map → wf:wfName（根元素属性）恒 null →
     * triggerApprovalIfNeeded 提前返回 → approveStatus 恒 null（自动提审静默失效）；修复后
     * submitForApproval 被真实调用 → approveStatus=SUBMITTED。
     */
    @Test
    public void testDerivedLabelAutoSubmitsForApproval() {
        String tagId = ensureTag("tag-approval-autosubmit", "cls-approval-autosubmit");

        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", "tlabel-autosubmit-001");
        data.put("source", "Classification");
        data.put("tagId", tagId);
        data.put("labelType", "Derived");
        data.put("entityType", "NopMetaEntityField");
        data.put("entityId", "field-autosubmit-001");

        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state approveStatus } }",
                Map.of("data", data));
        assertFalse(resp.hasError(), "save Derived TagLabel should not error: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById("tlabel-autosubmit-001");
        assertNotNull(saved);
        assertEquals("Suggested", saved.getState(), "Derived label should stay Suggested");
        assertEquals("SUBMITTED", saved.getApproveStatus(),
                "Derived label must auto-submit for approval (approveStatus=SUBMITTED; was null "
                        + "when the wf:wfName root-attr read was broken)");
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

    /**
     * P2-09（R6.4）：提审失败必须 fail-loud——不得 LOG.warn 后继续（标签保存成功但永不进审批流，
     * 用户侧零感知 = 静默数据丢失）。
     *
     * <p>判别性失败路径（确定性真实失败，零 mock）：save 的 data 预置 approveStatus=APPROVED
     * （∉ {null, UNSUBMITTED, REJECTED}）的 Derived 标签 → submitForApproval XPL
     * （approval-support.xbiz:19-26）抛 nop.err.wf.approve.invalid-status → save 抛
     * ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED（cause 保留原始异常链）+ 标签不落库
     * （save 为 CREATE 语义，事务回滚后按 tagLabelId 查询无该行）。
     *
     * <p>禁止方案：wfName 指向不存在的工作流——wf:wfName 硬编码于 xmeta 根属性，测试资源 delta
     * 覆盖 xmeta 为全 test classpath 全局，会打挂同容器正路径测试（:124-147）。
     */
    @Test
    public void testDerivedLabelApprovalFailureFailsLoud() {
        String tagId = ensureTag("tag-approval-failloud", "cls-approval-failloud");

        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", "tlabel-failloud-001");
        data.put("source", "Classification");
        data.put("tagId", tagId);
        data.put("labelType", "Derived");
        data.put("entityType", "NopMetaEntityField");
        data.put("entityId", "field-failloud-001");
        data.put("approveStatus", "APPROVED");

        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId state approveStatus } }",
                Map.of("data", data));
        assertTrue(resp.hasError(),
                "save with non-submittable approveStatus must fail-loud (no silent skip): " + resp);

        String errorCode = resp.getErrorCode();
        assertNotNull(errorCode, "error must carry an error code: " + resp);
        assertTrue(errorCode.contains("nop.err.metadata.tag-label-submit-approval-failed"),
                "error must carry ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED (discriminator), got: " + errorCode);
        assertFalse(errorCode.contains("nop.err.wf.approve.invalid-status"),
                "the inner invalid-status error must be wrapped (outer code is the adjudicated one): " + errorCode);

        // 事务回滚验证：save 为 CREATE 语义，失败后标签不落库（无"已保存但永不进审批流"的静默中间态）
        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById("tlabel-failloud-001");
        assertNull(saved, "failed save must leave no TagLabel row (transaction rollback)");
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
        IServiceContext svcCtx = newServiceContext();
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request, svcCtx);
        return graphQLEngine.executeGraphQL(context);
    }

    private IServiceContext newServiceContext() {
        ensureUser();
        ServiceContextImpl ctx = new ServiceContextImpl();
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(TEST_USER_ID);
        userContext.setUserName("approval-autotest");
        ctx.setUserContext(userContext);
        return ctx;
    }
}
