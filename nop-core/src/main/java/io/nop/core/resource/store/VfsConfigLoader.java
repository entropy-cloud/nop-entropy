/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_VFS_CONFIG_FILE;
import static io.nop.core.CoreConfigs.CFG_VFS_DELTA_RESOURCE_STORE_BUILDER_CLASS;

@GlobalInstance
public class VfsConfigLoader {
    static final Logger LOG = LoggerFactory.getLogger(VfsConfigLoader.class);

    private static VfsConfig _default;

    public static VfsConfig loadDefault() {
        if (_default == null) {
            _default = VfsConfigLoader.loadJsonConfig();
        }
        return _default;
    }

    public static void registerDefault(VfsConfig config) {
        _default = config;
    }

    private static IResource getConfigResource() {
        String file = CFG_RESOURCE_VFS_CONFIG_FILE.get();
        if (StringHelper.isEmpty(file))
            return null;
        if (ResourceHelper.startsWithNamespace(file, ResourceConstants.CLASSPATH_NS)) {
            return new ClassPathResource(file);
        }
        FileResource resource = new FileResource(new File(file));
        if (resource.exists()) {
            LOG.info("nop.vfs.use-vfs-config-file:file={}", resource);
            return resource;
        } else {
            LOG.warn("nop.vfs.vfs-config-file-not-exists:file={}", resource);
        }
        return null;
    }

    private static VfsConfig loadJsonConfig() {
        IResource resource = getConfigResource();
        VfsConfig config;
        if (resource == null) {
            config = new VfsConfig();
        } else {
            config = (VfsConfig) ResourceHelper.readJson(resource, VfsConfig.class);
        }
        List<String> layerIds = ConvertHelper.toCsvList(CoreConfigs.CFG_VFS_DELTA_LAYER_IDS.get(), NopException::new);
        if (layerIds != null && !layerIds.isEmpty()) {
            config.setDeltaLayerIds(layerIds);
        }

        String builderClass = CFG_VFS_DELTA_RESOURCE_STORE_BUILDER_CLASS.get();
        if (!StringHelper.isEmpty(builderClass)) {
            config.setStoreBuilderClass(builderClass);
        }
        return config;
    }
}
