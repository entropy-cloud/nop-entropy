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
    String ARG_PLUGIN_STATE = "pluginState";

    /**
     * 插件未激活（LOADED/UNLOADED/FAILED 态命令与服务不可用；激活态代理在 deactivate
     * 完成后调用同抛此码）。
     */
    ErrorCode ERR_PLUGIN_INACTIVE =
            define("nop.err.plugin.inactive", "插件未激活，命令与服务不可用:{pluginId}", ARG_PLUGIN_ID);

    /**
     * unload 守卫（单激活不变量，01 §三）：插件处于 ACTIVATED 或生命周期中间态
     * （ACTIVATING/DEACTIVATING）时禁止 unload——须先 deactivate 回到 LOADED
     * （FAILED 态已清理完毕，允许 unload）。定义在 api 模块（support 与 manager 均抛出）。
     */
    ErrorCode ERR_PLUGIN_NOT_DEACTIVATED =
            define("nop.err.plugin.not-deactivated", "插件未去激活，禁止卸载:{pluginId},{pluginState}",
                    ARG_PLUGIN_ID, ARG_PLUGIN_STATE);

    /**
     * 非状态机感知插件不支持新状态机生命周期方法（load/unload/activate/deactivate/
     * getService(s)/updateConfig）——default 实现显式失败（No Silent No-Op），
     * 实现层对非 aware 插件永不调用这些方法。
     */
    ErrorCode ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED =
            define("nop.err.plugin.lifecycle-not-supported",
                    "插件未参与状态机（非状态机感知），不支持该生命周期方法");

    /**
     * 插件定义文件缺失或无法解析（VFS 轨 id 对应的 *.plugin.xml 不存在；aware 插件 load() 时
     * 约定路径无定义文件且未声明容忍缺失）。
     */
    ErrorCode ERR_PLUGIN_DEFINITION_NOT_FOUND =
            define("nop.err.plugin.definition-not-found", "插件定义文件不存在或不可解析:{pluginId}", ARG_PLUGIN_ID);
}
