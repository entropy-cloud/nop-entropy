/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.loader;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;
import io.nop.ioc.IocConstants;
import io.nop.ioc.impl.IBeanClassIntrospection;
import io.nop.ioc.impl.IBeanPropValueResolver;
import io.nop.ioc.impl.resolvers.BeanValueResolver;
import io.nop.ioc.impl.resolvers.ConfigMapResolver;
import io.nop.ioc.impl.resolvers.ConfigValueResolver;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigPropHelper {
    public static IBeanPropValueResolver buildConfigVarResolver(IBeanPropertyModel propModel, SourceLocation loc, String configPrefix,
                                                                boolean reactive,
                                                                IBeanClassIntrospection introspection) {
        IGenericType type = propModel.getType();
        if (introspection.isAllowedConfigVarType(type)) {
            String configVar = buildConfigVar(configPrefix, propModel);
            return new ConfigValueResolver(loc, reactive, configVar, propModel.getDefaultValue());
        } else if (isConfigBean(type)) {
            String configVar = buildConfigVar(configPrefix, propModel);
            return buildBeanResolver(type.getRawClass(), loc, configVar, reactive, introspection);
        } else if (type.isMapLike()) {
            return new ConfigMapResolver(loc, true, configPrefix, type, false);
        } else {
            return null;
        }
    }

    private static boolean isConfigBean(IGenericType type) {
        return !type.isAbstract() && type.getRawClass().isAnnotationPresent(ConfigBean.class);
    }

    static IBeanPropValueResolver buildBeanResolver(Class<?> beanClass, SourceLocation loc, String configPrefix,
                                                    boolean reactive,
                                                    IBeanClassIntrospection introspection) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(beanClass);
        Map<String, IBeanPropValueResolver> props = new LinkedHashMap<>();
        for (IBeanPropertyModel propModel : beanModel.getPropertyModels().values()) {
            if (propModel.isWritable()) {
                IBeanPropValueResolver resolver = buildConfigVarResolver(propModel, loc, configPrefix, reactive,introspection);
                if (resolver != null) {
                    props.put(propModel.getName(), resolver);
                }
            }
        }
        return new BeanValueResolver(beanClass, props, true);
    }

    static String buildConfigVar(String configPrefix, IBeanPropertyModel propModel) {
        String configVarName = propModel.getConfigVarName();
        if (configVarName == null)
            configVarName = StringHelper.camelCaseToHyphen(propModel.getName());
        return configPrefix + '.' + configVarName;
    }

    public static String getNormalizedId(String id) {
        if (id.startsWith(IocConstants.DEFAULT_ID_PREFIX))
            return id.substring(IocConstants.DEFAULT_ID_PREFIX.length());
        return id;
    }
}
