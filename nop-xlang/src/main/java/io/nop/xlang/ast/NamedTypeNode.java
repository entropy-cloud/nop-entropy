/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.core.reflect.IClassModel;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast._gen._NamedTypeNode;

public abstract class NamedTypeNode extends _NamedTypeNode {
    private transient IGenericType typeInfo;
    private transient IClassModel classModel;

    public IClassModel getClassModel() {
        return classModel;
    }

    public void setClassModel(IClassModel classModel) {
        this.classModel = classModel;
    }

    @JsonIgnore
    public IGenericType getTypeInfo() {
        return typeInfo;
    }

    public void setTypeInfo(IGenericType typeInfo) {
        this.typeInfo = typeInfo;
    }
}