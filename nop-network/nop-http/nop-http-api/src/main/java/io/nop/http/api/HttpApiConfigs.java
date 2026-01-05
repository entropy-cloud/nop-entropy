package io.nop.http.api;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface HttpApiConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(HttpApiConfigs.class);

    @Description("是否打印所有header")
    IConfigReference<Boolean> CFG_HTTP_LOG_PRINT_ALL_HEADERS = varRef(S_LOC, "nop.http.log.print-all-headers", Boolean.class, false);
}
