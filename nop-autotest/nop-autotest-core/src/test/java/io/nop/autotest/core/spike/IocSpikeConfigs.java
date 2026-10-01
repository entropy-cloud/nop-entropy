package io.nop.autotest.core.spike;

import io.nop.api.core.util.SourceLocation;

/**
 * Spike-local config reference for the IoC start-mode switch, mirroring
 * io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_MODE (the ioc module is a
 * binary dependency here; the constant name is stable across nop-ioc 2.0.0-SNAPSHOT).
 */
public final class IocSpikeConfigs {
    public static final SourceLocation s_loc = SourceLocation.fromClass(IocSpikeConfigs.class);

    public static final io.nop.api.core.config.IConfigReference<String> APP_BEANS_CONTAINER_START_MODE =
            io.nop.api.core.config.AppConfig.varRef(s_loc, "nop.ioc.app-beans-container.start-mode", String.class, null);

    private IocSpikeConfigs() {
    }
}
