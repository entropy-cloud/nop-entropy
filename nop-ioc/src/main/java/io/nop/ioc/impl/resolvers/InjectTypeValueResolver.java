/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.core.lang.xml.XNode;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

public class InjectTypeValueResolver implements IBeanPropValueResolver {
    private final Class<?> beanType;
    private final boolean optional;

    private final boolean ignoreDepends;

    public InjectTypeValueResolver(Class<?> beanType, boolean optional, boolean ignoreDepends) {
        this.beanType = beanType;
        this.optional = optional;
        this.ignoreDepends = ignoreDepends;
    }

    @Override
    public String toConfigString() {
        return IocConstants.PREFIX_INJECT_TYPE + (ignoreDepends ? "~" : "") + (optional ? "?" : "")
                + beanType.getName();
    }

    @Override
    public XNode toConfigNode() {
        XNode node = XNode.make("ioc:inject");
        node.setAttr("type", beanType.getName());
        if (ignoreDepends)
            node.setAttr("ignore-depends", true);
        if (optional) {
            node.setAttr("optional", true);
        }
        return node;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        if (optional) {
            if (!container.containsBeanType(beanType))
                return null;
        }
        return container.getBeanByType(beanType, true);
    }
}