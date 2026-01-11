/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.SourceLocation;

/**
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2026-01-11
 */
public class PlaceholderConfigReference<T> implements IConfigReference<T> {
    public static final String PLACEHOLDER_START = "${";
    public static final String PLACEHOLDER_END = "}";

    private final IConfigReference<T> ref;

    private int refValueHash = 0;
    private T actualValue = null;

    public PlaceholderConfigReference(IConfigReference<T> ref) {
        this.ref = ref;
    }

    @Override
    public T get() {
        T value = ref.getAssignedValue();
        // 不处理缺省值
        if (value == null) {
            return ref.getDefaultValue();
        }

        int valueHash = value.hashCode();
        // 仅做一次替换
        if (actualValue == null) {
            refValueHash = valueHash;
            actualValue = replace(value, AppConfig.getConfigProvider());
        }
        // 若值已被更新，则返回新值
        else if (valueHash != refValueHash) {
            return value;
        }
        return actualValue;
    }

    @Override
    public String getName() {
        return ref.getName();
    }

    @Override
    public Class<T> getValueType() {
        return ref.getValueType();
    }

    @Override
    public T getDefaultValue() {
        return ref.getDefaultValue();
    }

    @Override
    public T getAssignedValue() {
        return ref.getAssignedValue();
    }

    @Override
    public IConfigValue<T> getProvider() {
        return ref.getProvider();
    }

    @Override
    public boolean isDynamic() {
        return ref.isDynamic();
    }

    @Override
    public SourceLocation getLocation() {
        return ref.getLocation();
    }

    private static <T> T replace(T value, IConfigProvider provider) {
        Object result = value;

        if (value instanceof String) {
            result = ApiStringHelper.renderTemplate((String) value, PLACEHOLDER_START, PLACEHOLDER_END, (key) -> {
                String val = provider.getConfigValue(key, null);

                return val != null ? val : PLACEHOLDER_START + key + PLACEHOLDER_END;
            });
        } //
        else if (value instanceof Collection) {
            Stream<?> stream = ((Collection<?>) value).stream().map((v) -> replace(v, provider));

            if (value instanceof Set) {
                result = stream.collect(Collectors.toCollection(LinkedHashSet::new));
            } else {
                result = stream.collect(Collectors.toList());
            }
        }
        return (T) result;
    }
}
