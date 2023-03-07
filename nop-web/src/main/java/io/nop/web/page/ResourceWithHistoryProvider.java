/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.commons.concurrent.lock.IResourceLockManager;
import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceHistory;
import io.nop.core.resource.SimpleBakResourceHistory;
import io.nop.core.resource.VirtualFileSystem;

import java.security.Timestamp;
import java.util.function.Function;

public abstract class ResourceWithHistoryProvider {

    private IResourceLockManager resourceLockManager = new LocalResourceLockManager();

    private IResourceHistory resourceHistory = SimpleBakResourceHistory.INSTANCE;

    public void setResourceLockManager(IResourceLockManager resourceLockManager) {
        this.resourceLockManager = resourceLockManager;
    }

    public void setResourceHistory(IResourceHistory resourceHistory) {
        this.resourceHistory = resourceHistory;
    }

    public void rollback(String path, Timestamp timestamp) {
        resourceLockManager.runWithLock(path, () -> {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            resourceHistory.rollback(resource, null);
        });
    }

    protected void withHistorySupport(IResource resource, Function<IResource,Boolean> task) {
        resourceLockManager.runWithLock(resource.getStdPath(), () -> {
            resourceHistory.changeResource(resource, task);
        });
    }
}
