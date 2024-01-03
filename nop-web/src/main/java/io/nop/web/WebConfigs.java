package io.nop.web;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface WebConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(WebConfigs.class);
    @Description("启动时自动加载xjs和xcss文件")
    IConfigReference<Boolean> CFG_WEB_AUTO_LOAD_DYNAMIC_FILE = varRef(s_loc,
            "nop.web.auto-load-dynamic-file", Boolean.class, false);

    @Description("启用XCSS文件")
    IConfigReference<Boolean> CFG_WEB_USE_DYNAMIC_CSS = varRef(s_loc,
            "nop.web.use-dynamic-css", Boolean.class, true);

    @Description("启用XJS文件")
    IConfigReference<Boolean> CFG_WEB_USE_DYNAMIC_JS = varRef(s_loc,
            "nop.web.use-dynamic-js", Boolean.class, true);


}
