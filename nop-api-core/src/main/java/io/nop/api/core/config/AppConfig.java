/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.config;

import io.nop.api.core.annotations.core.GlobalInstance;

import static io.nop.api.core.ApiConfigs.CFG_APPLICATION_LOCALE;
import static io.nop.api.core.ApiConfigs.CFG_APPLICATION_NAME;
import static io.nop.api.core.ApiConfigs.CFG_APPLICATION_TIMEZONE;
import static io.nop.api.core.ApiConfigs.CFG_APPLICATION_VERSION;
import static io.nop.api.core.ApiConfigs.CFG_DEBUG;
import static io.nop.api.core.ApiConfigs.CFG_PROFILE;

@SuppressWarnings("PMD.TooManyStaticImports")
@GlobalInstance
public class AppConfig {
    private static IConfigProvider s_provider = new SimpleConfigProvider();

    public static void registerConfigProvider(IConfigProvider provider) {
        s_provider = provider;
    }

    public static IConfigProvider getConfigProvider() {
        return s_provider;
    }

    public static String appName() {
        return CFG_APPLICATION_NAME.get();
    }

    public static String appVersion() {
        return CFG_APPLICATION_VERSION.get();
    }


    public static String appLocale() {
        return CFG_APPLICATION_LOCALE.get();
    }

    public static String defaultLocale() {
        return CFG_APPLICATION_LOCALE.get();
    }


    public static String appTimezone() {
        return CFG_APPLICATION_TIMEZONE.get();
    }

    public static boolean isDebugMode() {
        return CFG_DEBUG.get();
    }

    public static String activeProfile() {
        return CFG_PROFILE.get();
    }

    public static <T> IConfigReference<T> varRef(String name, Class<T> valueType, T defaultValue) {
        return s_provider.getConfigReference(name, valueType, defaultValue);
    }

    public static <T> IConfigReference<T> withOverride(IConfigReference<T> defaultRef, String name) {
        return new OverrideConfigReference<>(varRef(name, defaultRef.getValueType(), null), defaultRef);
    }

    public static Object var(String name) {
        return s_provider.getConfigValue(name, null);
    }

    public static <T> T var(String name, T defaultValue) {
        return s_provider.getConfigValue(name, defaultValue);
    }
}