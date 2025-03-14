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
    private final SourceLocation location;
    private final List<String> configVars;
    private final Object defaultValue;

    private final boolean reactive;
    private final boolean mandatory;

    public ConfigValueResolver(SourceLocation location,
                               boolean reactive, List<String> configVars, boolean mandatory, Object defaultValue) {
        this.location = location;
        this.reactive = reactive;
        this.configVars = configVars;
        this.mandatory = mandatory;
        this.defaultValue = defaultValue;
    }

    public ConfigValueResolver(SourceLocation location, boolean reactive, String configVar, Object defaultValue) {
        this(location, reactive, Collections.singletonList(configVar), false, defaultValue);
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
        int n = configVars.size();
        for (int i = 0; i < n; i++) {
            String configVar = configVars.get(i);
            Object value;
            if (configVar.endsWith(".*")) {
                value = container.getConfigValueWithPrefix(configVar.substring(0, configVar.length() - 2));
            } else {
                value = container.getConfigValue(configVar);
            }

            if (!StringHelper.isEmptyObject(value))
                return value;
        }

        if (mandatory) {
            throw new NopException(ERR_IOC_EMPTY_CONFIG_VAR).param(ARG_CONFIG_VARS, configVars);
        }

        if (n > 1) {
            String varName = configVars.get(n - 1);
            if(!varName.endsWith(".*")) {
                Class clazz = defaultValue == null ? String.class : defaultValue.getClass();

                // 调用getConfigReference会在configProvider中注册配置变量
                Object value = container.getConfigProvider().getConfigReference(varName, clazz, defaultValue, location).get();
                if (value == null)
                    value = defaultValue;
                return value;
            }
        }

        return defaultValue;
    }
}