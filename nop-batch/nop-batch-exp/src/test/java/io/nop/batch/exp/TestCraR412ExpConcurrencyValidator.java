/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.exp.config.ExportDbConfig;
import io.nop.batch.exp.config.ExportTableConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2546 R4-12（C8-010 / W-11）：exp 自建池的线程占用上界校验（纯静态，无 DB）。
 */
public class TestCraR412ExpConcurrencyValidator {

    /** pin11c：threadCount 超 cap（乘积口径）→ fail-fast；同步池短路放行。 */
    @Test
    public void pin11c_threadCountProductExceedsCap() {
        ExportDbConfig config = new ExportDbConfig();
        config.setThreadCount(300);            // cap=200
        config.setConcurrencyPerTable(1);
        NopException err = assertThrows(NopException.class,
                () -> DbToolConcurrencyValidator.validateExportExecutorConfig(config));
        assertEquals("nop.err.batch.concurrency-exceeds-cap", err.getErrorCode());
        assertEquals("threadCount×concurrencyPerTable", err.getParams().get("paramName"));
    }

    /** pin11d：乘积超界——threadCount 与表级并发都不超单值，但乘积超 cap。 */
    @Test
    public void pin11d_productOverCap() {
        ExportDbConfig config = new ExportDbConfig();
        config.setThreadCount(20);
        config.setConcurrencyPerTable(20);     // 20*20=400 > 200
        NopException err = assertThrows(NopException.class,
                () -> DbToolConcurrencyValidator.validateExportExecutorConfig(config));
        assertEquals("nop.err.batch.concurrency-exceeds-cap", err.getErrorCode());
        assertEquals(400L, err.getParams().get("paramValue"));
    }

    /** pin11e：threadCount<=1（Sync 池）短路——即使乘积算式会超也不校验。 */
    @Test
    public void pin11e_syncPoolShortCircuits() {
        ExportDbConfig config = new ExportDbConfig();
        config.setThreadCount(1);
        config.setConcurrencyPerTable(1000);
        assertDoesNotThrow(() -> DbToolConcurrencyValidator.validateExportExecutorConfig(config));
    }

    /** pin11f：表级 concurrency 参与取最大。 */
    @Test
    public void pin11f_tableLevelConcurrencyCounts() {
        ExportDbConfig config = new ExportDbConfig();
        config.setThreadCount(10);
        ExportTableConfig table = new ExportTableConfig();
        table.setConcurrency(50);              // 10*50=500 > 200
        List<ExportTableConfig> tables = new ArrayList<>();
        tables.add(table);
        config.setTables(tables);
        NopException err = assertThrows(NopException.class,
                () -> DbToolConcurrencyValidator.validateExportExecutorConfig(config));
        assertEquals(500L, err.getParams().get("paramValue"));
    }

    /** pin11g：正常配置不触发。 */
    @Test
    public void pin11g_normalConfigPasses() {
        ExportDbConfig config = new ExportDbConfig();
        config.setThreadCount(4);
        config.setConcurrencyPerTable(2);      // 8
        assertDoesNotThrow(() -> DbToolConcurrencyValidator.validateExportExecutorConfig(config));
    }
}
