/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.JObject;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.nop.config.ConfigErrors.ARG_CONFIG_NAME;

public class ConfigSourceHelper {
    static final Logger LOG = LoggerFactory.getLogger(ConfigSourceHelper.class);

    public static Map<String, ValueWithLocation> buildConfigValues(SourceLocation loc, Map<?, ?> map) {
        if (map == null || map.isEmpty())
            return Collections.emptyMap();

        Map<String, ValueWithLocation> ret = new HashMap<>();

        buildConfigValues(loc, null, map, ret);
        return ret;
    }

    static void buildConfigValues(SourceLocation loc, String prefix, Map<?, ?> map,
                                  Map<String, ValueWithLocation> ret) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();

            // JObject可以保留每个值对应的源码位置
            SourceLocation valueLoc = getLocation(map, loc, (String) key);

            String name = (String) key;
            name = StringHelper.normalizeConfigVar(name);

            if (prefix != null) {
                name = prefix + '.' + name;
            }

            if (value instanceof Collection) {
                ValueWithLocation vl = ValueWithLocation.of(valueLoc, value);
                ret.put(name, vl);
                continue;
            }

            if (value instanceof Map) {
                buildConfigValues(loc, name, (Map<?, ?>) value, ret);
                continue;
            } else {
                if (!StringHelper.isValidConfigVar(name)) {
                    LOG.warn("nop.config.ignore-illegal-conf-name:name={},value={},loc={}", key, value, valueLoc);
                    continue;
                }

                ValueWithLocation vl = ValueWithLocation.of(valueLoc, value);
                ValueWithLocation old = ret.put(name, vl);
                if (old != null) {
                    LOG.warn("nop.err.config.duplicate-conf-name:name={},loc={},loc2={}", name, valueLoc,
                            old.getLocation());
                }
            }
        }
    }

    static private SourceLocation getLocation(Map<?, ?> map, SourceLocation loc, String key) {
        SourceLocation valueLoc = null;
        if (map instanceof JObject)
            valueLoc = ((JObject) map).getLocation(key);
        if (valueLoc == null)
            valueLoc = loc;
        return valueLoc;
    }

    public static void initConfigBean(Object bean, IConfigSource config, String prefix) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(bean.getClass());
        beanModel.forEachReadWriteProp(propModel -> {
            String name = propModel.getName();
            name = StringHelper.camelCaseToHyphen(name);
            String configName = prefix + "." + name;
            Object value = config.getConfigValue(configName, null);

            if (value != null) {
                value = ConvertHelper.convertTo(propModel.getRawClass(), value,
                        err -> new NopException(err).param(ARG_CONFIG_NAME, configName));

                propModel.setPropertyValue(bean, value);
            }
        });
    }

    public static boolean isChanged(Map<String, ValueWithLocation> oldVars, Map<String, ValueWithLocation> newVars) {
        if (oldVars.size() != newVars.size())
            return true;

        for (Map.Entry<String, ValueWithLocation> entry : oldVars.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue().getValue();

            ValueWithLocation newValue = newVars.get(name);
            if (newValue == null)
                return true;

            if (!Objects.equals(value, newValue.getValue()))
                return true;
        }
        return false;
    }
}