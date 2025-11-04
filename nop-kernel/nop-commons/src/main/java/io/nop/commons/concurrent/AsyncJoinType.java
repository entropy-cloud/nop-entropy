/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;

@Locale("zh-CN")
public enum AsyncJoinType {
    @Description("等待任何子步骤成功就可以返回")
    anySuccess,

    @Description("等待所有子步骤成功，如果任何子步骤失败就返回")
    anyFailure,

    @Description("等待任何子步骤完成返回，无论子步骤是否报错都正常返回")
    allComplete,

    @Description("等待所有子步骤完成再返回，并且要求所有步骤都成功，否则抛出异常")
    allSuccess;
}
