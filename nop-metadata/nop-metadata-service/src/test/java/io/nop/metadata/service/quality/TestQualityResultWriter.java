package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2.8 / 11-04 回归测试：QualityResultWriter 落盘前对 status 做显式字典校验（PASS/FAIL/ERROR/SKIP），
 * 非法 status fail-fast 抛 ERR_QUALITY_RESULT_STATUS_INVALID，不静默写入（参照 xmeta dict 校验语义）。
 *
 * <p>R4.3（plan-2026-08-05-1625-2）：append 签名增加 checkpointId/runId（幂等键载体，可空），
 * 本测试适配新签名并断言检查点上下文确实写入落盘行（UK 拒绝同 runId 重复行的前置条件）；
 * 单规则路径（null/null）保持时序追加语义（不写 null 以外的哨兵值）。
 */
public class TestQualityResultWriter {

    @Test
    public void testAppendWithValidStatusSaves() {
        IEntityDao<NopMetaQualityResult> resultDao = mock(IEntityDao.class);
        NopMetaQualityResult row = new NopMetaQualityResult();
        when(resultDao.newEntity()).thenReturn(row);

        QualityRuleJudgment judgment = new QualityRuleJudgment();
        judgment.setStatus("FAIL");
        judgment.setActualValue(1.0);
        judgment.setMessage("nullCount=1");

        NopMetaQualityResult saved = new QualityResultWriter().append(resultDao, "rule-1", null, null, judgment);
        verify(resultDao).saveEntity(saved);
        assertEquals("FAIL", saved.getStatus());
        assertEquals("rule-1", saved.getQualityRuleId());
    }

    @Test
    public void testAppendWithInvalidStatusThrows() {
        IEntityDao<NopMetaQualityResult> resultDao = mock(IEntityDao.class);
        when(resultDao.newEntity()).thenReturn(new NopMetaQualityResult());

        QualityRuleJudgment judgment = new QualityRuleJudgment();
        judgment.setStatus("INVALID_STATUS");

        NopException ex = assertThrows(NopException.class,
                () -> new QualityResultWriter().append(resultDao, "rule-1", null, null, judgment));
        assertEquals(NopMetadataErrors.ERR_QUALITY_RESULT_STATUS_INVALID.getErrorCode(), ex.getErrorCode());
        verify(resultDao, never()).saveEntity(any());
    }

    @Test
    public void testAppendWithNullStatusThrows() {
        IEntityDao<NopMetaQualityResult> resultDao = mock(IEntityDao.class);

        QualityRuleJudgment judgment = new QualityRuleJudgment();
        judgment.setStatus(null);

        NopException ex = assertThrows(NopException.class,
                () -> new QualityResultWriter().append(resultDao, "rule-1", null, null, judgment));
        assertEquals(NopMetadataErrors.ERR_QUALITY_RESULT_STATUS_INVALID.getErrorCode(), ex.getErrorCode());
    }

    /**
     * R4.3 接线验证（Minimum Rules #23）：检查点执行路径（checkpointId/runId 非 null）必须原样写入落盘行
     * —— 这是 DB 复合 UK 拒绝同 runId 重复写行的前置条件（Minor-7 单测部分；DB 级拒绝由 e2e 覆盖）。
     */
    @Test
    public void testAppendCarriesCheckpointIdAndRunId() {
        IEntityDao<NopMetaQualityResult> resultDao = mock(IEntityDao.class);
        NopMetaQualityResult row = new NopMetaQualityResult();
        when(resultDao.newEntity()).thenReturn(row);

        QualityRuleJudgment judgment = new QualityRuleJudgment();
        judgment.setStatus("PASS");

        NopMetaQualityResult saved = new QualityResultWriter()
                .append(resultDao, "rule-cp", "cp-1", "run-abc123", judgment);
        verify(resultDao).saveEntity(saved);
        assertEquals("cp-1", saved.getCheckpointId(), "checkpointId must be persisted for UK enforcement");
        assertEquals("run-abc123", saved.getRunId(), "runId must be persisted for UK enforcement");
    }

    /**
     * R4.3 单规则路径回归：checkpointId/runId 为 null 时落盘行两列保持 null（不写哨兵值），
     * 时序追加语义不受影响（复合 UK 任一列 NULL 不参与冲突判定）。
     */
    @Test
    public void testAppendWithoutCheckpointContextKeepsNulls() {
        IEntityDao<NopMetaQualityResult> resultDao = mock(IEntityDao.class);
        NopMetaQualityResult row = new NopMetaQualityResult();
        when(resultDao.newEntity()).thenReturn(row);

        QualityRuleJudgment judgment = new QualityRuleJudgment();
        judgment.setStatus("PASS");

        NopMetaQualityResult saved = new QualityResultWriter().append(resultDao, "rule-1", null, null, judgment);
        verify(resultDao).saveEntity(saved);
        assertNull(saved.getCheckpointId(), "single-rule path must keep checkpointId null");
        assertNull(saved.getRunId(), "single-rule path must keep runId null");
    }
}
