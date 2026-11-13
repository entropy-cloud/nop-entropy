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
    private static volatile IVirtualFileSystem _instance = null;

    public static boolean isInitialized() {
        return _instance != null;
    }

    public static IVirtualFileSystem instance() {
        IVirtualFileSystem fs = _instance;
        if (fs == null)
            throw new NopException(ERR_RESOURCE_VIRTUAL_FILE_SYSTEM_NOT_INITIALIZED);
        return fs;
    }

    /**
     * 1.7-C1-009（plan 2547 裁定 G；draft review R1 M-6 勘正）：原实现「先销毁旧实例再发布新实例」
     * 存在确定性错序——销毁与发布之间若其他线程读 {@code _instance}，会读到**正在被销毁**的旧实例。
     * 改为先发布新的（volatile），再销毁旧的。
     */
    public static void registerInstance(IVirtualFileSystem instance) {
        IVirtualFileSystem old = _instance;
        _instance = instance;
        DestroyHelper.safeDestroy(old);
    }

    public static void unregisterInstance(IVirtualFileSystem instance) {
        if (_instance == instance) {
            _instance = null;
        }
    }
}