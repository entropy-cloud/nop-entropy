/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.impl;

import io.nop.commons.util.StringHelper;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IGenericType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GenericIntersectionTypeImpl extends AbstractCompositeType {
    private String typeName;

    public GenericIntersectionTypeImpl(List<IGenericType> subTypes) {
        super(subTypes);
    }

    @Override
    public String getRawTypeName() {
        Set<String> types = new LinkedHashSet<>();
        for (IGenericType subType : getSubTypes()) {
            types.add(subType.getRawTypeName());
        }
        return StringHelper.join(types, "&");
    }

    public String getTypeName() {
        if (typeName == null)
            typeName = StringHelper.join(getSubTypes(), "&");
        return typeName;
    }

    @Override
    public GenericTypeKind getKind() {
        return GenericTypeKind.INTERSECTION;
    }
}