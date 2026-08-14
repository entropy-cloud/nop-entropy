package io.nop.plugin.api;

public interface NopPluginConstants {
    String BEAN_NOP_PLUGIN_COMMAND_PREFIX = "nopPluginCommand_";

    String PLUGIN_CONFIG_FILE = "/nop/plugin.json";

    String PLUGIN_BEANS_FILE = "/nop/plugin.beans.xml";

    /**
     * plugin 定义文件（plugin.xdef 描述的 *.plugin.xml），aware 插件 load() 时从 VFS 约定路径读取。
     */
    String PLUGIN_DEFINITION_FILE = "/nop/plugin.plugin.xml";

    /**
     * plugin 定义结构层 schema（动态模型模式，见 plugin.xdef 文件头注释）。
     */
    String PLUGIN_XDEF_PATH = "/nop/schema/plugin/plugin.xdef";
}
