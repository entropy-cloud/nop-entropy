/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.commons.util.ClassHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

import java.util.Map;
import java.util.Set;

public class MapValueResolver implements IBeanPropValueResolver {
    private final Class<?> type;
    private final Map<String, IBeanPropValueResolver> props;
    private final boolean excludeNull;

    public MapValueResolver(Class<?> type, Map<String, IBeanPropValueResolver> props, boolean excludeNull) {
        this.type = type;
        this.props = props;
        this.excludeNull = excludeNull;
    }

    @Override
    public String toConfigString() {
        return null;
    }

    @Override
    public XNode toConfigNode() {
        XNode node = XNode.make("map");
        for (Map.Entry<String, IBeanPropValueResolver> entry : props.entrySet()) {
            XNode prop = XNode.make("entry");
            prop.setAttr("key", entry.getKey());

            IBeanPropValueResolver resolver = entry.getValue();
            String value = resolver.toConfigString();
            if (value != null) {
                prop.setAttr("value", value);
            } else {
                prop.appendChild(resolver.toConfigNode());
            }
            node.appendChild(prop);
        }
        return node;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        Map<String, Object> ret = ClassHelper.newMap(type);

        for (Map.Entry<String, IBeanPropValueResolver> entry : props.entrySet()) {
            IBeanPropValueResolver prop = entry.getValue();
            Object value = prop.resolveValue(container, scope);
            if (excludeNull && value == null)
                continue;
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