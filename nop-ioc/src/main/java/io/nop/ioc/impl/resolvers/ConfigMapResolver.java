/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

import java.util.Map;
import java.util.Set;

import static io.nop.ioc.IocErrors.ARG_CONFIG_PREFIX;
import static io.nop.ioc.IocErrors.ERR_IOC_NO_CONFIG_WITH_PREFIX;

public class ConfigMapResolver implements IBeanPropValueResolver {
    private final SourceLocation location;
    private final String configPrefix;
    private final IGenericType type;

    private final boolean reactive;
    private final boolean mandatory;

    public ConfigMapResolver(SourceLocation location,
                             boolean reactive, String configPrefix,
                             IGenericType type,
                             boolean mandatory) {
        this.location = location;
        this.reactive = reactive;
        this.configPrefix = configPrefix;
        this.type = type;
        this.mandatory = mandatory;
    }

    @Override
    public void collectConfigVars(Set<String> vars, boolean reactive) {
        if (this.reactive) {
            if (reactive)
                vars.add(configPrefix + ".*");
        } else {
            vars.add(configPrefix + ".*");
        }
    }

    @Override
    public String toConfigString() {
        return (reactive ? IocConstants.PREFIX_R_CFG : IocConstants.PREFIX_CFG) + configPrefix + ".*";
    }

    @Override
    public XNode toConfigNode() {
        return null;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        Map<String, Object> data = container.getConfigValueWithPrefix(configPrefix);

        if (mandatory && CollectionHelper.isEmptyMap(data)) {
            throw new NopException(ERR_IOC_NO_CONFIG_WITH_PREFIX).param(ARG_CONFIG_PREFIX, configPrefix);
        }
        if (type != null)
            return BeanTool.castBeanToType(data, type);

        return data;
    }
}