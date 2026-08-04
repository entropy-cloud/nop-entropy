package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2.8 / 11-04 回归测试：QualityResultWriter 落盘前对 status 做显式字典校验（PASS/FAIL/ERROR/SKIP），
 * 非法 status fail-fast 抛 ERR_QUALITY_RESULT_STATUS_INVALID，不静默写入（参照 xmeta dict 校验语义）。
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

        NopMetaQualityResult saved = new QualityResultWriter().append(resultDao, "rule-1", judgment);
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
                () -> new QualityResultWriter().append(resultDao, "rule-1", judgment));
        assertEquals(NopMetadataErrors.ERR_QUALITY_RESULT_STATUS_INVALID.getErrorCode(), ex.getErrorCode());
        verify(resultDao, never()).saveEntity(any());
    }

    @Test
    public void testAppendWithNullStatusThrows() {
        IEntityDao<NopMetaQualityResult> resultDao = mock(IEntityDao.class);

        QualityRuleJudgment judgment = new QualityRuleJudgment();
        judgment.setStatus(null);

        NopException ex = assertThrows(NopException.class,
                () -> new QualityResultWriter().append(resultDao, "rule-1", judgment));
        assertEquals(NopMetadataErrors.ERR_QUALITY_RESULT_STATUS_INVALID.getErrorCode(), ex.getErrorCode());
    }
}
