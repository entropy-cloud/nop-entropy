/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.biz.importexport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * plan 2546 R4-12（C8-010 / W-11）：biz 导出链钳制的**下界**（以及上界零回归）。
 * <p>
 * 缺陷态：只判 {@code >} 上界，client 传 batchSize=0/负 原样穿透到 builder，
 * 经 {@code adjustBatchSize} 钳到 1 → 每行一 chunk（外部请求可控参数直达）。
 */
public class TestCraR412BizClampLowerBound {

    private static BizEntityExportConfig newConfig(int concurrency, int batchSize) {
        BizEntityExportConfig config = new BizEntityExportConfig();
        config.setConcurrency(concurrency);
        config.setBatchSize(batchSize);
        return config;
    }

    /** pin11a：batchSize=0/负 → 归 maxBatchSize。 */
    @Test
    public void pin11a_batchSizeLowerBoundClamped() {
        BizEntityExportConfig zero = newConfig(1, 0);
        DefaultBizEntityExporter.clampConfig(zero, 1, 500);
        assertEquals(500, zero.getBatchSize(), "batchSize=0 必须归 maxBatchSize（否则每行一 chunk）");

        BizEntityExportConfig negative = newConfig(1, -7);
        DefaultBizEntityExporter.clampConfig(negative, 1, 500);
        assertEquals(500, negative.getBatchSize(), "负 batchSize 同样归一");
    }

    /** pin11b：concurrency<=0 → 1。 */
    @Test
    public void pin11b_concurrencyLowerBoundClamped() {
        BizEntityExportConfig zero = newConfig(0, 100);
        DefaultBizEntityExporter.clampConfig(zero, 3, 1000);
        assertEquals(1, zero.getConcurrency());

        BizEntityExportConfig negative = newConfig(-2, 100);
        DefaultBizEntityExporter.clampConfig(negative, 3, 1000);
        assertEquals(1, negative.getConcurrency());
    }

    /** pin11c：上界钳制零回归。 */
    @Test
    public void pin11c_upperBoundStillClamped() {
        BizEntityExportConfig config = newConfig(9, 9999);
        DefaultBizEntityExporter.clampConfig(config, 2, 100);
        assertEquals(2, config.getConcurrency());
        assertEquals(100, config.getBatchSize());
    }

    /** pin11d：合法值不被改动。 */
    @Test
    public void pin11d_inRangeUntouched() {
        BizEntityExportConfig config = newConfig(2, 300);
        DefaultBizEntityExporter.clampConfig(config, 3, 1000);
        assertEquals(2, config.getConcurrency());
        assertEquals(300, config.getBatchSize());
    }
}
