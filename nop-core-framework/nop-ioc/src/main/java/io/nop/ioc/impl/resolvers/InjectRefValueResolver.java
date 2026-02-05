/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.api.core.util.Guard;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.impl.IBeanPropValueResolver;

import java.util.Set;

public class InjectRefValueResolver implements IBeanPropValueResolver {
    private final String ref;
    private final boolean optional;

    private final boolean ignoreDepends;

    private final BeanDefinition resolvedBean;

    public InjectRefValueResolver(String ref, boolean optional, boolean ignoreDepends, BeanDefinition resolvedBean) {
        this.ref = Guard.notEmpty(ref, "ref");
        this.optional = optional;
        this.ignoreDepends = ignoreDepends || (resolvedBean != null && resolvedBean.isIocForceLazyProperty());
        this.resolvedBean = resolvedBean;
    }

    public BeanDefinition getResolvedBean() {
        return resolvedBean;
    }

    @Override
    public String toConfigString() {
        if (resolvedBean != null)
            return null;
        return IocConstants.PREFIX_INJECT_REF + (ignoreDepends ? "~" : "") + (optional ? "?" : "") + ref;
    }

    @Override
    public XNode toConfigNode() {
        XNode node = XNode.make("ref");
        node.setAttr("bean", ref);
        if (ignoreDepends)
            node.setAttr("ioc:ignore-depends", true);
        if (optional) {
            node.setAttr("ioc:optional", true);
        }
        if (resolvedBean != null) {
            node.setAttr("ext:resolved-loc", resolvedBean.getLocation());
            if (resolvedBean.getTrace() != null) {
                node.setAttr("ext:resolved-trace", resolvedBean.getTrace());
            }
        }
        return node;
    }

    @Override
    public void collectDepends(Set<String> depends) {
        if (!ignoreDepends) {
            depends.add(ref);
        }
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        if (optional) {
            if (!container.containsBean(ref))
                return null;
        }
        return container.getBean(ref, true);
    }
}