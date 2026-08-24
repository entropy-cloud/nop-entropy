/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.biz.importexport;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.batch.biz.BizReportErrors.ERR_BIZ_REPORT_UNSUPPORTED_EXPORT_FORMAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 不支持的导出格式必须通过NopException+ErrorCode抛出（可定位、可国际化），
 * 不能用裸IllegalArgumentException拼接伪错误码字符串
 */
public class TestBizExportTaskBuilderFormat {

    @Test
    public void testUnsupportedFormatThrowsNopException() {
        BizExportTaskBuilder builder = new BizExportTaskBuilder(null, null, null);
        BizEntityExportConfig config = new BizEntityExportConfig();
        config.setExportFormat("xml");

        NopException ex = assertThrows(NopException.class, () -> builder.buildConsumer(config));
        assertEquals(ERR_BIZ_REPORT_UNSUPPORTED_EXPORT_FORMAT.getErrorCode(), ex.getErrorCode());
    }
}
