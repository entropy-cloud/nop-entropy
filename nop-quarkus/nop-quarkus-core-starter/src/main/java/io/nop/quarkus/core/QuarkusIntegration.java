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
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.quarkus.core.ioc.NopQuarkusBeanContainer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Optional;

import static io.nop.quarkus.core.QuarkusConstants.CONFIG_KEY_PROFILE_PARENT;

public class QuarkusIntegration {

    // 4.4-MA-04（plan 2562 裁定 B②）：start 注册面前快照，供 stop 回滚
    static volatile IBeanContainer s_previousContainer;
    static volatile boolean s_containerRegistered;
    static volatile boolean s_meterRegistered;
    static volatile boolean s_profilePropSet;
    static volatile boolean s_profileParentPropSet;

    public static void start() {
        applyConfigOverrides();
        registerQuarkusContainer();
        registerMeterRegistryIfPresent();
    }

    /**
     * 4.4-MA-04（plan 2562 裁定 B②）：撤销 {@link #start()} 的注册面。与
     * IocCoreInitializer.destroy（:60-69）同构：先撤销计量注册面，再回写容器注册表指针。
     * 注意：NopQuarkusBeanContainer.stop/restart 为空体，不能作为配对目标（rule 24），
     * 本方法的配对锚点是 start() 写入的全局注册面，而非该 bean 的生命周期方法。
     *
     * <p>由 {@link QuarkusIntegrationShutdownObserver} 在 CDI ShutdownEvent 上调用，
     * 仅撤销 Quarkus 集成层注册面；CoreInitialization.destroy 由各 app main 自行执行，
     * 此处不重复 destroy。</p>
     */
    public static void stop() {
        if (s_meterRegistered) {
            GlobalMeterRegistry.unregisterInstance();
            s_meterRegistered = false;
        }

        // System property 为精确、无副作用的撤销面
        if (s_profilePropSet) {
            System.clearProperty(ApiConfigs.CFG_PROFILE.getName());
            s_profilePropSet = false;
        }
        if (s_profileParentPropSet) {
            System.clearProperty(ApiConfigs.CFG_PROFILE_PARENT.getName());
            s_profileParentPropSet = false;
        }

        // ConfigProvider 动态覆盖值（CFG_PROFILE/CFG_DEBUG/CFG_PROFILE_PARENT）显式 no-op-with-reason：
        // IConfigProvider 仅有 reset()（整体重置会连带清除宿主或外部设置的无关动态覆盖），
        // 无按键移除 API；profile 覆盖在 JVM 关闭阶段残留无观测后果，故保留并在此登记。

        // IocCoreInitializer.destroy 回写模式：恢复注册前的容器指针（可能为 null，即注销注册表）
        if (s_containerRegistered) {
            IBeanContainer previous = s_previousContainer;
            BeanContainer.registerInstance(previous);
            s_previousContainer = null;
            s_containerRegistered = false;
        }
    }

    static void applyConfigOverrides() {
        Config config = ConfigProvider.getConfig();
        String profile = config.getValue("quarkus.profile", String.class);

        Optional<String> parentProfile = ConfigProvider.getConfig().getOptionalValue(CONFIG_KEY_PROFILE_PARENT,
                String.class);

        if (!StringHelper.isEmpty(profile)) {
            System.setProperty(ApiConfigs.CFG_PROFILE.getName(), profile);
            s_profilePropSet = true;
            AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_PROFILE, profile);

            if (profile.equals("dev")) {
                AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_DEBUG, true);
            }
        }

        if (parentProfile.isPresent()) {
            System.setProperty(ApiConfigs.CFG_PROFILE_PARENT.getName(), parentProfile.get());
            s_profileParentPropSet = true;
            AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_PROFILE_PARENT, ConvertHelper.toCsvSet(parentProfile.get()));
        }
    }

    static void registerQuarkusContainer() {
        s_previousContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
        BeanContainer.registerInstance(new NopQuarkusBeanContainer());
        s_containerRegistered = true;
    }

    static void registerMeterRegistryIfPresent() {
        if (BeanContainer.instance().containsBeanType(MeterRegistry.class)) {
            MeterRegistry meterRegistry = BeanContainer.instance().getBeanByType(MeterRegistry.class);
            GlobalMeterRegistry.registerInstance(meterRegistry);
            s_meterRegistered = true;
        }
    }
}
