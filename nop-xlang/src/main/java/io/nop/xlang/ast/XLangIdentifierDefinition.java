/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.core.type.IGenericType;

/**
 * 所有identifier的定义信息（包括函数定义，导入类，全局变量定义，局部变量定义）
 */
public interface XLangIdentifierDefinition {
    IGenericType getResolvedType();

    default int getVarSlot() {
        return -1;
    }

    /**
     * 当变量被用在闭包中，且存在对变量的修改时，闭包变量需要作为EvalReference来传递
     *
     * @return
     */
    default boolean isUseRef() {
        return false;
    }

    /**
     * 是否允许被赋值语句修改
     */
    default boolean isAllowAssignment() {
        return false;
    }
}
