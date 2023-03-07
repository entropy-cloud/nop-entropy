/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.collections.IKeyedElement;
import io.nop.core.type.IGenericType;

/**
 * {@link IXplTagAttribute}和{@link IXplTagSlot}的公共基类，它们在编译时都转化为XLang中的变量定义。
 */
public interface IXplTagVariable extends ISourceLocationGetter, IKeyedElement {
    default String key() {
        return getName();
    }

    String getVarName();

    String getName();

    String getDisplayName();

    String getDescription();

    IGenericType getType();
}
