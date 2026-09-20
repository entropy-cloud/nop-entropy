package io.nop.rg.core.walk;

import io.nop.core.git.GitIgnoreFile;
import io.nop.core.resource.impl.FileResource;
import io.nop.rg.core.NopRgException;

import java.io.IOException;
import java.security.AccessControlException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * 并行文件遍历器（nop-rg design 决策 4：专用线程池，非 commonPool）。
 *
 * <p>契约（plan 2264/2265 钉死）：
 * <ul>
 *   <li>输入为 {@link Path} 目录根；输出为排序后的常规文件列表（确定性）。</li>
 *   <li>{@code respectGitignore}（默认 true）：以 nop-core {@link GitIgnoreFile} 过滤（内部以
 *       {@link FileResource} 包装；VFS 初始化由调用方负责——库本体无副作用，未初始化时异常自然抛出）。</li>
 *   <li>{@code includeHidden}（默认 false）：{@code .} 前缀文件/目录跳过（.gitignore 本身即隐藏文件，
 *       默认不入结果；其规则由 GitIgnoreFile.create() 自身遍历加载，不受本开关影响）。</li>
 *   <li>被过滤掉的目录不递归（剪枝）。</li>
 *   <li>符号链接目录不递归（目录环会导致无限递归；对齐 rg 默认不跟随目录链接）；
 *       文件符号链接按普通文件读取；悬空链接跳过（plan 2273 A1）。</li>
 *   <li>遍历错误（目录不可读等）快速失败：walk() 抛 {@link NopRgException}，不静默返回不完整结果
 *       （plan 2265 OPT 修复 Wave 2 audit Minor m2）。</li>
 *   <li>工作窃取：ForkJoinPool（专用实例）+ 目录级 RecursiveAction，任务粒度随目录树深度/宽度自适应
 *       （plan 2265 OPT-01/02/03）。</li>
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
     * 遍历并返回排序后的常规文件列表。每次调用独立执行（新建 ForkJoinPool 并在结束后关闭）。
     *
     * @throws NopRgException 遍历发生 I/O 错误（如目录不可读）
     */
    public List<Path> walk() {
        if (!Files.isDirectory(root)) {
            throw new NopRgException("walk root is not a directory: " + root);
        }

        GitIgnoreFile ignore = loadIgnoreRules();
        WalkContext context = new WalkContext(ignore);
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.invoke(new ScanAction(root, context));
        } finally {
            pool.shutdownNow();
        }
        if (context.error != null) {
            throw new NopRgException("walk failed: " + root, context.error);
        }

        List<Path> result = new ArrayList<>(context.files);
        result.sort(Comparator.comparing(Path::toString));
        return result;
    }

    private GitIgnoreFile loadIgnoreRules() {
        if (!respectGitignore) {
            return null;
        }
        return GitIgnoreFile.create(new FileResource(root.toFile()));
    }

    private static final class WalkContext {
        final List<Path> files = new ArrayList<>();
        final GitIgnoreFile ignore;
        volatile Throwable error;

        WalkContext(GitIgnoreFile ignore) {
            this.ignore = ignore;
        }

        void recordError(Throwable t) {
            if (error == null) {
                error = t; // 首个错误胜出，快速失败
            }
        }
    }

    private final class ScanAction extends RecursiveAction {
        private final Path dir;
        private final WalkContext context;

        ScanAction(Path dir, WalkContext context) {
            this.dir = dir;
            this.context = context;
        }

        @Override
        protected void compute() {
            if (context.error != null) {
                return; // 快速失败后停止分发新任务
            }
            List<Path> children;
            try {
                try (Stream<Path> stream = Files.list(dir)) {
                    children = stream.toList();
                }
            } catch (IOException e) {
                context.recordError(e);
                return;
            }
            List<ScanAction> subdirs = null;
            for (Path child : children) {
                String name = child.getFileName() == null ? "" : child.getFileName().toString();
                boolean hidden = name.startsWith(".");
                if (hidden && !includeHidden) {
                    continue;
                }
                if (context.ignore != null && context.ignore.isIgnored(new FileResource(child.toFile()))) {
                    continue;
                }
                boolean isDir;
                try {
                    isDir = Files.isDirectory(child);
                } catch (AccessControlException e) {
                    context.recordError(e);
                    return;
                }
                // 符号链接目录不递归：目录环（a/b -> a）会无限递归；对齐 rg 默认不跟随目录链接。
                // 文件符号链接保持现状（按普通文件读取内容）；悬空链接保持现状（isDirectory/isRegularFile
                // 均为 false，静默跳过）
                if (isDir && Files.isSymbolicLink(child)) {
                    continue;
                }
                if (isDir) {
                    ScanAction action = new ScanAction(child, context);
                    if (subdirs == null) {
                        subdirs = new ArrayList<>();
                    }
                    subdirs.add(action);
                } else if (Files.isRegularFile(child)) {
                    synchronized (context) {
                        context.files.add(child);
                    }
                }
            }
            if (subdirs != null) {
                // 目录级任务分治：ForkJoinPool work-stealing 自适应调度（OPT-01/03）
                invokeAll(subdirs);
            }
        }
    }
}
