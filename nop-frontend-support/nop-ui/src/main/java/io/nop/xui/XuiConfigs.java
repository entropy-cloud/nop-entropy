/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface XuiConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(XuiConfigs.class);
    @Description("全局设置的最大允许上传的文件大小。每个附件字段可以单独设置最大大小，但都在这个最大值的范围之内")
    IConfigReference<Long> CFG_FILE_UPLOAD_MAX_SIZE = varRef(s_loc, "nop.file.upload.max-size", Long.class, null);
}