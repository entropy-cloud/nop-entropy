/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.dsl.model;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 1.7-C7-004（plan 2545 W-10；closure audit R2 MJ-2）：负 maxRetryCount 装配期类型化 fail-fast 的行为钉。
 * <p>
 * 缺陷态：负值在底层 {@code RetryPolicy} 中语义为「无限重试」（与 -1=禁用 惯例相反），
 * 毒记录叠加负配置会让 chunk 线程永久自旋。修复前为裸 IllegalArgumentException（R1 M-3），
 * 且零测试（R2 MJ-2）。
 */
public class TestBatchRetryPolicyModelNegative {

    @Test
    public void negativeMaxRetryCountFailsFastWithTypedError() {
        BatchRetryPolicyModel model = new BatchRetryPolicyModel();
        model.setMaxRetryCount(-1);

        NopException err = assertThrows(NopException.class, model::buildRetryPolicy);
        assertEquals("nop.err.batch.retry-max-count-negative", err.getErrorCode(),
                "负 maxRetryCount 必须以专属错误码失败（不得是裸 IllegalArgumentException）");
        assertEquals(-1, err.getParams().get("value"));
    }

    @Test
    public void nonNegativeMaxRetryCountBuilds() {
        BatchRetryPolicyModel model = new BatchRetryPolicyModel();
        model.setMaxRetryCount(3);
        assertNotNull(model.buildRetryPolicy());
    }
}
