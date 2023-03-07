/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml;

import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;

@Locale("zh-CN")
public enum XNodeValuePosition {
    @Label("节点名")
    tag,

    @Label("节点属性")
    attr,

    @Label("节点的值")
    value,

    @Label("子节点")
    child,

    @Label("节点注释")
    comment;
}
