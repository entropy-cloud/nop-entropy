/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.api;

import io.nop.api.core.util.ISourceLocationGetter;

import java.util.List;
import java.util.Set;

public interface IBeanDefinition extends ISourceLocationGetter {
    String getId();

    Set<String> getNames();

    String getScope();

    List<Class<?>> getBeanTypes();

    IBeanPointcut getIocPointcut();
}