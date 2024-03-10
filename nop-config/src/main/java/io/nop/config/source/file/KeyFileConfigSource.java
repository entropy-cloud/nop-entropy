/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source.file;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.objects.ValueWithLocation;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyFileConfigSource extends AbstractFileConfigSource {

    public KeyFileConfigSource(Collection<String> paths, long refreshInterval) {
        super(paths, refreshInterval);
    }

    public String getName() {
        return "key-files";
    }

    @Override
    protected Map<String, ValueWithLocation> loadConfigFromPath(List<Path> paths) {
        Map<String, ValueWithLocation> ret = new HashMap<>();
        for (Path path : paths) {
            String name = path.getFileName().toString();
            File file = path.toFile();
            String text = FileHelper.readText(file, null);
            SourceLocation loc = SourceLocation.fromPath(FileHelper.getFileUrl(file));
            ValueWithLocation old = ret.put(name, ValueWithLocation.of(loc, text));
            if (old != null) {
                LOG.warn("nop.config.duplicate-key-file:name={},loc={},oldLoc={}", name, loc, old.getLocation());
            }
        }
        return ret;
    }
}
