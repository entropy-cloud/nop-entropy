/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalMeterRegistry {
    static final Logger LOG = LoggerFactory.getLogger(GlobalMeterRegistry.class);

    // 运行期可通过registerInstance替换，需要volatile保证跨线程可见
    static volatile MeterRegistry s_instance = new SimpleMeterRegistry();

    // 4.4-MA-04（plan 2562 裁定 B②）：宿主（Quarkus 集成层）注册的实例引用，供
    // unregisterInstance 判定撤销对象；null 表示当前无宿主注册待撤销
    static volatile MeterRegistry s_registeredInstance;

    public static MeterRegistry instance() {
        return s_instance;
    }

    public static void print() {
        String text = MeterPrinter.scrape(instance(), new MeterPrintConfig());
        LOG.info("metrics={}", text);
    }

    public static void registerInstance(MeterRegistry instance) {
        if (s_instance != instance) {
            s_instance.close();
            s_instance = instance;
        }
        s_registeredInstance = instance;
    }

    /**
     * 4.4-MA-04（plan 2562 裁定 B②）：撤销 {@link #registerInstance(MeterRegistry)}
     * 的注册面。仅当当前实例仍是宿主注册的那个实例时生效（期间被再次替换则不动新值）。
     * 不 close 宿主注册表——其生命周期归宿主（Arc/Quarkus）管理。缺省
     * SimpleMeterRegistry 已在注册时被 close，故回退到新的缺省实例。
     */
    public static void unregisterInstance() {
        MeterRegistry registered = s_registeredInstance;
        if (registered == null || s_instance != registered)
            return;
        s_registeredInstance = null;
        s_instance = new SimpleMeterRegistry();
    }
}