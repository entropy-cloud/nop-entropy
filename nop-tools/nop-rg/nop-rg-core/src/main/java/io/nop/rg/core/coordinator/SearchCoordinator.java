package io.nop.rg.core.coordinator;

import io.nop.rg.core.NopRgException;
import io.nop.rg.core.glob.GlobMatcher;
import io.nop.rg.core.io.ChunkedFileReader;
import io.nop.rg.core.io.MappedFileReader;
import io.nop.rg.core.search.LiteralFinderProvider;
import io.nop.rg.core.search.PreparedFinder;
import io.nop.rg.core.search.PreparedLiteral;
import io.nop.rg.core.walk.ParallelFileWalker;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * 搜索编排器（plan 2264 COORD-01..03）：glob 过滤 → 并行遍历 → 映射搜索 → 行级聚合。
 *
 * <p>契约：
 * <ul>
 *   <li>搜索路径两级：≤ chunkedThreshold 整文件 MappedFileReader（搜索与行提取同源）；
 *       &gt; chunkedThreshold 经 ChunkedFileReader 分块扫描 + 命中行整文件映射懒加载提取。</li>
 *   <li>策略选择：字面量 = {@link PreparedLiteral}（含 -i 折叠，经 PreparedFinder 抽象可被
 *       nop-rg-vector 的 SIMD 实现替换）；正则 = {@link RegexSearcher}；VECTOR = ServiceLoader 发现
 *       LiteralFinderProvider，provider 不可用时降级标量并向 stderr 提示，classpath 无 provider 显式抛异常。</li>
 *   <li>二进制文件整文件跳过：文件头 8KB 含 NUL 字节（对齐 rg 默认行为）。</li>
 *   <li>字面量命中枚举为非重叠序列（rg 对齐，plan 2276 G1）：扫描推进 +1 保证发现完整，
 *       报告侧仅保留 start ≥ 前一命中末端的命中（span 级枚举过滤，下游各口径随之对齐 rg）。</li>
 *   <li>行级聚合与结果类型归 {@link MatchAggregator}/{@link FileMatches}
 *       （plan 2268 Phase 2 职责分解）。</li>
 * </ul>
 */
public class SearchCoordinator {

    public enum Strategy {
        LITERAL, REGEX, VECTOR
    }

    public static final int BINARY_SNIFF_BYTES = 8192;
    public static final long DEFAULT_CHUNKED_THRESHOLD = 256L * 1024 * 1024;

    private final int threads;
    private final boolean respectGitignore;
    private final boolean includeHidden;
    private final long chunkedThreshold;

    /**
     * 任务装饰 seam（plan 2564 W-05/W-03，R2-M1 定形）：包私有、默认恒等；测试注入装饰器
     * 确定性向任务边界送 InternalError / RuntimeException，验 coordinator 包装与错误项转换。
     */
    Function<Callable<FileTaskOutcome>, Callable<FileTaskOutcome>> taskDecorator = Function.identity();

    public SearchCoordinator(int threads, boolean respectGitignore, boolean includeHidden) {
        this(threads, respectGitignore, includeHidden, DEFAULT_CHUNKED_THRESHOLD);
    }

    /**
     * @param chunkedThreshold 文件超过该尺寸走 ChunkedFileReader 分块扫描路径（Wave 3 大文件路径），
     *                         否则整文件映射。分块路径支持字面量策略（LITERAL/VECTOR，precompiled finder）；
     *                         REGEX 显式抛异常。
     */
    public SearchCoordinator(int threads, boolean respectGitignore, boolean includeHidden, long chunkedThreshold) {
        if (chunkedThreshold <= 0) {
            throw new IllegalArgumentException("chunkedThreshold must be positive");
        }
        this.threads = threads;
        this.respectGitignore = respectGitignore;
        this.includeHidden = includeHidden;
        this.chunkedThreshold = chunkedThreshold;
    }

    /**
     * 执行搜索；返回部分成功语义的联合结果（plan 2564 W-05）：
     * 单文件失败（I/O / FFM fault / 内存越界等）不弃全——失败文件归入
     * {@link SearchOutcome#errors()} 错误项，其余文件照常聚合；walker fail-fast（已声明契约）
     * 与 VECTOR 策略解析仍在 coordinator 级失败，异常路径保持不变。
     */
    public SearchOutcome search(SearchCommand command) {
        Strategy strategy = command.strategy();
        GlobMatcher globs = GlobMatcher.of(command.getGlobs());
        List<Path> files = new ParallelFileWalker(command.getRoot(), threads, respectGitignore, includeHidden).walk();

        // RegexSearcher / PreparedFinder per-command 编译一次（优化迭代 Round 1：BMH 跳表不再每 match 重建）
        RegexSearcher regexSearcher = strategy == Strategy.REGEX
                ? new RegexSearcher(command.getPattern(), command.isIgnoreCase())
                : null;
        PreparedFinder prepared;
        if (strategy == Strategy.VECTOR) {
            prepared = resolveVectorFinder(command);
        } else if (strategy == Strategy.LITERAL) {
            prepared = PreparedLiteral.compile(command.patternBytes(), command.isIgnoreCase());
        } else {
            prepared = null;
        }
        List<Callable<FileTaskOutcome>> tasks = new ArrayList<>();
        for (Path file : files) {
            String rel = command.getRoot().relativize(file).toString().replace('\\', '/');
            if (!globs.accept(rel)) {
                continue;
            }
            PreparedFinder preparedRef = prepared;
            // 任务边界单点 catch（W-05 失效面 1-4/8 统一覆盖；InternalError 腿为 W-03 包装点输入）；
            // taskDecorator seam 应用于边界 try 之内——注入异常确定性进入任务边界（R2-M1）
            tasks.add(() -> {
                try {
                    return taskDecorator.apply(() -> new FileTaskOutcome(rel,
                            searchFile(file, command, strategy, regexSearcher, preparedRef), null)).call();
                } catch (InternalError | RuntimeException e) {
                    return new FileTaskOutcome(rel, null, toFileError(rel, e));
                }
            });
        }

        ExecutorService pool = new ForkJoinPool(Math.max(1, threads));
        try {
            Map<String, FileMatches> results = new TreeMap<>();
            List<FileError> errors = new ArrayList<>();
            try {
                for (Future<FileTaskOutcome> future : pool.invokeAll(tasks)) {
                    try {
                        FileTaskOutcome outcome = future.get();
                        if (outcome.error() != null) {
                            errors.add(outcome.error());
                        } else if (outcome.matches() != null) {
                            results.put(outcome.relativePath(), outcome.matches());
                        }
                    } catch (ExecutionException e) {
                        // 兜底腿（R1-m1）：任务边界 catch 之外仍逃逸的异常（如 decorator 层 Error）
                        // 不再中断收集——错误聚合继续，已完成的文件结果照常保留
                        errors.add(new FileError("", "search task failed: " + e.getCause()));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopRgException("search interrupted", e);
            }
            return new SearchOutcome(results, errors);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 任务边界异常 → 错误项（plan 2564 W-05/W-03）：RuntimeException/NopRgException 消息保留
     * 原始异常上下文（裸 InternalError 常无消息——R1-m2，不经此腿）；InternalError（FFM 映射
     * fault 的实测转换形态，W-03）在此补「search fault while scanning: &lt;rel&gt;」带路径包装，
     * 使错误可见性回到「错误携带文件名」族内一致性（对照 stat/map 既有文案形态）。
     */
    private static FileError toFileError(String rel, Throwable e) {
        if (e instanceof InternalError) {
            NopRgException wrapped = new NopRgException("search fault while scanning: " + rel, e);
            return new FileError(rel, wrapped.getMessage());
        }
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        return new FileError(rel, message);
    }

    /**
     * 单文件搜索；无命中或二进制文件返回 null。
     * 两级路径（plan 2265 Phase 3）：≤ chunkedThreshold 整文件映射；> chunkedThreshold 分块扫描
     * （仅字面量策略，REGEX 显式抛异常）+ 命中行经整文件映射懒加载提取。
     * 两条路径对受支持策略结果一致。
     */
    private FileMatches searchFile(Path file, SearchCommand command, Strategy strategy,
                                   RegexSearcher regexSearcher, PreparedFinder prepared) {
        boolean regex = strategy == Strategy.REGEX;
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new NopRgException("stat file failed: " + file, e);
        }
        if (size > chunkedThreshold && regex) {
            // 契约裁定（plan 2265 Phase 3）：整文件解码 >阈值文件需 2-4GB 堆——显式失败，不静默回退
            throw new NopRgException("regex search is not supported for files larger than chunkedThreshold ("
                    + chunkedThreshold + " bytes): " + file);
        }
        if (!regex) {
            if (size == 0) {
                return null;
            }
            if (size > chunkedThreshold) {
                return searchFileChunked(file, command, prepared);
            }
        }
        try (MappedFileReader reader = new MappedFileReader(file)) {
            MemorySegment seg = reader.getSegment();
            long mappedSize = reader.getSize();
            if (mappedSize == 0 || isBinary(seg, mappedSize)) {
                return null;
            }

            List<MatchSpan> spans = regex
                    ? regexSpans(seg, mappedSize, regexSearcher)
                    : literalSpans(seg, mappedSize, prepared);

            if (spans.isEmpty()) {
                return null;
            }
            return aggregate(spans, seg, mappedSize, command);
        }
    }

    /**
     * 大文件分块扫描：overlap = patternLen-1，只报告主区间内命中（plan 2263 去重语义）；
     * 命中行文本/行号对命中文件懒加载整文件映射提取。REGEX 显式失败不静默回退。
     */
    private FileMatches searchFileChunked(Path file, SearchCommand command, PreparedFinder prepared) {
        byte[] pattern = command.patternBytes();
        int overlap = Math.max(0, pattern.length - 1);
        List<MatchSpan> spans = new ArrayList<>();
        try (ChunkedFileReader reader = new ChunkedFileReader(file,
                (int) Math.min(chunkedThreshold, Integer.MAX_VALUE), overlap)) {
            ChunkedFileReader.Chunk chunk;
            boolean first = true;
            long lastReportedEnd = 0; // 跨 chunk 持续：跨界命中由前块报告后压制后续块内的重叠出现
            while ((chunk = reader.nextChunk()) != null) {
                MemorySegment seg = chunk.segment();
                if (first) {
                    first = false;
                    if (isBinary(seg, Math.min(chunk.viewLen(), BINARY_SNIFF_BYTES))) {
                        return null;
                    }
                }
                long from = 0;
                while (true) {
                    long pos = prepared.find(seg, from, chunk.viewLen());
                    if (pos < 0) {
                        break;
                    }
                    long absolute = chunk.absoluteOffset(pos);
                    if (chunk.inPrimary(absolute) && absolute >= lastReportedEnd) {
                        spans.add(new MatchSpan(absolute, absolute + pattern.length));
                        lastReportedEnd = absolute + pattern.length;
                    }
                    from = pos + 1;
                }
            }
        }
        if (spans.isEmpty()) {
            return null;
        }
        // 行提取：整文件映射（虚拟内存按需换页；与已关闭的分块映射不冲突，审查实测双映射共存）
        try (MappedFileReader whole = new MappedFileReader(file)) {
            return aggregate(spans, whole.getSegment(), whole.getSize(), command);
        }
    }

    private FileMatches aggregate(List<MatchSpan> spans, MemorySegment seg, long size, SearchCommand command) {
        return MatchAggregator.aggregate(spans, seg, size, command.getMaxMatchesPerFile(),
                command.isIncludeLineText(), command.isIncludeSubmatchText());
    }

    /**
     * VECTOR 策略：ServiceLoader 发现 LiteralFinderProvider（plan 2266 裁定）。
     * 无 provider = classpath 缺 nop-rg-vector（显式报错）；provider 内部孵化模块
     * 不可用时降级标量并向 stderr 提示（audit N2）；ServiceConfigurationError 显式转 NopRgException（audit N1）。
     */
    private PreparedFinder resolveVectorFinder(SearchCommand command) {
        try {
            Iterator<LiteralFinderProvider> it = ServiceLoader
                    .load(LiteralFinderProvider.class).iterator();
            if (!it.hasNext()) {
                throw new NopRgException("vector strategy unavailable: nop-rg-vector not on classpath"
                        + " (SIMD requires --add-modules jdk.incubator.vector; without it the provider falls back to scalar)");
            }
            LiteralFinderProvider provider = it.next();
            PreparedFinder finder = provider.compile(command.patternBytes(), command.isIgnoreCase());
            if (!provider.available()) {
                System.err.println("nop-rg: vector acceleration unavailable (" + provider.unavailableReason()
                        + "), falling back to scalar");
            }
            return finder;
        } catch (ServiceConfigurationError e) {
            throw new NopRgException("vector provider load failed", e);
        }
    }

    /**
     * 字面量命中枚举（非重叠，plan 2276 G1）：扫描推进 +1 保证自重叠出现可被发现；
     * 报告侧过滤为 start ≥ 前一报告命中末端（rg 从命中末端继续的等价序列）。
     */
    private static List<MatchSpan> literalSpans(MemorySegment seg, long size, PreparedFinder prepared) {
        List<MatchSpan> spans = new ArrayList<>();
        long from = 0;
        int patternLength = prepared.patternLength();
        long lastReportedEnd = 0;
        while (true) {
            long pos = prepared.find(seg, from, size);
            if (pos < 0) {
                break;
            }
            if (pos >= lastReportedEnd) {
                spans.add(new MatchSpan(pos, pos + patternLength));
                lastReportedEnd = pos + patternLength;
            }
            from = pos + 1;
        }
        return spans;
    }

    private static List<MatchSpan> regexSpans(MemorySegment seg, long size, RegexSearcher searcher) {
        List<MatchSpan> spans = new ArrayList<>();
        for (RegexSearcher.ByteSpan span : searcher.findAll(seg, size)) {
            spans.add(new MatchSpan(span.byteStart(), span.byteEnd()));
        }
        return spans;
    }

    private boolean isBinary(MemorySegment seg, long size) {
        long limit = Math.min(size, BINARY_SNIFF_BYTES);
        for (long i = 0; i < limit; i++) {
            if (seg.get(ValueLayout.JAVA_BYTE, i) == 0) {
                return true;
            }
        }
        return false;
    }
}
