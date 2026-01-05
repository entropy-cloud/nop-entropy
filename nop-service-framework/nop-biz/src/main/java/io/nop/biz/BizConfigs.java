/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface BizConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(BizConfigs.class);
    @Description("使用in查询条件时最多允许多个候选值")
    IConfigReference<Integer> CFG_BIZ_QUERY_IN_OP_MAX_ALLOW_VALUE_SIZE = varRef(
            S_LOC, "nop.biz.query.in-op-max-allow-value-size", Integer.class, 100);

    @Description("最多允许前台发送几个左连接条件")
    IConfigReference<Integer> CFG_BIZ_QUERY_MAX_LEFT_JOIN_PROP_COUNT = varRef(
            S_LOC, "nop.biz.query.max-left-join-prop-count", Integer.class, 3
    );
}
