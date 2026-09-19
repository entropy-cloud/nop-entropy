package io.nop.rg.core.walk;

import io.nop.core.git.GitIgnoreFile;
import io.nop.core.resource.impl.FileResource;
import io.nop.rg.core.NopRgException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 并行文件遍历器（nop-rg design 决策 4：专用 ExecutorService，非 commonPool）。
 *
 * <p>契约（plan 2264 钉死）：
 * <ul>
 *   <li>输入为 {@link Path} 目录根；输出为排序后的常规文件列表（确定性）。</li>
 *   <li>{@code respectGitignore}（默认 true）：以 nop-core {@link GitIgnoreFile} 过滤（内部以
 *       {@link FileResource} 包装；VFS 初始化由调用方负责——库本体无副作用，未初始化时异常自然抛出）。</li>
 *   <li>{@code includeHidden}（默认 false）：{@code .} 前缀文件/目录跳过（.gitignore 本身即隐藏文件，
 *       默认不入结果；其规则由 GitIgnoreFile.create() 自身遍历加载，不受本开关影响）。</li>
 *   <li>被过滤掉的目录不递归（剪枝）。</li>
 * </ul>
 */
public class ParallelFileWalker {
    private static final int DEFAULT_THREADS = Runtime.getRuntime().availableProcessors();

    private final Path root;
    private final int threads;
    private final boolean respectGitignore;
    private final boolean includeHidden;

    public ParallelFileWalker(Path root, int threads, boolean respectGitignore, boolean includeHidden) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }
        this.root = root;
        this.threads = threads;
        this.respectGitignore = respectGitignore;
        this.includeHidden = includeHidden;
    }

    public static ParallelFileWalker of(Path root) {
        return new ParallelFileWalker(root, DEFAULT_THREADS, true, false);
    }

    /**
     * 遍历并返回排序后的常规文件列表。每次调用独立执行（新建线程池并在结束后关闭）。
     */
    public List<Path> walk() {
        if (!Files.isDirectory(root)) {
            throw new NopRgException("walk root is not a directory: " + root);
        }

        GitIgnoreFile ignore = loadIgnoreRules();
        ConcurrentLinkedQueue<Path> fileQueue = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Path> dirQueue = new ConcurrentLinkedQueue<>();
        dirQueue.offer(root);
        AtomicInteger pending = new AtomicInteger(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> drainLoop(dirQueue, fileQueue, pending, ignore));
            }
            awaitQuiescence(pending);
        } finally {
            pool.shutdownNow();
        }

        List<Path> result = new ArrayList<>(fileQueue);
        result.sort(Comparator.comparing(Path::toString));
        return result;
    }

    private GitIgnoreFile loadIgnoreRules() {
        if (!respectGitignore) {
            return null;
        }
        return GitIgnoreFile.create(new FileResource(root.toFile()));
    }

    private void drainLoop(ConcurrentLinkedQueue<Path> dirQueue, ConcurrentLinkedQueue<Path> fileQueue,
                           AtomicInteger pending, GitIgnoreFile ignore) {
        while (!Thread.currentThread().isInterrupted()) {
            Path dir = dirQueue.poll();
            if (dir == null) {
                if (pending.get() == 0) {
                    return;
                }
                LockSupport.parkNanos(100_000L); // 等待其他线程投递新目录
                continue;
            }
            try {
                scanDir(dir, dirQueue, fileQueue, pending, ignore);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                pending.decrementAndGet();
            }
        }
    }

    private void scanDir(Path dir, ConcurrentLinkedQueue<Path> dirQueue, ConcurrentLinkedQueue<Path> fileQueue,
                         AtomicInteger pending, GitIgnoreFile ignore) throws IOException {
        List<Path> children;
        try (var stream = Files.list(dir)) {
            children = stream.toList();
        }
        for (Path child : children) {
            String name = child.getFileName() == null ? "" : child.getFileName().toString();
            boolean hidden = name.startsWith(".");
            if (hidden && !includeHidden) {
                continue;
            }
            if (ignore != null && ignore.isIgnored(new FileResource(child.toFile()))) {
                continue;
            }
            if (Files.isDirectory(child)) {
                pending.incrementAndGet();
                dirQueue.offer(child);
            } else if (Files.isRegularFile(child)) {
                fileQueue.offer(child);
            }
        }
    }

    private void awaitQuiescence(AtomicInteger pending) {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
        while (pending.get() > 0) {
            if (System.nanoTime() > deadline) {
                throw new NopRgException("parallel walk timed out");
            }
            LockSupport.parkNanos(1_000_000L);
        }
    }
}
