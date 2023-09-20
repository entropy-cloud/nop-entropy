package io.nop.boot;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface NopBootConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopBootConfigs.class);

    @Description("是否打印Nop平台的Banner")
    IConfigReference<Boolean> CFG_BANNER_ENABLED = varRef(s_loc, "nop.banner.enabled", Boolean.class, true);
}
