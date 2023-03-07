/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl.resolvers;

import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

public class BeanContainerValueResolver implements IBeanPropValueResolver {
    public static final BeanContainerValueResolver INSTANCE = new BeanContainerValueResolver();

    @Override
    public String toConfigString() {
        return "@bean:container";
    }

    @Override
    public XNode toConfigNode() {
        return null;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        return container;
    }
}
