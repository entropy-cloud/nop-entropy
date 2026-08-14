package io.nop.plugin.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-plugin-api 的错误码定义。定义在此模块（而非 nop-plugin-manager 的 PluginManagerErrors），
 * 因为 {@code nop-plugin-support} 不依赖 {@code nop-plugin-manager}，而 support 与 manager
 * 都会抛出 INACTIVE / 定义缺失类错误（设计文档 01-architecture-baseline.md §7.1）。
 */
public interface PluginApiErrors {
    String ARG_PLUGIN_ID = "pluginId";

    /**
     * 插件没有 ACTIVATED 实例（aware 定义在 LOADED 且 0 实例时命令不可用；W4 完善实例数=1 路由）。
     */
    ErrorCode ERR_PLUGIN_INACTIVE =
            define("nop.err.plugin.inactive", "插件没有激活的实例，命令不可用:{pluginId}", ARG_PLUGIN_ID);

    /**
     * 插件定义文件缺失或无法解析（VFS 轨 id 对应的 *.plugin.xml 不存在；aware 插件 load() 时约定路径无定义文件）。
     */
    ErrorCode ERR_PLUGIN_DEFINITION_NOT_FOUND =
            define("nop.err.plugin.definition-not-found", "插件定义文件不存在或不可解析:{pluginId}", ARG_PLUGIN_ID);
}
