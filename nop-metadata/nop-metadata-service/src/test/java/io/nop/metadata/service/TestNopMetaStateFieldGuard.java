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
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.quality.QualityResultWriter;
import io.nop.metadata.service.quality.QualityRuleJudgment;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-33 / P2-26（plan 2026-08-16-0920-2 Phase 3）：状态机守卫收紧的区分力测试（双路径）。
 *
 * <p>每个经 delta xmeta 收紧（{@code updatable="false"}）的状态字段走两条路径：
 * <ol>
 *   <li><b>GraphQL {@code __update} 直改入口 → 值不变</b>——运行时语义是<b>静默丢弃</b>非 updatable prop
 *       （{@code ObjMetaBasedValidator.validateForUpdate}，CrudBizModel.update 传 inputSelection=null），
 *       不抛错拒绝；同请求内可更新字段（reason/remark/message）仍正常落库，证明丢弃是字段级而非请求级。</li>
 *   <li><b>专用路径（approve mutation / 生产 writer）仍可翻转</b>——收紧不得破坏合法状态写入
 *       （.xbiz 与 Java 实体写不经 InputBean 校验）。</li>
 * </ol>
 *
 * <p>P2-26：checkpoint 删除后结果行保留（无级联 = 历史事实语义）+ 双向关系可查询。
 * 变异验证记录（区分力实证）：临时移除 NopMetaTagLabel.xmeta 中 {@code <prop name="state" updatable="false"/>}
 * 后 testTagLabelStateFieldsNotDirectUpdatable 红（state 被直改为 Confirmed）、恢复后绿——见 plan Phase 3。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaStateFieldGuard extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate orm;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    // ===== P2-33: NopMetaTagLabel.state + approveStatus =====

    /** 路径 (a)：__update 直改 state/approveStatus 被静默丢弃（不抛错），同请求 reason 正常更新。 */
    @Test
    public void testTagLabelStateFieldsNotDirectUpdatable() {
        saveTagLabel("guard-tlabel-1", "Suggested", null);

        Map<String, Object> data = Map.of(
                "id", "guard-tlabel-1",
                "state", "Confirmed",
                "approveStatus", "APPROVED",
                "reason", "direct-update-attempt");
        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaTagLabel__update(data:$data) { tagLabelId state approveStatus reason } }",
                Map.of("data", data));
        assertFalse(resp.hasError(), "__update must not error (non-updatable prop is silently dropped): " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById("guard-tlabel-1");
        assertNotNull(saved);
        assertEquals("Suggested", saved.getState(),
                "state must be unchanged: __update input face closed by delta xmeta updatable=false");
        assertNull(saved.getApproveStatus(), "approveStatus must be unchanged (was null before update)");
        assertEquals("direct-update-attempt", saved.getReason(),
                "updatable field (reason) in the same request must still be applied (field-level drop, not request-level)");
    }

    /** 路径 (b)：approve mutation（工作流/直调触达的专用路径）仍翻转 state/approveStatus。 */
    @Test
    public void testTagLabelApprovePathStillFlipsState() {
        saveTagLabel("guard-tlabel-2", "Suggested", "SUBMITTED");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaTagLabel__approve(id: \"guard-tlabel-2\") { tagLabelId state approveStatus } }",
                Map.of());
        assertFalse(resp.hasError(), "approve mutation must keep working after xmeta tightening: " + resp);

        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById("guard-tlabel-2");
        assertNotNull(saved);
        assertEquals("Confirmed", saved.getState(), "dedicated approve path must still flip state");
        assertEquals("APPROVED", saved.getApproveStatus(), "dedicated approve path must still flip approveStatus");
    }

    // ===== P2-33: NopMetaDataContract.status + approveStatus =====

    /** 路径 (a)：__update 直改 status/approveStatus 被静默丢弃，同请求 remark 正常更新。 */
    @Test
    public void testDataContractStateFieldsNotDirectUpdatable() {
        saveContract("guard-contract-1", "DRAFT", "SUBMITTED");

        Map<String, Object> data = Map.of(
                "id", "guard-contract-1",
                "status", "RETIRED",
                "approveStatus", "APPROVED",
                "remark", "direct-update-attempt");
        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaDataContract__update(data:$data) { contractId status approveStatus remark } }",
                Map.of("data", data));
        assertFalse(resp.hasError(), "__update must not error (silent drop semantics): " + resp);

        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById("guard-contract-1");
        assertNotNull(saved);
        assertEquals("DRAFT", saved.getStatus(), "status must be unchanged (lifecycle only via approve/reject)");
        assertEquals("SUBMITTED", saved.getApproveStatus(), "approveStatus must be unchanged (only via approval actions)");
        assertEquals("direct-update-attempt", saved.getRemark(),
                "updatable field (remark) in the same request must still be applied");
    }

    /** 路径 (b)：approve mutation 仍翻转 status 生命周期（DRAFT→ACTIVE）与 approveStatus。 */
    @Test
    public void testDataContractApprovePathStillFlipsStatus() {
        saveContract("guard-contract-2", "DRAFT", "SUBMITTED");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataContract__approve(id: \"guard-contract-2\") { contractId status approveStatus } }",
                Map.of());
        assertFalse(resp.hasError(), "approve mutation must keep working after xmeta tightening: " + resp);

        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById("guard-contract-2");
        assertNotNull(saved);
        assertEquals("ACTIVE", saved.getStatus(), "dedicated approve path must still advance lifecycle DRAFT→ACTIVE");
        assertEquals("APPROVED", saved.getApproveStatus(), "dedicated approve path must still flip approveStatus");
    }

    // ===== P2-33: NopMetaQualityResult.status =====

    /** 路径 (a)：__update 直改 status 被静默丢弃，同请求 message 正常更新。 */
    @Test
    public void testQualityResultStatusNotDirectUpdatable() {
        saveQualityResult("guard-qresult-1", "FAIL");

        Map<String, Object> data = Map.of(
                "id", "guard-qresult-1",
                "status", "PASS",
                "message", "direct-update-attempt");
        GraphQLResponseBean resp = execute(
                "mutation($data:Map) { NopMetaQualityResult__update(data:$data) { qualityResultId status message } }",
                Map.of("data", data));
        assertFalse(resp.hasError(), "__update must not error (silent drop semantics): " + resp);

        NopMetaQualityResult saved = daoProvider.daoFor(NopMetaQualityResult.class).getEntityById("guard-qresult-1");
        assertNotNull(saved);
        assertEquals("FAIL", saved.getStatus(), "status must be unchanged (only execution/re-judge engine writes it)");
        assertEquals("direct-update-attempt", saved.getMessage(),
                "updatable field (message) in the same request must still be applied");
    }

    /**
     * 路径 (b)：status 的专用写入通道（唯一生产写位点 {@link QualityResultWriter#append}，insert-only，
     * 含 ALLOWED_STATUSES fail-fast 校验）不受收紧影响——引擎仍能落盘 status。
     */
    @Test
    public void testQualityResultWriterStillWritesStatus() {
        QualityRuleJudgment judgment = new QualityRuleJudgment();
        judgment.setStatus("PASS");
        judgment.setMessage("guard-writer-path");
        judgment.setActualValue(0.0);

        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult row = new QualityResultWriter().append(dao, "guard-rule-writer", null, null, judgment);

        assertNotNull(row.getQualityResultId());
        NopMetaQualityResult saved = dao.getEntityById(row.getQualityResultId());
        assertNotNull(saved, "engine insert path must persist the result row");
        assertEquals("PASS", saved.getStatus(), "production writer (insert) must still write status");
    }

    // ===== P2-26: checkpoint 删除保留结果行 + 关系可查询 =====

    /** 检查点删除后结果行无条件保留（无级联 = 历史事实语义，P2-26 裁定行为本体）。 */
    @Test
    public void testCheckpointDeleteRetainsResultRows() {
        // 检查点经 GraphQL __save 创建（沿 testSaveDeleteWiringSchedulerBeanPresent 先例）：
        // DAO 直写会把实体留在测试 ambient session 中，后续 GraphQL delete session 的 flush
        // 对跨 session 实体不可见（delete 标记丢失），故创建走与删除相同的入口链路。
        GraphQLResponseBean saveResp = execute(
                "mutation { NopMetaQualityCheckpoint__save(data: {"
                        + "checkpointId: \"guard-cp-1\", checkpointName: \"guard-cp-1\", "
                        + "displayName: \"guard-cp-1\", status: \"ACTIVE\", "
                        + "validations: \"[{\\\"ruleIds\\\":[\\\"__any__\\\"]}]\""
                        + " }) { checkpointId } }", Map.of());
        assertFalse(saveResp.hasError(), "checkpoint save must succeed: " + saveResp);
        saveQualityResultRow("guard-cp-1", "guard-run-1", "guard-rule-cp", "FAIL", "guard-qresult-cp-1");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaQualityCheckpoint__delete(id: \"guard-cp-1\") }", Map.of());
        assertFalse(resp.hasError(), "checkpoint delete must succeed: " + resp);

        assertNull(daoProvider.daoFor(NopMetaQualityCheckpoint.class).getEntityById("guard-cp-1"),
                "checkpoint must be deleted");
        NopMetaQualityResult retained = daoProvider.daoFor(NopMetaQualityResult.class)
                .getEntityById("guard-qresult-cp-1");
        assertNotNull(retained, "result row must be RETAINED after checkpoint delete (no cascade = adjudicated semantics)");
        assertEquals("guard-cp-1", retained.getCheckpointId(),
                "retained row keeps its checkpointId (historical fact, orphan-by-design)");
    }

    /** P2-26 关系显式化后双向可查询：result.checkpoint to-one 命中、checkpoint.qualityResults to-many 命中。 */
    @Test
    public void testQualityResultCheckpointRelationQueryable() {
        saveCheckpoint("guard-cp-2");
        saveQualityResultRow("guard-cp-2", "guard-run-2", "guard-rule-cp2", "PASS", "guard-qresult-cp-2");

        orm.runInSession(session -> {
            NopMetaQualityResult result = daoProvider.daoFor(NopMetaQualityResult.class)
                    .getEntityById("guard-qresult-cp-2");
            assertNotNull(result);
            NopMetaQualityCheckpoint cp = result.getCheckpoint();
            assertNotNull(cp, "to-one relation checkpoint must be queryable after P2-26 materialization");
            assertEquals("guard-cp-2", cp.getCheckpointId());

            NopMetaQualityCheckpoint owner = daoProvider.daoFor(NopMetaQualityCheckpoint.class)
                    .getEntityById("guard-cp-2");
            assertNotNull(owner);
            boolean found = owner.getQualityResults().stream()
                    .anyMatch(r -> "guard-qresult-cp-2".equals(r.getQualityResultId()));
            assertTrue(found, "to-many relation qualityResults must be queryable");
            return null;
        });
    }

    // ===== helpers =====

    private void saveTagLabel(String id, String state, String approveStatus) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        NopMetaTagLabel label = dao.newEntity();
        label.setTagLabelId(id);
        label.setSource("Classification");
        label.setTagId("guard-tag");
        label.setLabelType("Derived");
        label.setState(state);
        label.setApproveStatus(approveStatus);
        label.setEntityType("NopMetaEntityField");
        label.setEntityId("guard-field-1");
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        label.setCreateTime(now);
        label.setUpdateTime(now);
        dao.saveEntity(label);
    }

    private void saveContract(String id, String status, String approveStatus) {
        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        NopMetaDataContract c = dao.newEntity();
        c.setContractId(id);
        c.setContractName(id + "-name");
        c.setDisplayName(id + "-name");
        c.setStatus(status);
        c.setApproveStatus(approveStatus);
        c.setOwnerUserId("autotest");
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
    }

    private void saveQualityResult(String id, String status) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult r = dao.newEntity();
        r.setQualityResultId(id);
        r.setQualityRuleId("guard-rule-1");
        r.setExecuteTime(now);
        r.setStatus(status);
        r.setVersion(1L);
        r.setCreatedBy("autotest");
        r.setUpdatedBy("autotest");
        r.setCreateTime(now);
        r.setUpdateTime(now);
        dao.saveEntity(r);
    }

    private void saveCheckpoint(String checkpointId) {
        IEntityDao<NopMetaQualityCheckpoint> dao = daoProvider.daoFor(NopMetaQualityCheckpoint.class);
        NopMetaQualityCheckpoint cp = dao.newEntity();
        cp.setCheckpointId(checkpointId);
        cp.setCheckpointName(checkpointId);
        cp.setDisplayName(checkpointId);
        cp.setStatus("ACTIVE");
        cp.setVersion(1L);
        cp.setCreatedBy("autotest");
        cp.setUpdatedBy("autotest");
        cp.setCreateTime(now);
        cp.setUpdateTime(now);
        dao.saveEntity(cp);
    }

    private void saveQualityResultRow(String checkpointId, String runId, String ruleId, String status, String resultId) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult row = dao.newEntity();
        row.setQualityResultId(resultId);
        row.setQualityRuleId(ruleId);
        row.setCheckpointId(checkpointId);
        row.setRunId(runId);
        row.setStatus(status);
        row.setExecuteTime(now);
        row.setVersion(1L);
        row.setCreatedBy("autotest");
        row.setUpdatedBy("autotest");
        row.setCreateTime(now);
        row.setUpdateTime(now);
        dao.saveEntity(row);
    }

    private GraphQLResponseBean execute(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
