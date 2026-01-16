/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.initialize;

import io.nop.api.core.config.AppConfig;
import io.nop.config.ConfigConstants;
import io.nop.config.starter.ConfigStarter;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;

public class ConfigInitializer implements ICoreInitializer {
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_START_CONFIG;
    }

    @Override
    public boolean isEnabled() {
        return AppConfig.var(ConfigConstants.CFG_CONFIG_ENABLED, true);
    }

    @Override
    public void initialize() {
        ConfigStarter.instance().start();
    }

    @Override
    public void destroy() {
        ConfigStarter.instance().stop();
    }
}