/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.source;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.commons.util.objects.ValueWithLocation;

import java.util.Map;

/**
 * 类似于Spring的PropertySource，对应一组配置变量
 */
public interface IConfigSource extends AutoCloseable {
    String getName();

    /**
     * configVars为不变集合，它不会被修改，只会被整体替换。当配置发生变化时，返回的configVars集合将会自动被替换。
     *
     * @return 由本配置源所提供的配置变量
     */
    Map<String, ValueWithLocation> getConfigValues();

    default ValueWithLocation getConfigValue(String name) {
        return getConfigValues().get(name);
    }

    default <T> T getConfigValue(String name, T defaultValue) {
        ValueWithLocation vl = getConfigValue(name);
        if (vl == null)
            return defaultValue;
        Object value = vl.getValue();
        if (ApiStringHelper.isEmptyObject(value))
            return defaultValue;

        if (defaultValue != null) {
            value = ConvertHelper.convertTo(defaultValue.getClass(), value, NopException::new);
        }
        return (T) value;
    }

    /**
     * 当配置发生变化时，会自动触发回调函数。在回调函数中，可以通过configSource.getConfigVars()来获得当前最新的配置变量集合。
     *
     * @param callback 回调函数
     */
    void addOnChange(Runnable callback);
}
