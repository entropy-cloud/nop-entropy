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

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class PropsValueResolver implements IBeanPropValueResolver {
    private final Map<String, IBeanPropValueResolver> props;

    public PropsValueResolver(Map<String, IBeanPropValueResolver> props) {
        this.props = props;
    }

    @Override
    public String toConfigString() {
        return null;
    }

    @Override
    public XNode toConfigNode() {
        XNode node = XNode.make("props");
        for (Map.Entry<String, IBeanPropValueResolver> entry : props.entrySet()) {
            XNode prop = XNode.make("prop");
            prop.setAttr("key", entry.getKey());

            IBeanPropValueResolver resolver = entry.getValue();
            String value = resolver.toConfigString();
            prop.setContentValue(value);
            node.appendChild(prop);
        }
        return node;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        Properties ret = new Properties();

        for (Map.Entry<String, IBeanPropValueResolver> entry : props.entrySet()) {
            IBeanPropValueResolver prop = entry.getValue();
            Object value = prop.resolveValue(container, scope);
            if (value != null)
                ret.put(entry.getKey(), value);

        }
        return ret;
    }

    @Override
    public void collectConfigVars(Set<String> vars, boolean reactive) {
        for (IBeanPropValueResolver resolver : props.values()) {
            resolver.collectConfigVars(vars, reactive);
        }
    }

    @Override
    public void collectDepends(Set<String> depends) {
        for (IBeanPropValueResolver resolver : props.values()) {
            resolver.collectDepends(depends);
        }
    }
}