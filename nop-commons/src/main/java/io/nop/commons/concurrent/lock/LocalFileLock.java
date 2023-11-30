/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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

    private FileLock lock;
    private RandomAccessFile randomAccessFile = null;
    private FileChannel channel = null;

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

        try {
            do {
                randomAccessFile = new RandomAccessFile(lockFile, "rw");
                channel = randomAccessFile.getChannel();
                lock = channel.tryLock();

                if(timeoutMs <= 0)
                    break;

                if (lock == null) {
                    IoHelper.safeCloseObject(channel);
                    IoHelper.safeCloseObject(randomAccessFile);
                    try {
                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException e) { //NOSONAR
                        if (interruptable)
                            throw e;
                    }
                }
            } while (lock == null && !CoreMetrics.isExpiredNanos(expireNanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_FILE_ACQUIRE_LOCK_FAIL, e).param(ARG_WAIT_TIME, timeoutMs).param(ARG_PATH,
                    lockFile.getAbsolutePath());
        }

        return false;
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
        IoHelper.safeCloseObject(lock);
        lock = null;

        IoHelper.safeCloseObject(channel);
        channel = null;
        IoHelper.safeCloseObject(randomAccessFile);
        randomAccessFile = null;
    }
}
