/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

import java.util.List;
import java.util.Set;

public class ConcatValueResolver implements IBeanPropValueResolver {
    private final List<IBeanPropValueResolver> resolvers;

    public ConcatValueResolver(List<IBeanPropValueResolver> resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public String toConfigString() {
        StringBuilder sb = new StringBuilder();
        for (IBeanPropValueResolver resolver : resolvers) {
            if (resolver instanceof FixedValueResolver) {
                sb.append(resolver.toConfigString());
            } else {
                sb.append("@{");
                sb.append(resolver.toConfigString());
                sb.append('}');
            }
        }
        return null;
    }

    @Override
    public void collectConfigVars(Set<String> vars, boolean reactive) {
        for (IBeanPropValueResolver resolver : resolvers) {
            resolver.collectConfigVars(vars, reactive);
        }
    }

    @Override
    public void collectDepends(Set<String> depends) {
        for (IBeanPropValueResolver resolver : resolvers) {
            resolver.collectDepends(depends);
        }
    }

    @Override
    public XNode toConfigNode() {
        return null;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        StringBuilder sb = new StringBuilder();
        for (IBeanPropValueResolver resolver : resolvers) {
            Object value = resolver.resolveValue(container, scope);
            if (value != null)
                sb.append(value);
        }
        return sb.toString();
    }
}