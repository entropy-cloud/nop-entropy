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
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P2-22（plan 2026-08-16-0226-3 Phase 5）：approve 语义诚实化钉死。
 *
 * <p>终态 (a)（工作流 live 事实支撑）：approve = notifyResult 回调挂点，**无字段变更、不做 re-judge**
 * ——真实 re-judge 在 qualityBreachApproval 工作流 agree 路径的 verify 步骤（v1.xwf c:script 调
 * reJudgeFailClosed，先于流程结束）。修复前方法体含无字段变更的 {@code updateEntity}（no-op 写，
 * 但会触发乐观锁 version 递增）；修复后 approve 纯读返回。
 *
 * <p>断言：approve 返回实体且 DB 行零变更（version/isFalsePositive/status 全不变——若 updateEntity
 * 残留，version 必递增）；对照 reject 保持真实字段变更（isFalsePositive=1）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaQualityResultApprove extends JunitBaseTestCase {

    public TestNopMetaQualityResultApprove() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testApproveIsSideEffectFree() {
        NopMetaQualityResult before = saveResult("qr-approve-1", "FAIL");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaQualityResult__approve(id: \"" + before.getQualityResultId() + "\") "
                        + "{ qualityResultId status isFalsePositive } }");
        assertFalse(resp.hasError(), "approve should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertNotNull(data);
        assertFalse(data.contains("ERROR"), "approve must return the loaded entity: " + data);

        // DB 行零变更：无字段变更 + 无 updateEntity（若残留 no-op update，version 乐观锁列必递增）
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult after = dao.getEntityById(before.getQualityResultId());
        assertNotNull(after);
        assertEquals(before.getStatus(), after.getStatus(), "approve must not change status");
        assertEquals(before.getIsFalsePositive(), after.getIsFalsePositive(),
                "approve must not touch isFalsePositive");
        assertEquals(before.getVersion(), after.getVersion(),
                "approve must not issue any entity update (version column unchanged)");
    }

    /** 对照：reject 保持真实字段变更（isFalsePositive=1）——同族两方法语义分野钉死。 */
    @Test
    public void testRejectKeepsRealFieldChange() {
        NopMetaQualityResult before = saveResult("qr-reject-1", "FAIL");
        assertEquals(0, before.getIsFalsePositive() == null ? 0 : before.getIsFalsePositive().intValue());

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaQualityResult__reject(id: \"" + before.getQualityResultId() + "\") "
                        + "{ qualityResultId isFalsePositive } }");
        assertFalse(resp.hasError(), "reject should not error: " + resp);

        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult after = dao.getEntityById(before.getQualityResultId());
        assertNotNull(after);
        assertEquals(1, after.getIsFalsePositive().intValue(), "reject must set isFalsePositive=1 (real field change)");
    }

    private NopMetaQualityResult saveResult(String ruleId, String status) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult r = dao.newEntity();
        r.setQualityRuleId(ruleId);
        r.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        r.setStatus(status);
        r.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        r.setCreatedBy("autotest");
        r.setCreateTime(now);
        r.setUpdatedBy("autotest");
        r.setUpdateTime(now);
        dao.saveEntity(r);
        return r;
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(ctx);
    }
}
