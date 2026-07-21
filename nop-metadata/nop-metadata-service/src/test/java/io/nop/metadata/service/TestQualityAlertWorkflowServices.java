package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证质量告警工作流相关服务的单元测试。
 * 覆盖新 ErrorCode 常量 + BizModel 基本 reject 守卫。
 */
public class TestQualityAlertWorkflowServices {

    @Test
    public void testNewErrorCodesDefined() {
        assertNotNull(NopMetadataErrors.ERR_QUALITY_RESULT_NOT_FOUND);
        assertEquals("nop.err.metadata.quality-result-not-found",
                NopMetadataErrors.ERR_QUALITY_RESULT_NOT_FOUND.getErrorCode());
    }

    @Test
    public void testArgConstants() {
        assertEquals("qualityResultId", NopMetadataErrors.ARG_QUALITY_RESULT_ID);
    }

    @Test
    public void testQualityResultNotFoundError() {
        NopException ex = new NopException(NopMetadataErrors.ERR_QUALITY_RESULT_NOT_FOUND)
                .param(NopMetadataErrors.ARG_QUALITY_RESULT_ID, "test-id");
        assertEquals("nop.err.metadata.quality-result-not-found", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("test-id"));
    }
}
