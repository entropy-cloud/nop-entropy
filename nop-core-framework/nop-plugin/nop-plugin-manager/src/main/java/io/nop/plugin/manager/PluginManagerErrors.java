package io.nop.plugin.manager;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface PluginManagerErrors {
    String ARG_PARAM_NAME = "paramName";
    String ARG_FILE_NAME = "fileName";
    String ARG_INSTANCE_KEY = "instanceKey";
    String ARG_INSTANCE_KEYS = "instanceKeys";
    String ARG_BEAN_TYPE = "beanType";
    String ARG_ACTIVATOR = "activator";

    ErrorCode ERR_PLUGIN_MISSING_CONFIG_FILE =
            define("nop.err.plugin.missing-config-file", "插件中缺少nop/plugin.json文件");

    ErrorCode ERR_PLUGIN_INVALID_PARAM_NAME =
            define("nop.err.plugin.invalid-param-name", "非法的参数名{paramName}", ARG_PARAM_NAME);

    ErrorCode ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL =
            define("nop.err.plugin.download-rename-file-fail", "下载插件包后重命名文件失败:{fileName}");

    ErrorCode ERR_PLUGIN_NO_PLUGIN_CLASS_NAME =
            define("nop.err.plugin.no-plugin-class-name", "插件配置文件中缺少pluginClassName属性");

    /**
     * 定义未加载（LOADED 校验失败）：createInstance 要求定义已 loadPlugin 且仍为 LOADED。
     */
    ErrorCode ERR_PLUGIN_DEFINITION_NOT_LOADED =
            define("nop.err.plugin.definition-not-loaded", "插件定义未加载:{pluginId}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID);

    /**
     * 同 key 重复 createInstance（instanceKey 同定义下唯一，禁止静默覆盖）。
     */
    ErrorCode ERR_PLUGIN_INSTANCE_EXISTS =
            define("nop.err.plugin.instance-exists", "插件实例已存在:{pluginId},{instanceKey}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_INSTANCE_KEY);

    /**
     * 指定 key 的实例不存在（destroyInstance/getInstance 找不到）。
     */
    ErrorCode ERR_PLUGIN_INSTANCE_NOT_FOUND =
            define("nop.err.plugin.instance-not-found", "插件实例不存在:{pluginId},{instanceKey}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_INSTANCE_KEY);

    /**
     * unload 守卫：定义仍有实例时禁止 unload（须先 destroy 全部实例）。
     */
    ErrorCode ERR_PLUGIN_INSTANCES_NOT_EMPTY =
            define("nop.err.plugin.instances-not-empty", "插件仍有实例，禁止卸载:{pluginId},{instanceKeys}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_INSTANCE_KEYS);

    /**
     * 实例化路径未就绪（uber jar 轨 plugin.json 定义无 plugin.xdef/activator 载体，为 W4/W7 successor 项）。
     */
    ErrorCode ERR_PLUGIN_INSTANCE_NOT_SUPPORTED =
            define("nop.err.plugin.instance-not-supported",
                    "插件不支持实例化:{pluginId},{instanceKey}（uber jar 轨实例化路径为 successor 项）",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_INSTANCE_KEY);

    /**
     * 实例激活失败（实例化/activator 抛错；实例回退 DEACTIVATED 并记录错误）。
     */
    ErrorCode ERR_PLUGIN_ACTIVATION_FAILED =
            define("nop.err.plugin.activation-failed", "插件实例激活失败:{pluginId},{instanceKey}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_INSTANCE_KEY);

    /**
     * 定义声明的 activator bean 在实例容器中不存在。
     */
    ErrorCode ERR_PLUGIN_ACTIVATOR_NOT_FOUND =
            define("nop.err.plugin.activator-not-found", "插件激活器 bean 不存在:{activator},{pluginId},{instanceKey}",
                    ARG_ACTIVATOR, io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_INSTANCE_KEY);

    /**
     * 定义声明的 activator bean 未实现 IPluginActivator。
     */
    ErrorCode ERR_PLUGIN_INVALID_ACTIVATOR =
            define("nop.err.plugin.invalid-activator", "插件激活器 bean 未实现 IPluginActivator:{activator}",
                    ARG_ACTIVATOR);

    /**
     * 多候选且无唯一 primary（getService/getServices 解析失败，不静默返回集合）。
     */
    ErrorCode ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES =
            define("nop.err.plugin.multiple-service-candidates", "服务类型存在多个候选且无唯一 primary:{beanType}",
                    ARG_BEAN_TYPE);
}