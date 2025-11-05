package io.nop.plugin.manager.resolver;

import io.nop.api.core.beans.ArtifactCoordinates;

import java.net.URL;
import java.util.List;

public interface IPluginResourceResolver {
    List<URL> resolvePluginResource(ArtifactCoordinates pluginId);
}