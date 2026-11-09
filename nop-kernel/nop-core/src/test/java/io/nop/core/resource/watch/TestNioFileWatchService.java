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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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


    /** pin6006：unwatch 递归摘除后父的 subEntries 不再单调累积（C6-006）。 */
    @Test
    public void pin6006_unwatchRemovesFromParentSubEntries(@TempDir File subDir) throws Exception {
        NioFileWatchService service = startService();
        try {
            Files.createDirectory(subDir.toPath().resolve("child"));
            FileWatchEntry[] holder = new FileWatchEntry[1];
            Runnable cleanup = service.watch(subDir.toPath(), null, true, new IFileWatchListener() {
                @Override
                public void onFileChange(Path root, Path file) {
                }

                @Override
                public void onFileCreate(Path root, Path file) {
                }

                @Override
                public void onFileDelete(Path root, Path file) {
                }
            });
            // watch 是包私有 map：经反射拿根 entry（测试同包可直接用包私有 API）
            FileWatchEntry rootEntry = null;
            for (java.util.Map.Entry<java.nio.file.WatchKey, FileWatchEntry> e : serviceEntryMap(service).entrySet()) {
                if (e.getValue().getPath().equals(subDir.toPath().toAbsolutePath())) {
                    rootEntry = e.getValue();
                    break;
                }
            }
            assertNotNull(rootEntry, "根 entry 应已注册");
            int before = rootEntry.getSubEntries().size();
            assertTrue(before >= 1, "递归 watch 应产生 subEntry");

            cleanup.run();
            assertEquals(0, rootEntry.getSubEntries().size(),
                    "缺陷态：unwatch 后父的 subEntries 单调累积");
        } finally {
            service.stop();
        }
    }

    private java.util.Map<java.nio.file.WatchKey, FileWatchEntry> serviceEntryMap(NioFileWatchService service)
            throws Exception {
        java.lang.reflect.Field f = NioFileWatchService.class.getDeclaredField("watchKeyMap");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<java.nio.file.WatchKey, FileWatchEntry> map =
                (java.util.Map<java.nio.file.WatchKey, FileWatchEntry>) f.get(service);
        return map;
    }

    /** pin6006b：同路径重复 watch 复用同一 entry、返回可用的幂等句柄（C6-006）。 */
    @Test
    public void pin6006b_duplicateWatchReusesEntry() throws Exception {
        NioFileWatchService service = startService();
        try {
            Runnable h1 = service.watch(tempDir.toPath(), null, false, null);
            Runnable h2 = service.watch(tempDir.toPath(), null, false, null);
            assertEquals(1, serviceEntryMap(service).size(),
                    "缺陷态：重复 watch 注册第二个 WatchKey → 注销交叉误伤");
            h1.run();
            assertEquals(0, serviceEntryMap(service).size(), "旧句柄仍可注销整个 entry");
            h2.run(); // 幂等：已注销，不应抛错
        } finally {
            service.stop();
        }
    }

    /** pin6007：watcher 线程 daemon 化（C6-007，观测路径=线程名前缀扫描）。 */
    @Test
    public void pin6007_watcherThreadIsDaemon() throws Exception {
        NioFileWatchService service = startService();
        try {
            Thread watcher = findWatcherThread();
            assertNotNull(watcher, "启动后应存在 nio-file-watch-service 线程");
            assertTrue(watcher.isDaemon(), "缺陷态：watcher 线程非 daemon → 阻止 JVM 退出");
        } finally {
            service.stop();
        }
    }

    /** pin6007b：doStop 有界返回（<5s）且 watcher 线程消失（C6-007）。 */
    @Test
    public void pin6007b_stopIsBoundedAndThreadGone() throws Exception {
        NioFileWatchService service = startService();
        assertNotNull(findWatcherThread());
        long begin = System.currentTimeMillis();
        service.stop();
        long elapsed = System.currentTimeMillis() - begin;
        assertTrue(elapsed < 5000, "doStop 应在 5s 内返回，实际 " + elapsed + "ms");
        long deadline = System.currentTimeMillis() + 3000;
        while (findWatcherThread() != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(findWatcherThread() == null, "stop 后 watcher 线程应终止");
    }

    private Thread findWatcherThread() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("nio-file-watch-service"))
                return t;
        }
        return null;
    }
}
