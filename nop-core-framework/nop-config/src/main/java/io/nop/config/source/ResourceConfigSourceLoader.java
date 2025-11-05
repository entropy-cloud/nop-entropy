/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.ConfigConstants;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ResourceConfigSourceLoader implements IConfigSourceLoader {
   // private static final Logger LOG = LoggerFactory.getLogger(ResourceConfigSourceLoader.class);

    private final IResource resource;

    public ResourceConfigSourceLoader(IResource resource) {
        this.resource = resource;
    }

    @Override
    public IConfigSource loadConfigSource(IConfigSource baseConfig) {
        if (!resource.exists())
            return new StaticConfigSource(resource.getPath(), Collections.emptyMap());

        Map<String, ValueWithLocation> vars;
        if (resource.getName().endsWith(ConfigConstants.FILE_POSTFIX_PROPERTIES)) {
            vars = loadFromProperties();
        } else {
            vars = loadFromJson();
        }
        return new StaticConfigSource(resource.getPath(), vars);
    }

    private Map<String, ValueWithLocation> loadFromProperties() {
        Properties props = ResourceHelper.readProperties(resource);
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());
        return ConfigSourceHelper.buildConfigValues(loc, props);
    }

    private Map<String, ValueWithLocation> loadFromJson() {
        JsonParseOptions options = new JsonParseOptions();
        if (resource.getName().endsWith(ConfigConstants.FILE_POSTFIX_YAML)
                || resource.getName().endsWith(ConfigConstants.FILE_POSTFIX_YML)) {
            options.setYaml(true);
        }
        options.setKeepLocation(true);
        JObject obj = (JObject) JsonTool.instance().parseFromResource(resource, options);
        if (obj == null)
            return new HashMap<>(0);
        return ConfigSourceHelper.buildConfigValues(obj.getLocation(), obj);
    }
}
