/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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

    @Description("扩展目录的VFS路径，每个子目录对应一个extension，其中包含extension.json。"
            + " 默认值为 `/extensions`（VFS 绝对路径，带前导斜杠），与 `nop.web.index-extensions-base-path` 默认值 `/extensions` 对齐。"
            + " 检索时优先在 VFS 中查找（可通过 Delta 定制覆盖），VFS 中不存在时自动 fallback 到"
            + " classpath 静态资源 `META-INF/resources/extensions/{id}/`（由 Spring/Quarkus 默认"
            + " `classpath:/META-INF/resources/**` 映射暴露为 `/extensions/{id}/...` HTTP 路径）。")
    IConfigReference<String> CFG_WEB_INDEX_EXTENSIONS_DIR = varRef(s_loc,
            "nop.web.index-extensions-dir", String.class, "/extensions");

    @Description("启用的扩展名称列表，逗号分隔。只有在此列表中的扩展才会被加载")
    IConfigReference<String> CFG_WEB_INDEX_EXTENSION_NAMES = varRef(s_loc,
            "nop.web.index-extension-names", String.class, null);

    @Description("扩展资源的HTTP访问基础路径，默认为/extensions")
    IConfigReference<String> CFG_WEB_INDEX_EXTENSIONS_BASE_PATH = varRef(s_loc,
            "nop.web.index-extensions-base-path", String.class, "/extensions");

    @Description("index.html的title文本，支持${var}模板变量")
    IConfigReference<String> CFG_WEB_INDEX_TITLE = varRef(s_loc,
            "nop.web.index-title", String.class, null);

    @Description("并行验证页面模型的线程数")
    IConfigReference<Integer> CFG_WEB_PAGE_VALIDATION_THREAD_COUNT = varRef(s_loc,
            "nop.web.page-validation-thread-count", Integer.class, 1);

    @Description("前端渲染模式：amis（默认）或 flux。设为 flux 时强制使用 flux-web 渲染管线。")
    IConfigReference<String> CFG_WEB_RENDER_MODE = varRef(s_loc,
            "nop.web.render-mode", String.class, "amis");

}
