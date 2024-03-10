/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source.file;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.source.ConfigSourceHelper;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropsFileConfigSource extends AbstractFileConfigSource {
    public PropsFileConfigSource(Collection<String> paths, long refreshInterval) {
        super(paths, refreshInterval);
    }

    @Override
    public String getName() {
        return "props-files";
    }

    @Override
    protected Map<String, ValueWithLocation> loadConfigFromPath(List<Path> paths) {
        Map<String, ValueWithLocation> ret = new HashMap<>();
        for (Path path : paths) {
            File file = path.toFile();
            IResource resource = new FileResource(file);
            Map<String, Object> props = JsonTool.parseBeanFromResource(resource, JObject.class);
            SourceLocation loc = SourceLocation.fromPath(resource.getPath());

            Map<String, ValueWithLocation> values = ConfigSourceHelper.buildConfigValues(loc, props);
            ret.putAll(values);
        }
        return ret;
    }
}