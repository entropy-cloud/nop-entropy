/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.Symbol;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast._gen._Identifier;
import io.nop.xlang.ast.definition.LocalVarDeclaration;

import static io.nop.core.CoreErrors.ARG_IDENTIFIER;
import static io.nop.core.CoreErrors.ERR_LANG_AST_NODE_INVALID_IDENTIFIER;
import static io.nop.xlang.XLangErrors.ARG_AST_NODE;

public class Identifier extends _Identifier {

    /**
     * 是否是成员变量
     */
    private boolean member;

    /**
     * 是否作为函数名来使用
     */
    private boolean function;

    private IdentifierKind identifierKind;

    private Symbol token;

    /**
     * 当Identifier是变量声明时，这里记录变量定义相关的信息
     */
    private LocalVarDeclaration varDeclaration;

    /**
     * 当Identifier是变量引用时，这里指向它所引用的变量定义
     */
    private XLangIdentifierDefinition resolvedDefinition;

    private boolean implicit;

    public boolean isImplicit() {
        return implicit;
    }

    public void setImplicit(boolean implicit) {
        this.implicit = implicit;
    }

    public static Identifier valueOf(SourceLocation loc, String name) {
        Guard.notEmpty(name, "name is empty");
        Identifier node = new Identifier();
        node.setLocation(loc);
        node.setName(name);
        return node;
    }

    public static Identifier implicitVar(SourceLocation loc, String name) {
        Identifier id = valueOf(loc, name);
        id.setImplicit(true);
        return id;
    }

    public String toString() {
        return getASTKind() + ":" + getName();
    }

    public IdentifierKind getIdentifierKind() {
        return identifierKind;
    }

    public void setIdentifierKind(IdentifierKind identifierKind) {
        this.identifierKind = identifierKind;
    }

    public LocalVarDeclaration getVarDeclaration() {
        return varDeclaration;
    }

    public void setVarDeclaration(LocalVarDeclaration declaredDefinition) {
        checkAllowChange();
        this.varDeclaration = declaredDefinition;
    }

    public Symbol getToken() {
        return token;
    }

    public void setToken(Symbol token) {
        this.token = token;
    }

    public boolean isFunction() {
        return function;
    }

    public void setFunction(boolean function) {
        if (this.function == function)
            return;

        checkAllowChange();
        this.function = function;
    }

    public boolean isMember() {
        return member;
    }

    public void setMember(boolean member) {
        if (this.member == member)
            return;

        checkAllowChange();
        this.member = member;
    }

    public XLangIdentifierDefinition getResolvedDefinition() {
        return resolvedDefinition;
    }

    public void setResolvedDefinition(XLangIdentifierDefinition resolvedDefinition) {
        checkAllowChange();
        this.resolvedDefinition = resolvedDefinition;
    }

    public XLangIdentifierDefinition getDefinition() {
        if (resolvedDefinition != null)
            return resolvedDefinition;
        return varDeclaration;
    }

    @Override
    public void validate() {
        if (!StringHelper.isValidJavaVarName(name))
            throw new NopException(ERR_LANG_AST_NODE_INVALID_IDENTIFIER).param(ARG_AST_NODE, this).param(ARG_IDENTIFIER,
                    name);
    }
}