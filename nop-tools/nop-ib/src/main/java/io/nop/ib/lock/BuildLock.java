package io.nop.ib.lock;

import io.nop.ib.NopIbException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

/**
 * Exclusive build lock on {@code .mvn/build.lock} — the same file and the same
 * FileChannel semantics as mvnq's {@code MvnqLock} helper (IB-D9/IB-G5). Held
 * for product writes and test runs; never for read-only commands. The lock file
 * is never deleted (deleting it would fork the inode).
 */
public final class BuildLock implements AutoCloseable {
    public static final int EXIT_LOCK_TIMEOUT = 75;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3600);

    private final Path lockFile;
    private FileChannel channel;
    private FileLock lock;

    private BuildLock(Path lockFile) {
        this.lockFile = lockFile;
    }

    /**
     * Acquires the exclusive lock, waiting up to {@code timeout}. Returns null on
     * timeout (caller maps to exit code 75, aligned with mvnq) — timeout is an
     * expected outcome, not an exception.
     */
    public static BuildLock acquire(Path repoRoot, Duration timeout) {
        Path lockFile = repoRoot.resolve(".mvn").resolve("build.lock");
        try {
            Files.createDirectories(lockFile.getParent());
            FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (true) {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    BuildLock holder = new BuildLock(lockFile);
                    holder.channel = channel;
                    holder.lock = lock;
                    String info = "pid=" + ProcessHandle.current().pid()
                            + " owner=nop-ib started=" + Instant.now() + "\n";
                    channel.truncate(0);
                    channel.write(StandardCharsets.UTF_8.encode(info), 0);
                    return holder;
                }
                if (System.currentTimeMillis() >= deadline) {
                    channel.close();
                    return null;
                }
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            throw new NopIbException("cannot open build lock " + lockFile, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopIbException("interrupted while waiting for build lock");
        }
    }

    public static BuildLock acquire(Path repoRoot) {
        return acquire(repoRoot, DEFAULT_TIMEOUT);
    }

    @Override
    public void close() {
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException ignored) {
                // channel teardown below still releases the OS lock
            }
        }
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                throw new NopIbException("failed to close build lock channel", e);
            }
        }
    }
}
