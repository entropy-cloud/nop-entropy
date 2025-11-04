/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.collections.IKeyedElement;
import io.nop.core.type.IGenericType;

public interface IXplTagSlotArg extends IKeyedElement, ISourceLocationGetter {
    default String key() {
        return getName();
    }

    String getName();

    String getStdDomain();

    String getDisplayName();

    String getDescription();

    IGenericType getType();

    boolean isMandatory();

    boolean isImplicit();

    Object getDefaultValue();
}
