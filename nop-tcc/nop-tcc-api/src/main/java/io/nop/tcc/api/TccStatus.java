/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import io.nop.api.core.annotations.core.Option;

import java.util.Arrays;
import java.util.List;

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
    CONFIRM_FAILED(6),

    @Option(value = "7")
    CANCELLING(7),

    @Option(value = "8")
    CANCEL_FAILED(8),

    @Option(value = "9")
    BEFORE_TIMEOUT(9),

    @Option(value = "10")
    TIMEOUT_FAILED(10),

    @Option(value = "11")
    CONFIRM_SUCCESS(11), // finished

    @Option(value = "12")
    CANCEL_SUCCESS(12), // finished

    /**
     * 因业务原因导致cancel失败，也无法再重试
     */
    @Option(value = "13")
    BIZ_CANCEL_FAILED(13), // finished

    @Option(value = "14")
    TIMEOUT_SUCCESS(14), // finsiehd

    @Option(value = "15")
    KILLED(15);  // finished

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

    public static List<Integer> getFinishedStatus() {
        return Arrays.asList(CONFIRM_SUCCESS.getCode(), CANCEL_SUCCESS.getCode(), BIZ_CANCEL_FAILED.getCode(),
                TIMEOUT_SUCCESS.getCode(), KILLED.getCode());
    }

    public static TccStatus fromCode(int code) {
        TccStatus[] values = values();
        if (values.length <= code)
            return null;
        return values[code];
    }
}
