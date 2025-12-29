/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IRateLimitExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.RateLimitExecutorImpl;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.watch.IFileWatchListener;
import io.nop.core.resource.watch.NioFileWatchService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "watch-zip", mixinStandardHelpOptions = true, 
    description = "Watch a directory for changes and automatically update the corresponding zip file")
public class CliWatchZipCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliWatchZipCommand.class);

    @CommandLine.Parameters(index = "0", description = "Directory to watch for changes")
    File watchDir;

    @CommandLine.Parameters(index = "1", description = "Target zip file to update (optional, defaults to <watchDir>.zip)", arity = "0..1")
    File zipFile;

    @CommandLine.Option(names = {"-w", "--wait"}, description = "Debounce wait interval in milliseconds (default: 100)")
    int debounceWait = 100;

    @CommandLine.Option(names = {"-r", "--recursive"}, description = "Watch subdirectories recursively (default: true)")
    boolean recursive = true;

    private NioFileWatchService watchService;

    @PostConstruct
    public void init() {
        watchService = new NioFileWatchService();
        watchService.start();
    }

    @PreDestroy
    public void destroy() {
        if (watchService != null) {
            watchService.stop();
        }
    }

    @Override
    public Integer call() {
        if (!watchDir.exists() || !watchDir.isDirectory()) {
            LOG.error("Watch directory does not exist or is not a directory: {}", watchDir);
            return 1;
        }

        // If zip file is not specified, create one based on the watch directory name
        if (zipFile == null) {
            zipFile = new File(watchDir.getParentFile(), watchDir.getName() + ".zip");
        }

        LOG.info("nop.cli.watch-zip: watching directory={}, target zip={}, debounceWait={}ms", 
                watchDir.getAbsolutePath(), zipFile.getAbsolutePath(), debounceWait);

        // Initialize the watch service
        init();

        try {
            // Start watching and auto-zipping
            watchDirAndAutoZip(watchService, watchDir, zipFile);

            LOG.info("Press Enter to stop watching...");
            System.in.read();
        } catch (IOException e) {
            LOG.error("Error during watch operation", e);
            return 1;
        } finally {
            destroy();
        }

        return 0;
    }

    /**
     * Watch directory and automatically update zip file when changes occur
     */
    private void watchDirAndAutoZip(NioFileWatchService watchService, File dir, File zipFile) {
        IScheduledExecutor scheduledExecutor = GlobalExecutors.globalTimer();
        IRateLimitExecutor executor = new RateLimitExecutorImpl(scheduledExecutor);

        watchService.watch(dir.toPath(), path -> true, recursive, new IFileWatchListener() {
            @Override
            public void onFileChange(Path root, Path path) {
                LOG.info("nop.zip-tool.on-file-change: {}", path);
                scheduleZipUpdate(executor, dir, zipFile);
            }

            @Override
            public void onFileCreate(Path root, Path path) {
                LOG.info("nop.zip-tool.on-file-create: {}", path);
                scheduleZipUpdate(executor, dir, zipFile);
            }

            @Override
            public void onFileDelete(Path root, Path path) {
                LOG.info("nop.zip-tool.on-file-delete: {}", path);
                scheduleZipUpdate(executor, dir, zipFile);
            }
        });
    }

    /**
     * Schedule zip file update with debouncing
     */
    private void scheduleZipUpdate(IRateLimitExecutor executor, File dir, File zipFile) {
        executor.debounce("refresh", debounceWait, () -> {
            try {
                LOG.info("nop.zip-tool.updating-zip: {} -> {}", dir.getAbsolutePath(), zipFile.getAbsolutePath());
                ResourceHelper.zipDir(new FileResource(dir), new FileResource(zipFile), null);
                LOG.info("nop.zip-tool.zip-updated: {}", zipFile.getAbsolutePath());
            } catch (Exception e) {
                LOG.error("nop.zip-tool.zip-update-failed: " + zipFile.getAbsolutePath(), e);
            }
        });
    }
}