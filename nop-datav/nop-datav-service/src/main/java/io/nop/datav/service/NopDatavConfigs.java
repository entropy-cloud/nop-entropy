package io.nop.datav.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface NopDatavConfigs {

    SourceLocation s_loc = SourceLocation.fromClass(NopDatavConfigs.class);

    @Description("单任务最大导出行数（超出抛 ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED，防 OOM）")
    IConfigReference<Integer> CFG_DATAV_EXPORT_MAX_ROWS = varRef(
            s_loc, "nop.datav.export.max-rows", Integer.class, 100000);

    @Description("单用户并发导出任务数上限（pending+running，超出抛 ERR_DATAV_EXPORT_CONCURRENCY_LIMIT）")
    IConfigReference<Integer> CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER = varRef(
            s_loc, "nop.datav.export.max-concurrent-per-user", Integer.class, 3);

    @Description("导出文件最大字节数（传给 IFileStore.saveFile 的 maxLength 上限）")
    IConfigReference<Long> CFG_DATAV_EXPORT_FILE_MAX_LENGTH = varRef(
            s_loc, "nop.datav.export.file-max-length", Long.class, 104857600L);
}
