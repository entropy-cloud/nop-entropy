/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.quarkus.core.ioc.NopQuarkusBeanContainer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Optional;

import static io.nop.quarkus.core.QuarkusConstants.CONFIG_KEY_PROFILE_PARENT;

public class QuarkusIntegration {
    public static void start() {
        Config config = ConfigProvider.getConfig();
        String profile = config.getValue("quarkus.profile", String.class);

        Optional<String> parentProfile = ConfigProvider.getConfig().getOptionalValue(CONFIG_KEY_PROFILE_PARENT,
                String.class);

        if (!StringHelper.isEmpty(profile)) {
            System.setProperty(ApiConfigs.CFG_PROFILE.getName(), profile);
            AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_PROFILE, profile);

            if (profile.equals("dev")) {
                AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_DEBUG, true);
            }
        }

        if (parentProfile.isPresent()) {
            System.setProperty(ApiConfigs.CFG_PROFILE_PARENT.getName(), parentProfile.get());
            AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_PROFILE_PARENT, ConvertHelper.toCsvSet(parentProfile.get()));
        }

        BeanContainer.registerInstance(new NopQuarkusBeanContainer());
        if (BeanContainer.instance().containsBeanType(MeterRegistry.class)) {
            MeterRegistry meterRegistry = BeanContainer.instance().getBeanByType(MeterRegistry.class);
            GlobalMeterRegistry.registerInstance(meterRegistry);
        }
    }
}