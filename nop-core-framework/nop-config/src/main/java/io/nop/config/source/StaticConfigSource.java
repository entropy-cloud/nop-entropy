/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.commons.util.objects.ValueWithLocation;

import java.util.Map;

/**
 * 静态配置，不会改变。
 */
public class StaticConfigSource implements IConfigSource {
    private final String name;
    private final Map<String, ValueWithLocation> configValues;
    private final boolean hasProfilePrefixedVars;

    public StaticConfigSource(String name, Map<String, ValueWithLocation> configValues) {
        this.name = name;
        this.configValues = configValues;
        this.hasProfilePrefixedVars = configValues.keySet().stream()
                .anyMatch(k -> !k.isEmpty() && k.charAt(0) == '%');
    }

    public String getName() {
        return name;
    }

    @Override
    public Map<String, ValueWithLocation> getConfigValues() {
        return configValues;
    }

    @Override
    public boolean hasProfilePrefixedVars() {
        return hasProfilePrefixedVars;
    }

    @Override
    public void addOnChange(Runnable callback) {

    }

    @Override
    public void close() {

    }
}
