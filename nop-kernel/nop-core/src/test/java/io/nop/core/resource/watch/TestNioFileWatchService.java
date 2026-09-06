/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.watch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNioFileWatchService {

    @TempDir
    File tempDir;

    private NioFileWatchService startService() {
        NioFileWatchService service = new NioFileWatchService();
        service.start();
        return service;
    }

    @Test
    public void testWatchFileCreate() throws Exception {
        NioFileWatchService service = startService();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            service.watch(tempDir.toPath(), null, false, new IFileWatchListener() {
                @Override
                public void onFileChange(Path root, Path file) {
                }

                @Override
                public void onFileCreate(Path root, Path file) {
                    latch.countDown();
                }

                @Override
                public void onFileDelete(Path root, Path file) {
                }
            });

            Files.createFile(tempDir.toPath().resolve("a.txt"));
            assertTrue(latch.await(10, TimeUnit.SECONDS), "onFileCreate should be invoked for new file");
        } finally {
            service.stop();
        }
    }

    /**
     * watcher线程在ENTRY_CREATE时递归注册新子目录，单次register失败不应中断整个watcher循环
     */
    @Test
    public void testRegisterFailureDoesNotKillWatcher() throws Exception {
        AtomicInteger registerCount = new AtomicInteger();
        CountDownLatch subdirRegisterReached = new CountDownLatch(1);
        NioFileWatchService service = new NioFileWatchService() {
            @Override
            FileWatchEntry register(Path root, Path path, Predicate<Path> filter, boolean recursive,
                                    IFileWatchListener listener) throws IOException {
                // 第1次是watch()对根目录的注册；第2次是watcher线程对新子目录的注册，让它失败
                if (registerCount.incrementAndGet() == 2) {
                    subdirRegisterReached.countDown();
                    throw new IOException("mock register fail");
                }
                return super.register(root, path, filter, recursive, listener);
            }
        };
        service.start();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            service.watch(tempDir.toPath(), null, true, new IFileWatchListener() {
                @Override
                public void onFileChange(Path root, Path file) {
                }

                @Override
                public void onFileCreate(Path root, Path file) {
                    if (file.getFileName().toString().equals("b.txt"))
                        latch.countDown();
                }

                @Override
                public void onFileDelete(Path root, Path file) {
                }
            });

            // 触发watcher线程的递归register失败，并确认watcher线程已处理到该子目录事件
            Files.createDirectory(tempDir.toPath().resolve("sub"));
            assertTrue(subdirRegisterReached.await(10, TimeUnit.SECONDS),
                    "watcher thread should have processed the subdir create event");

            // watcher循环必须仍然存活并能继续处理根目录上的事件
            Files.createFile(tempDir.toPath().resolve("b.txt"));
            assertTrue(latch.await(10, TimeUnit.SECONDS),
                    "watcher loop should survive register failure and deliver subsequent events");
        } finally {
            service.stop();
        }
    }
}
