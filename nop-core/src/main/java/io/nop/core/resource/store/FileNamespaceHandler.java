/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.env.PlatformEnv;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.List;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_ALLOWED_FILE_PATH_PATTERN;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_ALLOW_ACCESS_PATH;

public class FileNamespaceHandler implements IResourceNamespaceHandler {
    public static final FileNamespaceHandler INSTANCE = new FileNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.FILE_NS;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        return buildFileResource(vPath, true);
    }

    public static FileResource buildFileResource(String vPath, boolean checkAllowed) {
        String path = ResourceHelper.removeNamespace(vPath, ResourceConstants.FILE_NS);
        if (path.startsWith("///"))
            path = path.substring(2);

        if (!PlatformEnv.isWindows()) {
            ResourceHelper.checkNormalVirtualPath(path);
        } else {
            checkWindowsPath(path);
        }

        if (checkAllowed) {
            String pattern = CFG_RESOURCE_ALLOWED_FILE_PATH_PATTERN.get();
            if (!StringHelper.isEmpty(pattern)) {
                if (!allowPath(path, pattern))
                    throw new NopException(ERR_RESOURCE_NOT_ALLOW_ACCESS_PATH).param(ARG_RESOURCE_PATH, vPath);
            }
        }

        // file:///test 规范化为 file:/test
        vPath = ResourceHelper.buildNamespacePath(ResourceConstants.FILE_NS, path);

        return new FileResource(vPath, new File(path));
    }

    private static void checkWindowsPath(String path) {
        if (!path.startsWith("/") || path.length() < 4 || path.charAt(2) != ':') {
            throw new NopException(ERR_RESOURCE_INVALID_PATH).param(ARG_RESOURCE_PATH, path);
        }
        // /c:/
        if (!ResourceHelper.isNormalVirtualPath(path.substring(3))) {
            throw new NopException(ERR_RESOURCE_INVALID_PATH).param(ARG_RESOURCE_PATH, path);
        }
    }

    private static boolean allowPath(String path, String pattern) {
        List<String> list = StringHelper.split(pattern, '|');
        for (String p : list) {
            if (StringHelper.matchPath(path, p))
                return true;
        }
        return false;
    }
}
