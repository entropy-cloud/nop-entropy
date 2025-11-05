/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * 变量作用域
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-04
 */
public interface XLangVarScope {

    /** 获取当前作用域内所定义的变量 */
    default @NotNull Map<String, XLangVarDecl> getVars() {
        return Map.of();
    }
}
