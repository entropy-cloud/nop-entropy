/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tcc.api;

public enum TccStatus {
    // 新建记录时处于created状态
    CREATED(0),

    TRYING(1),

    TRY_SUCCESS(2),

    // try失败，不需要cancel
    TRY_FAILED(3),

    // try的结果未知
    TRY_UNKNOWN(4),

    CONFIRMING(5),

    CONFIRM_SUCCESS(6),

    CONFIRM_FAILED(7),

    CANCELLING(8),

    CANCEL_SUCCESS(9),

    CANCEL_FAILED(10),

    /**
     * 因业务原因导致cancel失败，也无法再重试
     */
    BIZ_CANCEL_FAILED(11),

    BEFORE_TIMEOUT(12),

    TIMEOUT_SUCCESS(13),

    TIMEOUT_FAILED(14),

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
