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

    // volatile：配置引用会被多线程并发get()，缓存字段的发布/更新必须保证跨线程可见，
    // 且refValueHash先于actualValue写入，保证看到缓存的线程也能看到配套的hash
    private volatile int refValueHash = 0;
    private volatile T actualValue = null;

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
        T cached = actualValue;
        // 仅做一次替换
        if (cached == null) {
            refValueHash = valueHash;
            cached = replace(value, AppConfig.getConfigProvider());
            actualValue = cached;
        }
        // 若值已被更新，则返回新值
        else if (valueHash != refValueHash) {
            return value;
        }
        return cached;
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
