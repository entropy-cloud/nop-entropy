package io.nop.xui;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;

import static io.nop.api.core.config.AppConfig.varRef;

public interface XuiConfigs {
    @Description("全局设置的最大允许上传的文件大小。每个附件字段可以单独设置最大大小，但都在这个最大值的范围之内")
    IConfigReference<Long> CFG_FILE_UPLOAD_MAX_SIZE = varRef("nop.file.upload.max-size", Long.class, null);
}