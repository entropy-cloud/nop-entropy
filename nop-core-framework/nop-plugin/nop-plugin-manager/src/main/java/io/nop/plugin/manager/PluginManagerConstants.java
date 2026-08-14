package io.nop.plugin.manager;

public interface PluginManagerConstants {
    String VAR_PLUGIN_GROUP_ID = "pluginGroupId";

    String VAR_PLUGIN_ARTIFACT_ID = "pluginArtifactId";

    String VAR_PLUGIN_VERSION = "pluginVersion";

    /**
     * start/stop 兼容语义（§7.1）使用的默认实例 key（start = load + createInstance(默认 key)）。
     */
    String DEFAULT_INSTANCE_KEY = "default";


}
