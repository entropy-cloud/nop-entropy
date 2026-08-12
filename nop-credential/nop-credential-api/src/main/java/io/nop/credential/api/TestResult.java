/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 凭证连通性测试结果。
 *
 * <p>W2 阶段 {@code testCredential} 始终返回 {@code success=false} + "not implemented" 描述性消息
 * （非静默空操作，Rule #24）。实际 HTTP 连通性测试为消费者侧关注点（Non-Blocking Follow-up）。
 */
public class TestResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final Timestamp testedAt;

    public TestResult(boolean success, String message, Timestamp testedAt) {
        this.success = success;
        this.message = message;
        this.testedAt = testedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Timestamp getTestedAt() {
        return testedAt;
    }
}
