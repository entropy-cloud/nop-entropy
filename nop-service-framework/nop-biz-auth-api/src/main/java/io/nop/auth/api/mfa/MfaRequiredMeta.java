/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.mfa;

import java.io.Serializable;

/**
 * {@link MfaRequired} 注解在 {@code GraphQLFieldDefinition} 上的元数据载体。
 * <p>
 * 注解无属性（存在即敏感），故元数据为无状态标记（共享 {@link #INSTANCE} 单例），
 * 对齐 {@code BizMakerCheckerMeta} 的传播链先例（builder 读取 → fieldDefinition 持有 →
 * deepClone/merge 拷贝）。Serializable 以随元数据结构安全传递。
 */
public final class MfaRequiredMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final MfaRequiredMeta INSTANCE = new MfaRequiredMeta();

    private MfaRequiredMeta() {
    }

    @Override
    public String toString() {
        return "MfaRequiredMeta";
    }
}
