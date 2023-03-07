/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef;

import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.collections.IKeyedElement;

public interface IXDefAttribute extends IKeyedElement, ISourceLocationGetter, IFreezable {
    default String key() {
        return getName();
    }

    String getName();

    String getPropName();

    XDefTypeDecl getType();

    default boolean isUnknownAttr() {
        return getName().equals("*");
    }

    default boolean isMandatory() {
        XDefTypeDecl type = getType();
        if (type == null)
            return false;
        return type.isMandatory();
    }
}