/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.initialize.impl;

import io.nop.commons.util.DestroyHelper;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.DefaultVirtualFileSystem;

import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_REGISTER_VFS;

public class VirtualFileSystemInitializer implements ICoreInitializer {
    private DefaultVirtualFileSystem vfs;

    @Override
    public int order() {
        return INITIALIZER_PRIORITY_REGISTER_VFS;
    }

    @Override
    public void initialize() {
        // ConfigStarter中会初始化VFS。只有不引入nop-config模块时才会调用到这里
        if (!VirtualFileSystem.isInitialized()) {
            vfs = new DefaultVirtualFileSystem();
            VirtualFileSystem.registerInstance(vfs);
            ModuleManager.instance().discover();
        }
    }

    @Override
    public void destroy() {
        if (vfs != null) {
            VirtualFileSystem.unregisterInstance(vfs);
            DestroyHelper.safeDestroy(vfs);
            vfs = null;
            ModuleManager.instance().clear();
        }
    }
}