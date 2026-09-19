package io.nop.rg.core.coordinator;

import io.nop.rg.core.NopRgException;
import io.nop.rg.core.glob.GlobMatcher;
import io.nop.rg.core.io.MappedFileReader;
import io.nop.rg.core.search.ByteSearchStrategy;
import io.nop.rg.core.search.ScalarByteSearcher;
import io.nop.rg.core.walk.ParallelFileWalker;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
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

    private final int threads;
    private final boolean respectGitignore;
    private final boolean includeHidden;

    public SearchCoordinator(int threads, boolean respectGitignore, boolean includeHidden) {
        this.threads = threads;
        this.respectGitignore = respectGitignore;
        this.includeHidden = includeHidden;
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

        List<Callable<AbstractMap.SimpleEntry<String, FileMatches>>> tasks = new ArrayList<>();
        for (Path file : files) {
            String rel = command.getRoot().relativize(file).toString().replace('\\', '/');
            if (!globs.accept(rel)) {
                continue;
            }
            tasks.add(() -> new AbstractMap.SimpleEntry<>(rel, searchFile(file, command, strategy)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
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
     */
    private FileMatches searchFile(Path file, SearchCommand command, Strategy strategy) {
        try (MappedFileReader reader = new MappedFileReader(file)) {
            MemorySegment seg = reader.getSegment();
            long size = reader.getSize();
            if (size == 0 || isBinary(seg, size)) {
                return null;
            }

            List<long[]> spans = new ArrayList<>(); // {byteStart, byteEnd} 升序
            if (strategy == Strategy.REGEX) {
                RegexSearcher searcher = new RegexSearcher(command.getPattern(), command.isIgnoreCase());
                for (RegexSearcher.ByteSpan span : searcher.findAll(seg, size)) {
                    spans.add(new long[]{span.byteStart(), span.byteEnd()});
                }
            } else {
                ByteSearchStrategy searcher = command.isIgnoreCase()
                        ? FoldingByteSearcher.INSTANCE
                        : ScalarByteSearcher.INSTANCE;
                byte[] pattern = command.patternBytes();
                long from = 0;
                while (true) {
                    long pos = searcher.findPattern(seg, from, size, pattern);
                    if (pos < 0) {
                        break;
                    }
                    spans.add(new long[]{pos, pos + pattern.length});
                    from = pos + 1;
                }
            }

            if (spans.isEmpty()) {
                return null;
            }
            return buildLineMatches(seg, size, spans, command.getMaxMatchesPerFile());
        }
    }

    private FileMatches buildLineMatches(MemorySegment seg, long size, List<long[]> spans, int maxLines) {
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
                    lines.add(newLineMatch(seg, currentInfo, currentSubmatches));
                }
                currentInfo = info;
                currentSubmatches = new ArrayList<>();
            }
            currentSubmatches.add(new Submatch(span[0], span[1], decode(seg, span[0], (int) (span[1] - span[0]))));
        }
        if (currentInfo != null && !truncated) {
            if (maxLines > 0 && lines.size() >= maxLines) {
                truncated = true;
            } else {
                lines.add(newLineMatch(seg, currentInfo, currentSubmatches));
            }
        }
        return new FileMatches(lines, truncated);
    }

    private static LineMatch newLineMatch(MemorySegment seg, LineCursor.LineInfo info, List<Submatch> submatches) {
        String content = decode(seg, info.lineStart(), (int) (info.contentEnd() - info.lineStart()));
        String terminator = decode(seg, info.contentEnd(), (int) (info.lineEnd() - info.contentEnd()));
        return new LineMatch(info, List.copyOf(submatches), content, terminator);
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
     * 单文件结果：命中的行列表（升序）与截断标志。
     */
    public record FileMatches(List<LineMatch> lines, boolean truncated) {
        public int lineCount() {
            return lines.size();
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
