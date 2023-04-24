/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl.resolvers;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static io.nop.ioc.IocErrors.ARG_CONFIG_VARS;
import static io.nop.ioc.IocErrors.ERR_IOC_EMPTY_CONFIG_VAR;

public class ConfigValueResolver implements IBeanPropValueResolver {
    private final List<String> configVars;
    private final Object defaultValue;

    private final boolean reactive;
    private final boolean mandatory;

    public ConfigValueResolver(boolean reactive, List<String> configVars, boolean mandatory, Object defaultValue) {
        this.reactive = reactive;
        this.configVars = configVars;
        this.mandatory = mandatory;
        this.defaultValue = defaultValue;
    }

    public ConfigValueResolver(boolean reactive, String configVar, Object defaultValue) {
        this(reactive, Collections.singletonList(configVar), false, defaultValue);
    }

    @Override
    public void collectConfigVars(Set<String> vars, boolean reactive) {
        if (this.reactive) {
            if (reactive)
                vars.addAll(configVars);
        } else {
            vars.addAll(configVars);
        }
    }

    @Override
    public String toConfigString() {
        return (reactive ? IocConstants.PREFIX_R_CFG : IocConstants.PREFIX_CFG) + StringHelper.join(configVars, ",")
                + (defaultValue == null ? "" : "|" + defaultValue);
    }

    @Override
    public XNode toConfigNode() {
        return null;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        for (int i = 0, n = configVars.size(); i < n; i++) {
            Object value = container.getConfigValue(configVars.get(i));
            if (!StringHelper.isEmptyObject(value))
                return value;
        }
        if (mandatory) {
            throw new NopException(ERR_IOC_EMPTY_CONFIG_VAR).param(ARG_CONFIG_VARS, configVars);
        }
        return defaultValue;
    }
}