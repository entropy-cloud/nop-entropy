/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

public enum VariableKind {
    CONST, LET,

    /**
     * 语义与javascript不同。如果变量已定义，则使用已有的变量，否则创建变量定义，但是变量的作用域仍然是当前block， 而不是提升到函数scope
     */
    VAR;
}
