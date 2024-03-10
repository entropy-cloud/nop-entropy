/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;

@Locale("zh-CN")
public enum XDslExtendPhase {
    @Description("装载base节点，并执行base节点的所有extends合并动作，返回合并后的base节点")
    buildBase,

    @Description("将当前节点与base节点合并，即执行完x:extends和x:exp-extends处理过程")
    mergeBase,

    @Description("执行x:post-extends处理过程")
    postExtends,

    @Description("对合并后的结果节点进行简单验证，主要检查属性的唯一性和是否非空")
    validate;
}
