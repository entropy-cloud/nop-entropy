/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

/**
 * 执行后端能力集（后端注册条目声明）：静态生成物（构建期生成类）与动态翻译（运行时树翻译）。
 */
public enum EvalBackendCapability {
    /** 构建期静态生成物：扫描清单成员资源的生成类绑定（java 后端形态） */
    STATIC_GENERATED,

    /** 运行时动态翻译：Executable 树翻译为可 JIT 执行体（truffle 后端形态） */
    DYNAMIC_TRANSLATION
}
