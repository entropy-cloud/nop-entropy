/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import io.nop.api.core.annotations.core.Option;

public enum TccStatus {
    // 新建记录时处于created状态
    @Option(value = "0")
    CREATED(0),

    @Option(value = "1")
    TRYING(1),

    @Option(value = "2")
    TRY_SUCCESS(2),

    // try失败，不需要cancel
    @Option(value = "3")
    TRY_FAILED(3),

    // try的结果未知
    @Option(value = "4")
    TRY_UNKNOWN(4),

    @Option(value = "5")
    CONFIRMING(5),

    @Option(value = "6")
    CONFIRM_SUCCESS(6),

    @Option(value = "7")
    CONFIRM_FAILED(7),

    @Option(value = "8")
    CANCELLING(8),

    @Option(value = "9")
    CANCEL_SUCCESS(9),

    @Option(value = "10")
    CANCEL_FAILED(10),

    /**
     * 因业务原因导致cancel失败，也无法再重试
     */
    @Option(value = "11")
    BIZ_CANCEL_FAILED(11),

    @Option(value = "12")
    BEFORE_TIMEOUT(12),

    @Option(value = "13")
    TIMEOUT_SUCCESS(13),

    @Option(value = "14")
    TIMEOUT_FAILED(14),

    @Option(value = "15")
    KILLED(15);

    private final int code;

    TccStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public boolean isFinished() {
        return this == CONFIRM_SUCCESS || this == CANCEL_SUCCESS || this == BIZ_CANCEL_FAILED || this == TIMEOUT_SUCCESS
                || this == KILLED;
    }

    public boolean isAllowConfirm() {
        return this == TRY_SUCCESS || this == CONFIRM_SUCCESS;
    }

    public boolean isInTransaction() {
        return this != CREATED && !isFinished();
    }

    public boolean isRollbackOnly() {
        return isInTransaction() && !isAllowConfirm();
    }

    public boolean isCancelled() {
        return this == TRY_FAILED || this == CANCEL_SUCCESS || this == TIMEOUT_SUCCESS || this == BIZ_CANCEL_FAILED;
    }

    public boolean isConfirmed() {
        return this == CONFIRM_SUCCESS;
    }

    public static TccStatus fromCode(int code) {
        TccStatus[] values = values();
        if (values.length <= code)
            return null;
        return values[code];
    }
}
