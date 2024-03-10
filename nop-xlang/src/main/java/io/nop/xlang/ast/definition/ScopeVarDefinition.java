/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast.definition;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.XLangIdentifierDefinition;

public class ScopeVarDefinition implements XLangIdentifierDefinition, ISourceLocationGetter {
    private SourceLocation location;
    private final String varName;

    private IGenericType resolvedType;

    private boolean allowAssignment;
    private boolean changed;

    public ScopeVarDefinition(String varName) {
        this.varName = varName;
    }

    public static ScopeVarDefinition readOnly(String varName, IGenericType resolvedType) {
        ScopeVarDefinition ret = new ScopeVarDefinition(varName);
        ret.setAllowAssignment(false);
        ret.setResolvedType(resolvedType);
        return ret;
    }

    public static ScopeVarDefinition mutable(String varName, IGenericType resolvedType) {
        ScopeVarDefinition ret = new ScopeVarDefinition(varName);
        ret.setAllowAssignment(true);
        ret.setResolvedType(resolvedType);
        return ret;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    @Override
    public IGenericType getResolvedType() {
        return resolvedType;
    }

    public void setResolvedType(IGenericType resolvedType) {
        this.resolvedType = resolvedType;
    }

    public String getVarName() {
        return varName;
    }

    @Override
    public boolean isAllowAssignment() {
        return allowAssignment;
    }

    public void setAllowAssignment(boolean allowAssignment) {
        this.allowAssignment = allowAssignment;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }
}