package io.nop.rg.core.coordinator;

import io.nop.rg.core.NopRgException;
import io.nop.rg.core.glob.GlobMatcher;
import io.nop.rg.core.io.ChunkedFileReader;
import io.nop.rg.core.io.MappedFileReader;
import io.nop.rg.core.search.PreparedLiteral;
import io.nop.rg.core.search.ScalarByteSearcher;
import io.nop.rg.core.walk.ParallelFileWalker;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 搜索编排器（plan 2264 COORD-01..03）：glob 过滤 → 并行遍历 → 整文件映射搜索 → 行级聚合。
 *
 * <p>契约：
 * <ul>
 *   <li>搜索路径为整文件 MappedFileReader（搜索与行提取同源）；ChunkedFileReader 的生产线接线归 Wave 3
 *       大文件路径（plan 2264 Deferred But Adjudicated）。</li>
 *   <li>策略选择：字面量 = {@link ScalarByteSearcher}（区分大小写）或 {@link FoldingByteSearcher}（-i）；
 *       正则 = {@link RegexSearcher}；VECTOR 策略在 Wave 4 前显式抛异常，不静默降级。</li>
 *   <li>二进制文件整文件跳过：文件头 8KB 含 NUL 字节（对齐 rg 默认行为）。</li>
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

    public SearchCoordinator(int threads, boolean respectGitignore, boolean includeHidden) {
        this(threads, respectGitignore, includeHidden, DEFAULT_CHUNKED_THRESHOLD);
    }

    /**
     * @param chunkedThreshold 文件超过该尺寸走 ChunkedFileReader 分块扫描路径（Wave 3 大文件路径），
     *                         否则整文件映射。分块路径仅支持 LITERAL/FOLDING；REGEX 显式抛异常。
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
     * 执行搜索；返回按相对路径排序的逐文件结果（仅含有命中的文件）。
     */
    public Map<String, FileMatches> search(SearchCommand command) {
        Strategy strategy = command.strategy();
        if (strategy == Strategy.VECTOR) {
            // 显式失败：Vector 策略 Wave 4 前不可用，不静默降级
            throw new NopRgException("vector strategy is not available: requires nop-rg-vector (Wave 4, JDK 25+)");
        }

        GlobMatcher globs = GlobMatcher.of(command.getGlobs());
        List<Path> files = new ParallelFileWalker(command.getRoot(), threads, respectGitignore, includeHidden).walk();

        // RegexSearcher / PreparedLiteral per-command 编译一次（优化迭代 Round 1：BMH 跳表不再每 match 重建）
        RegexSearcher regexSearcher = strategy == Strategy.REGEX
                ? new RegexSearcher(command.getPattern(), command.isIgnoreCase())
                : null;
        PreparedLiteral prepared = strategy == Strategy.LITERAL
                ? PreparedLiteral.compile(command.patternBytes(), command.isIgnoreCase())
                : null;
        List<Callable<AbstractMap.SimpleEntry<String, FileMatches>>> tasks = new ArrayList<>();
        for (Path file : files) {
            String rel = command.getRoot().relativize(file).toString().replace('\\', '/');
            if (!globs.accept(rel)) {
                continue;
            }
            PreparedLiteral preparedRef = prepared;
            tasks.add(() -> new AbstractMap.SimpleEntry<>(rel,
                    searchFile(file, command, strategy, regexSearcher, preparedRef)));
        }

        ExecutorService pool = new java.util.concurrent.ForkJoinPool(Math.max(1, threads));
        try {
            Map<String, FileMatches> results = new TreeMap<>();
            try {
                for (Future<AbstractMap.SimpleEntry<String, FileMatches>> future : pool.invokeAll(tasks)) {
                    AbstractMap.SimpleEntry<String, FileMatches> entry = future.get();
                    if (entry.getValue() != null) {
                        results.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopRgException("search interrupted", e);
            } catch (ExecutionException e) {
                throw new NopRgException("search task failed", e.getCause());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 单文件搜索；无命中或二进制文件返回 null。
     * 两级路径（plan 2265 Phase 3）：≤ chunkedThreshold 整文件映射；> chunkedThreshold 分块扫描
     * （仅 LITERAL/FOLDING，REGEX 显式抛异常）+ 命中行经整文件映射懒加载提取。
     * 两条路径对受支持策略结果一致。
     */
    private FileMatches searchFile(Path file, SearchCommand command, Strategy strategy,
                                   RegexSearcher regexSearcher, PreparedLiteral prepared) {
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

            List<long[]> spans = new ArrayList<>(); // {byteStart, byteEnd} 升序
            if (regex) {
                spans.addAll(regexSpans(seg, mappedSize, regexSearcher));
            } else {
                spans.addAll(literalSpans(seg, mappedSize, prepared));
            }

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
    private FileMatches searchFileChunked(Path file, SearchCommand command, PreparedLiteral prepared) {
        byte[] pattern = command.patternBytes();
        int overlap = Math.max(0, pattern.length - 1);
        List<long[]> spans = new ArrayList<>();
        try (ChunkedFileReader reader = new ChunkedFileReader(file,
                (int) Math.min(chunkedThreshold, Integer.MAX_VALUE), overlap)) {
            ChunkedFileReader.Chunk chunk;
            boolean first = true;
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
                    if (chunk.inPrimary(absolute)) {
                        spans.add(new long[]{absolute, absolute + pattern.length});
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

    /**
     * 命中聚合：count 口径（includeLineText=false）走纯行计数快速路径（优化迭代 Round 5）；
     * 其余构建 LineMatch 列表。
     */
    private FileMatches aggregate(List<long[]> spans, MemorySegment seg, long size, SearchCommand command) {
        int maxLines = command.getMaxMatchesPerFile();
        if (!command.isIncludeLineText()) {
            LineCursor cursor = new LineCursor(seg, size);
            int lineCount = 0;
            long lastLineStart = -1;
            boolean truncated = false;
            for (long[] span : spans) {
                LineCursor.LineInfo info = cursor.advance(span[0]);
                if (info.lineStart() != lastLineStart) {
                    if (maxLines > 0 && lineCount >= maxLines) {
                        truncated = true;
                        break;
                    }
                    lineCount++;
                    lastLineStart = info.lineStart();
                }
            }
            return FileMatches.ofCount(lineCount, truncated);
        }
        return buildLineMatches(seg, size, spans, maxLines, true);
    }

    private static List<long[]> literalSpans(MemorySegment seg, long size, PreparedLiteral prepared) {
        List<long[]> spans = new ArrayList<>();
        long from = 0;
        int patternLength = prepared.patternLength();
        while (true) {
            long pos = prepared.find(seg, from, size);
            if (pos < 0) {
                break;
            }
            spans.add(new long[]{pos, pos + patternLength});
            from = pos + 1;
        }
        return spans;
    }

    private static List<long[]> regexSpans(MemorySegment seg, long size, RegexSearcher searcher) {
        List<long[]> spans = new ArrayList<>();
        for (RegexSearcher.ByteSpan span : searcher.findAll(seg, size)) {
            spans.add(new long[]{span.byteStart(), span.byteEnd()});
        }
        return spans;
    }

    private FileMatches buildLineMatches(MemorySegment seg, long size, List<long[]> spans, int maxLines,
                                         boolean includeLineText) {
        LineCursor cursor = new LineCursor(seg, size);
        List<LineMatch> lines = new ArrayList<>();
        List<Submatch> currentSubmatches = new ArrayList<>();
        LineCursor.LineInfo currentInfo = null;
        boolean truncated = false;
        for (long[] span : spans) { // spans 升序，同行命中的行信息相同
            LineCursor.LineInfo info = cursor.advance(span[0]);
            if (currentInfo == null || info.lineStart() != currentInfo.lineStart()) {
                if (currentInfo != null) {
                    if (maxLines > 0 && lines.size() >= maxLines) {
                        truncated = true;
                        break;
                    }
                    // submatches 所有权转移给 LineMatch（优化迭代 Round 3：去掉 List.copyOf）
                    lines.add(newLineMatch(seg, currentInfo, currentSubmatches, includeLineText));
                    currentSubmatches = new ArrayList<>();
                }
                currentInfo = info;
                currentSubmatches.clear();
            }
            currentSubmatches.add(new Submatch(span[0], span[1],
                    includeLineText ? decode(seg, span[0], (int) (span[1] - span[0])) : null));
        }
        if (currentInfo != null && !truncated) {
            if (maxLines > 0 && lines.size() >= maxLines) {
                truncated = true;
            } else {
                lines.add(newLineMatch(seg, currentInfo, currentSubmatches, includeLineText));
            }
        }
        return FileMatches.ofLines(lines, truncated);
    }

    private static LineMatch newLineMatch(MemorySegment seg, LineCursor.LineInfo info, List<Submatch> submatches,
                                          boolean includeLineText) {
        if (!includeLineText) {
            // count 口径（rg -c 等价）：不解码行文本/子匹配文本（优化迭代 Round 4）
            return new LineMatch(info, submatches, null, null);
        }
        String content = decode(seg, info.lineStart(), (int) (info.contentEnd() - info.lineStart()));
        // 终止符由长度直接判定，免解码（优化迭代 Round 3）
        long termLen = info.lineEnd() - info.contentEnd();
        String terminator = termLen == 0 ? "" : termLen == 1 ? "\n" : "\r\n";
        return new LineMatch(info, submatches, content, terminator);
    }

    private static String decode(MemorySegment seg, long offset, int length) {
        if (length <= 0) {
            return "";
        }
        byte[] bytes = seg.asSlice(offset, length).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
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

    /**
     * 单文件结果：命中的行列表（升序）与截断标志；count 口径（includeLineText=false 且仅需行数）
     * 下 lines 为空、仅携带 lineCount（优化迭代 Round 5：跳过 LineMatch/Submatch 构建）。
     */
    public static final class FileMatches {
        private final List<LineMatch> lines;
        private final boolean truncated;
        private final int countOnly; // -1 表示非 count-only

        private FileMatches(List<LineMatch> lines, boolean truncated, int countOnly) {
            this.lines = lines;
            this.truncated = truncated;
            this.countOnly = countOnly;
        }

        static FileMatches ofLines(List<LineMatch> lines, boolean truncated) {
            return new FileMatches(lines, truncated, -1);
        }

        static FileMatches ofCount(int lineCount, boolean truncated) {
            return new FileMatches(List.of(), truncated, lineCount);
        }

        public List<LineMatch> getLines() {
            return lines;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public int lineCount() {
            return countOnly >= 0 ? countOnly : lines.size();
        }

        public boolean isCountOnly() {
            return countOnly >= 0;
        }
    }

    /**
     * 单个命中：文件域字节区间 + 命中文本。
     */
    public record Submatch(long byteStart, long byteEnd, String text) {
    }

    /**
     * 单行命中：行信息 + 行内命中列表 + 行文本（不含/含终止符两种形态，对齐 rg lines.text）。
     */
    public static final class LineMatch {
        private final long lineNumber;
        private final long lineStart;
        private final long lineEnd;    // 含终止符
        private final long contentEnd; // 不含终止符
        private final List<Submatch> submatches;
        private final String text;     // 不含终止符
        private final String lineWithTerminator; // 含终止符（rg lines.text 形态）

        LineMatch(LineCursor.LineInfo info, List<Submatch> submatches, String content, String terminator) {
            this.lineNumber = info.lineNumber();
            this.lineStart = info.lineStart();
            this.lineEnd = info.lineEnd();
            this.contentEnd = info.contentEnd();
            this.submatches = submatches;
            this.text = content;
            this.lineWithTerminator = content + terminator;
        }

        public long getLineNumber() {
            return lineNumber;
        }

        public long getLineStart() {
            return lineStart;
        }

        public long getLineEnd() {
            return lineEnd;
        }

        public long getContentEnd() {
            return contentEnd;
        }

        public List<Submatch> getSubmatches() {
            return submatches;
        }

        public String getText() {
            return text;
        }

        public String getLineWithTerminator() {
            return lineWithTerminator;
        }
    }
}
