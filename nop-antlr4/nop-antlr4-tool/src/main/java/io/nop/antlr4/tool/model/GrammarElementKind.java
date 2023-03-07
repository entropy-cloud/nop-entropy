/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr4.tool.model;

public enum GrammarElementKind {
    RULE, TERMINAL, // 终结符号
    RULE_REF, // 引用已定义的规则
    STAR_BLOCK, // 0次或者多次重复
    OPTIONAL_BLOCK, // 可选规则
    OR_BLOCK, // 多个可选分支
    PLUS_BLOCK, // 一次或者多次重复
    SET_BLOCK, // 多个可选的终结符
    SEQ_BLOCK, // 顺序匹配
}
