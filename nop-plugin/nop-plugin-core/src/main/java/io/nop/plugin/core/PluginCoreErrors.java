package io.nop.plugin.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface PluginCoreErrors {
    String ARG_PARAM_NAME = "paramName";
    String ARG_FILE_NAME = "fileName";

    ErrorCode ERR_PLUGIN_MISSING_CONFIG_FILE =
            define("nop.err.plugin.missing-config-file", "插件中缺少nop/plugin.json文件");

    ErrorCode ERR_PLUGIN_INVALID_PARAM_NAME =
            define("nop.err.plugin.invalid-param-name", "非法的参数名{paramName}", ARG_PARAM_NAME);

    ErrorCode ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL =
            define("nop.err.plugin.download-rename-file-fail", "下载插件包后重命名文件失败:{fileName}");

    ErrorCode ERR_PLUGIN_NO_PLUGIN_CLASS_NAME =
            define("nop.err.plugin.no-plugin-class-name", "插件配置文件中缺少pluginClassName属性");
}