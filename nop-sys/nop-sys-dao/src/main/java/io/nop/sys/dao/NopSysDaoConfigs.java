package io.nop.sys.dao;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface NopSysDaoConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopSysDaoConfigs.class);
    @Description("是否自动初始化缺省Sequence")
    IConfigReference<Boolean> CFG_SYS_INIT_DEFAULT_SEQUENCE =
            varRef(s_loc,"nop.sys.init-default-sequence", Boolean.class, true);
}
