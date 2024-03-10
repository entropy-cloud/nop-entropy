/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

/**
 * 对AST进行语法分析之后得到的Identifier类型
 */
public enum IdentifierKind {
    IMPORT_CLASS_DECL,

    /**
     * 函数参数变量声明
     */
    PARAM_DECL,

    /**
     * 局部变量声明
     */
    VAR_DECL,

    /**
     * 局部函数声明
     */
    FUNC_DECL,

    /**
     * 全局变量引用
     */
    GLOBAL_VAR_REF,

    /**
     * 全局函数引用
     */
    GLOBAL_FUNC_REF,

    /**
     * EvalScope变量引用
     */
    SCOPE_VAR_REF,

    /**
     * EvalScope函数引用
     */
    SCOPE_FUNC_REF,

    IMPORT_CLASS_REF,

    /**
     * 闭包变量引用
     */
    CLOSURE_VAR_REF,

    /**
     * 闭包函数引用。函数在闭包中定义，而不是在当前block中定义
     */
    CLOSURE_FUNC_REF,

    /**
     * 局部变量引用或者参数引用
     */
    VAR_REF,

    /**
     * 局部函数引用
     */
    FUNC_REF;

    public boolean isDeclaration() {
        return ordinal() <= FUNC_DECL.ordinal();
    }

    public boolean isGlobalOrScopeRef() {
        return ordinal() >= GLOBAL_VAR_REF.ordinal() && ordinal() <= SCOPE_FUNC_REF.ordinal();
    }

    public boolean isClosureRef() {
        return this == CLOSURE_VAR_REF || this == CLOSURE_FUNC_REF;
    }

    public boolean isLocalRef() {
        return ordinal() >= VAR_REF.ordinal();
    }

    public IdentifierKind getRefKind() {
        switch (this) {
            case IMPORT_CLASS_DECL:
                return IMPORT_CLASS_REF;
            case FUNC_DECL:
                return FUNC_REF;
            case PARAM_DECL:
            case VAR_DECL:
                return VAR_REF;
            case CLOSURE_FUNC_REF:
            case CLOSURE_VAR_REF:
                return this;
            default:
                throw new IllegalStateException("not declaration kind");
        }
    }

    public IdentifierKind getClosureRefKind() {
        switch (this) {
            case FUNC_DECL:
                return CLOSURE_FUNC_REF;
            case PARAM_DECL:
            case VAR_DECL:
                return CLOSURE_VAR_REF;
            case CLOSURE_FUNC_REF:
            case CLOSURE_VAR_REF:
                return this;
            default:
                throw new IllegalStateException("not declaration kind");
        }
    }
}