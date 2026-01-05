/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.DestroyHelper;

import static io.nop.core.CoreErrors.ERR_RESOURCE_VIRTUAL_FILE_SYSTEM_NOT_INITIALIZED;

@GlobalInstance
public class VirtualFileSystem {
    private static IVirtualFileSystem _INSTANCE = null;

    public static boolean isInitialized() {
        return _INSTANCE != null;
    }

    public static IVirtualFileSystem instance() {
        IVirtualFileSystem fs = _INSTANCE;
        if (fs == null)
            throw new NopException(ERR_RESOURCE_VIRTUAL_FILE_SYSTEM_NOT_INITIALIZED);
        return fs;
    }

    public static void registerInstance(IVirtualFileSystem instance) {
        DestroyHelper.safeDestroy(_INSTANCE);
        _INSTANCE = instance;
    }

    public static void unregisterInstance(IVirtualFileSystem instance) {
        if (_INSTANCE == instance) {
            _INSTANCE = null;
        }
    }
}