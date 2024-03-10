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

    static MeterRegistry s_instance = new SimpleMeterRegistry();

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
    }
}