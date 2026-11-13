/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.BatchConfigs;
import io.nop.batch.exp.config.ExportDbConfig;
import io.nop.batch.exp.config.ExportTableConfig;
import io.nop.batch.exp.config.ImportDbConfig;
import io.nop.batch.exp.config.ImportTableConfig;

import java.util.List;

import static io.nop.batch.core.BatchErrors.ARG_MAX_CONCURRENCY_CAP;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_NAME;
import static io.nop.batch.core.BatchErrors.ARG_PARAM_VALUE;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_CONCURRENCY_EXCEEDS_CAP;

/**
 * 1.7-C8-010（plan 2546 W-11）：exp 工具链的线程占用上界校验。
 * <p>
 * 缺陷背景：biz 导出链有界且有钳制，而 exp 工具链自建 {@code DefaultThreadPoolExecutor}
 * 且 {@code threadCount} 直给（xdef {@code !int=0} 无上界）——同一"导出"业务按入口不同，
 * 线程占用相差一个数量级。本类提供**纯静态**（无 DB）校验，在 {@code execute()} 资源段之前调用。
 * <p>
 * 乘积口径：{@code max(各表 concurrency, concurrencyPerTable, 1) × max(threadCount, 1)}。
 * {@code threadCount<=1} 走 {@code SyncThreadPoolExecutor}（无 worker），乘积无意义，短路放行。
 */
public final class DbToolConcurrencyValidator {

    private DbToolConcurrencyValidator() {
    }

    public static void validateExportExecutorConfig(ExportDbConfig config) {
        int threadCount = config.getThreadCount();
        if (threadCount <= 1)
            return;

        int tableConcurrency = 1;
        List<ExportTableConfig> tables = config.getTables();
        if (tables != null) {
            for (ExportTableConfig table : tables) {
                if (table == null || table.getConcurrency() == null)
                    continue;
                tableConcurrency = Math.max(tableConcurrency, table.getConcurrency());
            }
        }
        if (config.getConcurrencyPerTable() != null)
            tableConcurrency = Math.max(tableConcurrency, config.getConcurrencyPerTable());

        validateProduct(threadCount, tableConcurrency);
    }

    public static void validateImportExecutorConfig(ImportDbConfig config) {
        int threadCount = config.getThreadCount();
        if (threadCount <= 1)
            return;

        int tableConcurrency = 1;
        List<ImportTableConfig> tables = config.getTables();
        if (tables != null) {
            for (ImportTableConfig table : tables) {
                if (table == null || table.getConcurrency() == null)
                    continue;
                tableConcurrency = Math.max(tableConcurrency, table.getConcurrency());
            }
        }
        if (config.getConcurrencyPerTable() != null)
            tableConcurrency = Math.max(tableConcurrency, config.getConcurrencyPerTable());

        validateProduct(threadCount, tableConcurrency);
    }

    private static void validateProduct(int threadCount, int tableConcurrency) {
        long product = (long) threadCount * Math.max(tableConcurrency, 1);
        int cap = BatchConfigs.CFG_BATCH_MAX_CONCURRENCY_CAP.get();
        if (product > cap)
            throw new NopException(ERR_BATCH_CONCURRENCY_EXCEEDS_CAP)
                    .param(ARG_PARAM_NAME, "threadCount×concurrencyPerTable")
                    .param(ARG_PARAM_VALUE, product)
                    .param(ARG_MAX_CONCURRENCY_CAP, cap);
    }
}
