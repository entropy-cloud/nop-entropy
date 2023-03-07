/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.metrics;

import io.micrometer.core.instrument.config.NamingConvention;

import java.time.Duration;

public class MeterPrintConfig {
    private boolean logInactive;
    private Duration step = Duration.ofMinutes(1);
    private NamingConvention namingConvention = NamingConvention.snakeCase;

    public NamingConvention getNamingConvention() {
        return namingConvention;
    }

    public void setNamingConvention(NamingConvention namingConvention) {
        this.namingConvention = namingConvention;
    }

    public NamingConvention namingConvention() {
        return namingConvention;
    }

    public boolean isLogInactive() {
        return logInactive;
    }

    public boolean logInactive() {
        return logInactive;
    }

    public void setLogInactive(boolean logInactive) {
        this.logInactive = logInactive;
    }

    public Duration step() {
        return step;
    }

    public Duration getStep() {
        return step;
    }

    public void setStep(Duration step) {
        this.step = step;
    }
}
