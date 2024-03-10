/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast.definition;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.Symbol;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.OptionalValue;
import io.nop.core.type.IGenericType;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IdentifierKind;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.scope.LexicalScope;

import static io.nop.xlang.XLangErrors.ARG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLANG_IDENTIFIER_IS_KEYWORD;

/**
 * 对应于局部变量定义、局部函数定义或者函数参数定义
 */
public class LocalVarDeclaration implements XLangIdentifierDefinition {
    /**
     * 唯一标识，可以作为Map的key来查找identifier关联的信息
     */
    private Symbol token;

    private SourceLocation location;

    private IdentifierKind identifierKind;

    private boolean allowAssignment;

    /**
     * 如果变量是常量，则这里对应于在编译期可以推导得到的常量值。
     */
    private OptionalValue constValue = OptionalValue.UNDEFINED;

    private IGenericType resolvedType;

    /**
     * 是否有赋值语句修改此变量的值
     */
    private boolean changed;

    /**
     * 该变量是否被使用
     */
    private boolean used;

    /**
     * 作为闭包变量被使用。如果闭包变量存在被修改的可能，则会创建为EvalReference类型。
     */
    private boolean usedInClosure;

    /**
     * 所有参数和变量在运行时都存放在EvalFrame中，stackSlot为frame中的变量下标
     */
    private int varSlot = -1;

    private LexicalScope lexicalScope;

    public LocalVarDeclaration(SourceLocation location, Symbol token, IdentifierKind identifierKind,
                               boolean allowAssignment) {
        this.location = location;
        this.token = token;
        this.allowAssignment = allowAssignment;
        this.identifierKind = identifierKind;
    }

    public String toString() {
        return getToken().getText() + ":kind=" + identifierKind + ",slot=" + varSlot + ",used=" + used + ",changed="
                + changed + ",allowAssign=" + allowAssignment + ",usedInClosure=" + usedInClosure + ",loc="
                + getLocation();
    }

    /**
     * 对于不可变的常量值，可以直接内联，不用保持变量引用
     *
     * @return
     */
    public boolean isInlineVar() {
        if (constValue.isPresent()) {
            Object value = constValue.getValue();
            if (value == null)
                return true;
            return XLangConstants.IMMUTABLE_PRIMITIVE_TYPES.contains(value.getClass());
        }
        return false;
    }

    public OptionalValue getConstValue() {
        return constValue;
    }

    public void setConstValue(OptionalValue constValue) {
        this.constValue = constValue;
    }

    public boolean isFuncDecl() {
        return identifierKind == IdentifierKind.FUNC_DECL;
    }

    public LexicalScope getFunctionScope() {
        return lexicalScope;
    }

    public void setFunctionScope(LexicalScope lexicalScope) {
        this.lexicalScope = lexicalScope;
    }

    public Symbol getToken() {
        return token;
    }

    public void setToken(Symbol token) {
        this.token = token;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setAllowAssignment(boolean allowAssignment) {
        this.allowAssignment = allowAssignment;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public boolean isUseRef() {
        return isChanged() && isUsedInClosure();
    }

    public boolean isUsedInClosure() {
        return usedInClosure;
    }

    public void setUsedInClosure(boolean usedInClosure) {
        this.usedInClosure = usedInClosure;
    }

    public boolean isAllowAssignment() {
        return allowAssignment;
    }

    public String getIdentifierName() {
        return token.getText();
    }

    public IdentifierKind getIdentifierKind() {
        return identifierKind;
    }

    public void setIdentifierKind(IdentifierKind identifierKind) {
        this.identifierKind = identifierKind;
    }

    public IGenericType getResolvedType() {
        return resolvedType;
    }

    public void setResolvedType(IGenericType resolvedType) {
        this.resolvedType = resolvedType;
    }

    public int getVarSlot() {
        return varSlot;
    }

    public void setVarSlot(int varSlot) {
        this.varSlot = varSlot;
    }

    public static LocalVarDeclaration makeVarDeclaration(Identifier identifier, IdentifierKind kind,
                                                         boolean allowAssignment) {
        if (identifier.getVarDeclaration() != null)
            return identifier.getVarDeclaration();

        if (StringHelper.isXLangKeyword(identifier.getName()))
            throw new NopEvalException(ERR_XLANG_IDENTIFIER_IS_KEYWORD).param(ARG_NAME, identifier.getName())
                    .source(identifier);

        identifier.setIdentifierKind(kind);
        identifier.setToken(Symbol.of(identifier.getName()));
        LocalVarDeclaration decl = new LocalVarDeclaration(identifier.getLocation(), identifier.getToken(), kind,
                allowAssignment);
        identifier.setVarDeclaration(decl);
        return decl;
    }
}