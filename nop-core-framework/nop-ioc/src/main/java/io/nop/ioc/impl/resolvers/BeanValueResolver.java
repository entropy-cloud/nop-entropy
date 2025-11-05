/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

import java.util.Map;
import java.util.Set;

public class BeanValueResolver implements IBeanPropValueResolver {
    private final Class<?> beanClass;
    private final Map<String, IBeanPropValueResolver> props;
    private final boolean skipIfEmpty;

    public BeanValueResolver(Class<?> beanClass, Map<String, IBeanPropValueResolver> props, boolean skipIfEmpty) {
        this.beanClass = beanClass;
        this.props = props;
        this.skipIfEmpty = skipIfEmpty;
    }

    @Override
    public String toConfigString() {
        return null;
    }

    @Override
    public XNode toConfigNode() {
        XNode node = XNode.make("bean");
        node.setAttr("class", beanClass.getName());
        for (Map.Entry<String, IBeanPropValueResolver> entry : props.entrySet()) {
            XNode prop = XNode.make("property");
            prop.setAttr("name", entry.getKey());
            XNode value = entry.getValue().toConfigNode();
            if (value != null) {
                prop.appendChild(value);
            } else {
                prop.setAttr("value", entry.getValue().toConfigString());
            }
            prop.setAttr("ioc:skip-if-empty", skipIfEmpty);
            node.appendChild(prop);
        }
        return node;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(beanClass);
        Object bean = beanModel.newInstance();
        for (Map.Entry<String, IBeanPropValueResolver> entry : props.entrySet()) {
            String propName = entry.getKey();
            Object value = entry.getValue().resolveValue(container, scope);

            if (skipIfEmpty && StringHelper.isEmptyObject(value))
                continue;

            beanModel.setProperty(bean, propName, value);
        }
        return bean;
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
