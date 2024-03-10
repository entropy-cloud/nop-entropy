/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.watch;

import java.nio.file.Path;

public class FileWatchListener implements IFileWatchListener {

    protected String getRelativePath(Path root, Path path) {
        return root.relativize(path).toString();
    }

    @Override
    public void onFileChange(Path root, Path path) {

    }

    @Override
    public void onFileCreate(Path root, Path path) {

    }

    @Override
    public void onFileDelete(Path root, Path path) {

    }
}