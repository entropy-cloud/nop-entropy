package io.nop.plugin.manager;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface PluginManagerErrors {
    String ARG_PARAM_NAME = "paramName";
    String ARG_FILE_NAME = "fileName";
    String ARG_BEAN_TYPE = "beanType";
    String ARG_BEAN_ID = "beanId";
    String ARG_ACTIVATOR = "activator";
    String ARG_SPEC_ATTR = "specAttr";
    String ARG_EXPECTED_HASH = "expectedHash";
    String ARG_ACTUAL_HASH = "actualHash";
    String ARG_URL = "url";

    ErrorCode ERR_PLUGIN_MISSING_CONFIG_FILE =
            define("nop.err.plugin.missing-config-file", "插件中缺少nop/plugin.json文件");

    ErrorCode ERR_PLUGIN_INVALID_PARAM_NAME =
            define("nop.err.plugin.invalid-param-name", "非法的参数名{paramName}", ARG_PARAM_NAME);

    /**
     * Maven 坐标分段未通过白名单校验（防路径穿越，HttpPluginResourceResolver 纵深防御）。
     */
    ErrorCode ERR_PLUGIN_INVALID_COORDINATE_SEGMENT =
            define("nop.err.plugin.invalid-coordinate-segment",
                    "非法的插件坐标分段:{paramName},{pluginId}",
                    ARG_PARAM_NAME, io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID);

    ErrorCode ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL =
            define("nop.err.plugin.download-rename-file-fail", "下载插件包后重命名文件失败:{fileName}");

    /**
     * 下载或缓存重验发现 jar 与预期 SHA256 不匹配（W7，设计 05 §四修订注解/§五）：
     * 篡改/损坏 fail-fast（删临时文件 + 抛异常），绝不使用未通过校验的 jar。
     */
    ErrorCode ERR_PLUGIN_SHA256_MISMATCH =
            define("nop.err.plugin.sha256-mismatch",
                    "插件包 SHA256 校验失败:{pluginId},expected={expectedHash},actual={actualHash}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_EXPECTED_HASH, ARG_ACTUAL_HASH);

    /**
     * 无任何 hash 来源（响应 header / .sha256 文件 / 配置预期 hash map）→ 显式失败（W7）：
     * Javadoc 已声明"完整性有SHA256校验码保证"，无校验下载属静默降级，禁止。
     */
    ErrorCode ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE =
            define("nop.err.plugin.checksum-not-available",
                    "插件包无可用 SHA256 校验源:{pluginId},{url}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_URL);

    ErrorCode ERR_PLUGIN_NO_PLUGIN_CLASS_NAME =
            define("nop.err.plugin.no-plugin-class-name", "插件配置文件中缺少pluginClassName属性");

    /**
     * 定义未加载（LOADED 校验失败）：activatePlugin/deactivatePlugin/reloadPlugin 要求
     * 定义已 loadPlugin 且未 unload。
     */
    ErrorCode ERR_PLUGIN_DEFINITION_NOT_LOADED =
            define("nop.err.plugin.definition-not-loaded", "插件定义未加载:{pluginId}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID);

    /**
     * 插件激活失败（容器构建/activator 抛错；插件置 FAILED 并记录错误，可重试激活）。
     */
    ErrorCode ERR_PLUGIN_ACTIVATION_FAILED =
            define("nop.err.plugin.activation-failed", "插件激活失败:{pluginId}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID);

    /**
     * 定义声明的 activator bean 在插件容器中不存在。
     */
    ErrorCode ERR_PLUGIN_ACTIVATOR_NOT_FOUND =
            define("nop.err.plugin.activator-not-found", "插件激活器 bean 不存在:{activator},{pluginId}",
                    ARG_ACTIVATOR, io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID);

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

    /**
     * getService/getServices 仅支持接口类型（生命周期绑定代理只能生成接口代理；
     * 具体类传入明确抛错，禁止静默返回裸引用/裸集合）。
     */
    ErrorCode ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE =
            define("nop.err.plugin.service-proxy-only-interface",
                    "服务类型必须为接口（生命周期代理仅支持接口）:{beanType}",
                    ARG_BEAN_TYPE);

    /**
     * 服务代理重激活后按 bean id 重新解析不到候选（容器 bean 定义变更的防御性失败，不静默返回 null）。
     */
    ErrorCode ERR_PLUGIN_SERVICE_CANDIDATE_NOT_FOUND =
            define("nop.err.plugin.service-candidate-not-found",
                    "服务候选不存在:{beanType},{beanId}",
                    ARG_BEAN_TYPE, ARG_BEAN_ID);

    /**
     * coeffect spec 格式非法（load 解析时构造即校验）：if-property 的 propName 为空等
     * （No Silent No-Op——非法格式显式失败，不静默忽略）。
     */
    ErrorCode ERR_PLUGIN_INVALID_COEFFECT_SPEC =
            define("nop.err.plugin.invalid-coeffect-spec",
                    "coeffect spec 格式非法:{pluginId},{specAttr}",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID, ARG_SPEC_ATTR);

    /**
     * reloadPlugin 对 uber jar 轨显式失败（W6）：设计 §六 HMR 面向本地/开发场景，
     * 远程 uber jar 不可编辑（与 W4 实例化裁决同构——不支持即显式失败，不静默 no-op）。
     */
    ErrorCode ERR_PLUGIN_RELOAD_NOT_SUPPORTED =
            define("nop.err.plugin.reload-not-supported",
                    "插件不支持热重载:{pluginId}（uber jar 轨 HMR 为 successor 项）",
                    io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID);
}