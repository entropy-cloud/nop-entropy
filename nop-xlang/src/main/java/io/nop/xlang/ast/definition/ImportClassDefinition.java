/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast.definition;

import io.nop.core.reflect.IClassModel;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.XLangIdentifierDefinition;

public class ImportClassDefinition implements XLangIdentifierDefinition {
    private final IClassModel resolvedClass;

    public ImportClassDefinition(IClassModel resolvedClass) {
        this.resolvedClass = resolvedClass;
    }

    public String getClassName() {
        return resolvedClass.getClassName();
    }

    public IClassModel getClassModel() {
        return resolvedClass;
    }

    @Override
    public IGenericType getResolvedType() {
        return resolvedClass.getType();
    }

    @Override
    public boolean isAllowAssignment() {
        return false;
    }

}
