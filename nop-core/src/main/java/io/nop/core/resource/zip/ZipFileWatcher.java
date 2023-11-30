/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.zip;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IRateLimitExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.RateLimitExecutorImpl;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.watch.IFileWatchListener;
import io.nop.core.resource.watch.NioFileWatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ZipFileWatcher {
    static final Logger LOG = LoggerFactory.getLogger(ZipToolHelper.class);

    /**
     * 调试辅助函数。
     * <p>
     * 监控dir目录下的文件，如果发生变化，则重新生成zip文件
     *
     * @param dir     被监控的目录
     * @param zipFile 由dir目录下文件压缩得到的zip文件
     */
    public static void watchDirAndAutoZip(NioFileWatchService watchService, File dir, File zipFile) {
        IScheduledExecutor scheduledExecutor = GlobalExecutors.globalTimer();
        IRateLimitExecutor executor = new RateLimitExecutorImpl(scheduledExecutor);

        watchService.watch(dir.toPath(), path -> true, true, new IFileWatchListener() {
            @Override
            public void onFileChange(Path root, Path path) {
                LOG.info("nop.zip-tool.on-file-change:{}", path);

                executor.debounce("refresh", 100, () -> {
                    ResourceHelper.zipDir(new FileResource(dir), new FileResource(zipFile), null);
                });
            }

            @Override
            public void onFileCreate(Path root, Path path) {
                LOG.info("nop.zip-tool.on-file-create:{}", path);
            }

            @Override
            public void onFileDelete(Path root, Path path) {
                LOG.info("nop.zip-tool.on-file-delete:{}", path);
            }
        });
    }

    public static void main(String[] inputArgs) {
        String[] args =  new String[]{"c:/watch/a.xpt.xlsx"};
        NioFileWatchService watchService = new NioFileWatchService();
        watchService.start();

        File zipFile = new File(args[0]);
        File dir = new File(zipFile.getParentFile(), StringHelper.removeFileExt(zipFile.getName()));
        watchDirAndAutoZip(watchService, dir, zipFile);
        try {
            System.in.read();
        } catch (IOException e) {
            System.out.println("exit");
        }
        watchService.stop();
    }
}
