/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.ConfigConstants;
import io.nop.config.router.ConfigRouterProcessor;

import java.util.HashMap;
import java.util.Map;

public class RouterConfigSource implements IConfigSource {
    private final IConfigSource baseSource;

    public RouterConfigSource(IConfigSource baseSource) {
        this.baseSource = baseSource;
    }

    public String getName() {
        return "router";
    }

    @Override
    public Map<String, ValueWithLocation> getConfigValues() {
        ConfigRouterProcessor processor = new ConfigRouterProcessor();
        Map<String, ValueWithLocation> vars = baseSource.getConfigValues();
        Map<String, ValueWithLocation> productRoute = processor.getRouteConfigVars(vars,
                ConfigConstants.CFG_CONFIG_PRODUCT_ROUTER);
        Map<String, ValueWithLocation> appRoute = processor.getRouteConfigVars(vars,
                ConfigConstants.CFG_CONFIG_APP_ROUTER);

        if (CollectionHelper.isEmptyMap(productRoute) && CollectionHelper.isEmptyMap(appRoute))
            return vars;

        Map<String, ValueWithLocation> merged = new HashMap<>();
        merged.putAll(vars);
        merged.putAll(productRoute);
        merged.putAll(appRoute);
        return merged;
    }

    @Override
    public void addOnChange(Runnable callback) {
        baseSource.addOnChange(callback);
    }

    @Override
    public void close() throws Exception {
        baseSource.close();
    }
}
