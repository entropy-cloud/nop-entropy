/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect;

import java.util.Set;

public interface IExtPropertyGetter extends IPropertyGetter {
    Set<String> getExtPropNames(Object obj);

    boolean isAllowExtProperty(Object obj, String name);
}
