/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.UnknownResource;

import java.io.File;
import java.sql.Timestamp;
import java.util.function.Function;

public class SimpleBakResourceHistory implements IResourceHistory {
    public static SimpleBakResourceHistory INSTANCE = new SimpleBakResourceHistory();

    @Override
    public boolean changeResource(IResource resource, Function<IResource, Boolean> task) {
        IResource bakResource = getBakResource(resource);
        boolean saved = bakResource.length() <= 0;
        if (saved) {
            resource.saveToResource(bakResource);
        }

        boolean b = Boolean.TRUE.equals(task.apply(resource));
        if (!b && saved) {
            bakResource.delete();
        }
        return b;
    }

    @Override
    public void rollback(IResource resource, Timestamp fileTime) {
        IResource bakResource = getBakResource(resource);
        if (bakResource.exists()) {
            bakResource.saveToResource(resource);
            bakResource.delete();
        }
    }

    private IResource getBakResource(IResource resource) {
        File file = resource.toFile();
        if (file == null)
            return new UnknownResource(resource.getPath());
        return new FileResource(resource.getPath() + ResourceConstants.FILE_POSTFIX_BAK,
                new File(file.getParent(), file.getName() + ResourceConstants.FILE_POSTFIX_BAK));
    }
}