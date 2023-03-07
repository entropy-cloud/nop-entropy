/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.biz;

public enum AuditType {
    DEFAULT,

    /**
     * 对于执行失败的操作，需要记录审计历史
     */
    AUDIT_FAILED,

    /**
     * 对于执行成功的操作也需要记录审计历史
     */
    AUDIT_SUCCESS,
}