/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.api.actor;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;

/**
 * @author canonical_entropy@163.com
 */
@Locale("zh-CN")
public enum WfAssignmentSelection {
    @Label("自动")
    @Description("系统自动选择actor，不需要用户手工选择")
    auto,

    @Label("单选")
    @Description("需要从assignment指定的actor中选择唯一的一个")
    single,

    @Label("多选")
    multiple,
}