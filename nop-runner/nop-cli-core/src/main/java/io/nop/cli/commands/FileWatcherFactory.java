/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.resource.watch.FileWatcher;
import io.nop.core.resource.watch.NioFileWatchService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

@Singleton
public class FileWatcherFactory {
    private NioFileWatchService watchService = new NioFileWatchService();

    @PostConstruct
    public void init() {
        watchService.start();
    }

    @PreDestroy
    public void destroy() {
        watchService.stop();
    }

    public FileWatcher newFileWatcher() {
        return new FileWatcher(watchService, GlobalExecutors.globalTimer());
    }
}