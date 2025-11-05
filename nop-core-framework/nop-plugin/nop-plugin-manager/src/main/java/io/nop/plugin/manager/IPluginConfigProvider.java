package io.nop.plugin.manager;

import io.nop.api.core.beans.ArtifactCoordinates;

import java.util.Map;

public interface IPluginConfigProvider {
    Map<String,Object> getPluginConfig(ArtifactCoordinates pluginId);
}
