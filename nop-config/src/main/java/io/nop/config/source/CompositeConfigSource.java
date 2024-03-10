/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.commons.util.IoHelper;
import io.nop.commons.util.objects.ValueWithLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeConfigSource implements IConfigSource {
    // private static final Logger LOG = LoggerFactory.getLogger(CompositeConfigSource.class);

    private final List<IConfigSource> configSources;

    public CompositeConfigSource(List<IConfigSource> configSources) {
        this.configSources = configSources;
    }

    public List<IConfigSource> getConfigSources() {
        return configSources;
    }

    public String getName() {
        return "composite";
    }

    @Override
    public Map<String, ValueWithLocation> getConfigValues() {
        Map<String, ValueWithLocation> ret = new HashMap<>();
        for (int i = configSources.size() - 1; i >= 0; i--) {
            IConfigSource configSource = configSources.get(i);
            ret.putAll(configSource.getConfigValues());
        }
        return ret;
    }

    public ValueWithLocation getConfigVar(String name) {
        for (IConfigSource source : configSources) {
            ValueWithLocation ref = source.getConfigValue(name);
            if (ref != null)
                return ref;
        }
        return null;
    }

    @Override
    public void addOnChange(Runnable callback) {
        for (IConfigSource configSource : configSources) {
            configSource.addOnChange(callback);
        }
    }

    @Override
    public void close() {
        IoHelper.safeCloseAll(configSources);
    }
}
