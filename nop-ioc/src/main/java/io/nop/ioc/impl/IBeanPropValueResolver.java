/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl;

import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;

import java.util.Set;

public interface IBeanPropValueResolver {
    String toConfigString();

    XNode toConfigNode();

    default boolean isSkipIfEmpty(){
        return false;
    }

    Object resolveValue(IBeanContainerImplementor container, IBeanScope scope);

    default void collectConfigVars(Set<String> vars, boolean reactive) {

    }

    default void collectDepends(Set<String> depends) {

    }
}
