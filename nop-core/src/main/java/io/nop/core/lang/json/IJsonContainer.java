/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.api.core.util.ICloneable;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;

public interface IJsonContainer extends ISourceLocationGetter, IDeepCloneable, ICloneable, IFreezable {
    @Override
    boolean frozen();

    @Override
    void freeze(boolean cascade);

    Object deepClone();

    int size();

    boolean isEmpty();

    String getComment();
}