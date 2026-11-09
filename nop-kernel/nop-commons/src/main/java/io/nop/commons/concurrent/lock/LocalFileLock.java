/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.IoHelper;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static io.nop.commons.CommonErrors.*;

public class LocalFileLock implements Lock {

    private final File lockFile;
    private final int pollIntervalMs;
    private final int timeoutMs;

    /**
     * 1.7-C1-011（plan 2547 裁定 H）：锁成功会话。原先 (lock, randomAccessFile, channel) 三个
     * 实例字段无同步（违反 Lock 接口的线程安全契约），且 {@code tryLock(0)} 在 lock==null 的 break
     * 路径上不释放已打开的句柄（泄漏 → 其他进程永远无法获锁）。
     * 收敛为单个 volatile 会话对象：成功路径整体发布，unlock 只关闭该会话，失败/超时/异常路径统一释放。
     */
    private volatile Handle held;

    /** 锁成功会话：一个 RandomAccessFile/FileChannel/FileLock 三件套的生命周期载体。 */
    private static final class Handle {
        final FileLock fileLock;
        final FileChannel channel;
        final RandomAccessFile file;

        Handle(FileLock fileLock, FileChannel channel, RandomAccessFile file) {
            this.fileLock = fileLock;
            this.channel = channel;
            this.file = file;
        }
    }

    public LocalFileLock(File lockFile, int pollIntervalMs, int timeoutMs) {
        this.lockFile = lockFile;
        this.pollIntervalMs = pollIntervalMs;
        this.timeoutMs = timeoutMs;
    }

    private boolean tryLock(long timeoutMs, boolean interruptable) throws InterruptedException {
        File lockFileDirectory = lockFile.getParentFile();
        if (!lockFileDirectory.mkdirs() && (!lockFileDirectory.exists() || !lockFileDirectory.isDirectory())) {
            throw new NopException(ERR_FILE_ACQUIRE_LOCK_FAIL).param(ARG_PATH, lockFile.getAbsolutePath());
        }

        long expireNanos = CoreMetrics.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        RandomAccessFile file = null;
        FileChannel channel = null;
        FileLock fileLock = null;
        try {
            do {
                // 1.7-C1-011：每次轮询先释放上一轮未成功的句柄，避免轮询间累积打开的文件
                releaseHandles(fileLock, channel, file);
                fileLock = null;
                channel = null;
                file = null;

                file = new RandomAccessFile(lockFile, "rw");
                channel = file.getChannel();
                fileLock = channel.tryLock();

                if (timeoutMs <= 0)
                    break;

                if (fileLock == null) {
                    try {
                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException e) { //NOSONAR
                        if (interruptable)
                            throw e;
                    }
                }
            } while (fileLock == null && !CoreMetrics.isExpiredNanos(expireNanos));

            if (fileLock != null) {
                // 成功：整体发布会话
                this.held = new Handle(fileLock, channel, file);
                return true;
            }

            // 失败（含 tryLock(0) 的 break 路径）：必须释放本轮已打开的句柄，否则泄漏
            releaseHandles(fileLock, channel, file);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            releaseHandles(fileLock, channel, file);
            throw e;
        } catch (Exception e) {
            // 异常路径上关闭已打开的文件句柄，避免泄漏导致其他进程永远无法获锁
            releaseHandles(fileLock, channel, file);
            throw new NopException(ERR_FILE_ACQUIRE_LOCK_FAIL, e).param(ARG_WAIT_TIME, timeoutMs).param(ARG_PATH,
                    lockFile.getAbsolutePath());
        }
    }

    @Override
    public void lock() {
        try {
            boolean result = tryLock(timeoutMs, false);
            if (!result)
                throw new NopException(ERR_FILE_ACQUIRE_LOCK_TIMEOUT).param(ARG_PATH, lockFile.getAbsolutePath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopException(ERR_FILE_ACQUIRE_LOCK_FAIL, e).param(ARG_PATH, lockFile.getAbsolutePath());
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        boolean result = tryLock(timeoutMs, true);
        if (!result)
            throw new NopException(ERR_FILE_ACQUIRE_LOCK_TIMEOUT).param(ARG_PATH, lockFile.getAbsolutePath());
    }

    @Override
    public boolean tryLock() {
        try {
            return tryLock(0, false);
        } catch (InterruptedException e) { //NOSONAR
            return false;
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(unit.toMillis(time), true);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("newCondition");
    }

    public void unlock() {
        // 1.7-C1-011：只关闭「锁成功会话」；未持锁时为空操作
        Handle handle = this.held;
        if (handle == null)
            return;
        this.held = null;
        releaseHandles(handle.fileLock, handle.channel, handle.file);
    }

    private static void releaseHandles(FileLock fileLock, FileChannel channel, RandomAccessFile file) {
        IoHelper.safeCloseObject(fileLock);
        IoHelper.safeCloseObject(channel);
        IoHelper.safeCloseObject(file);
    }
}
