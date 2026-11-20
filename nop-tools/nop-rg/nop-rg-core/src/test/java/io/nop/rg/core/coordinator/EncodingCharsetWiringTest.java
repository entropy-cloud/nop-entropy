package io.nop.rg.core.coordinator;

import io.nop.rg.core.io.MappedFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * `--encoding` 三处一致接线验证（plan 2575 Phase E2，R8）：
 * SearchCommand.charset 在运行时确实被模式编码（patternBytes / prefilter）、
 * regex 解码（含 CharsetOffsetMap）与输出解码三条链消费。
 */
public class EncodingCharsetWiringTest {

    private static final Charset GBK = Charset.forName("GBK");

    @TempDir
    Path tempDir;

    /** 模式侧：patternBytes 按指定 charset 编码（漏改回退 UTF-8 时变异探针必红）。 */
    @Test
    public void testPatternBytesUseCommandCharset() {
        SearchCommand cmd = new SearchCommand(tempDir, "needle 中文", SearchCoordinator.Strategy.LITERAL,
                false, List.of(), 0, true, true, GBK);
        assertArrayEquals("needle 中文".getBytes(GBK), cmd.patternBytes());
        assertEquals(GBK, cmd.charset());
        // 兼容重载默认 UTF-8（R8）
        SearchCommand legacy = new SearchCommand(tempDir, "x", SearchCoordinator.Strategy.LITERAL, false, List.of(), 0);
        assertEquals(StandardCharsets.UTF_8, legacy.charset());
    }

    /** regex 侧：GBK 整文件解码 + CharsetOffsetMap 按实际字节长度映射（R5，UTF-8 写死必漂移）。 */
    @Test
    public void testRegexSearcherGbkDecodeAndOffsets() throws Exception {
        // "needle 中文 x" = GBK 13 字节（含尾部空格让 . 有可匹配字符）
        byte[] corpus = "needle 中文 x\nabc\n".getBytes(GBK);
        Path file = tempDir.resolve("gbk.txt");
        Files.write(file, corpus);
        try (MappedFileReader reader = new MappedFileReader(file)) {
            MemorySegment seg = reader.getSegment();
            RegexSearcher searcher = new RegexSearcher("中文.", false, GBK);
            List<RegexSearcher.ByteSpan> spans = searcher.findAll(seg, reader.getSize());
            assertEquals(1, spans.size(), "GBK 整文件 regex 必须命中");
            // needle 6 + 空格 1 → 中文 7..11，. 匹配空格 → 7..12；UTF-8 写死时中文按 3 字节 → 7..13 漂移必红
            assertEquals(7, spans.get(0).byteStart());
            assertEquals(12, spans.get(0).byteEnd());
        }
    }

    /** regex 侧 prefilter：requiredLiteral 按 GBK 编码（候选行路径接线，R5）。 */
    @Test
    public void testRegexCandidatePathGbkPrefilter() throws Exception {
        byte[] corpus = "xx 中文 xx\nzz zz zz\nyy 中文 yy\n".getBytes(GBK);
        Path file = tempDir.resolve("gbk2.txt");
        Files.write(file, corpus);
        try (MappedFileReader reader = new MappedFileReader(file)) {
            MemorySegment seg = reader.getSegment();
            RegexSearcher searcher = new RegexSearcher("中.", false, GBK);
            List<RegexSearcher.ByteSpan> spans = searcher.findAll(seg, reader.getSize());
            // 候选行两级路径（prefilter GBK 编码扫出候选行）：行 1 "xx 中文 xx\n"（11B）→ 中. 3..7；
            // 行 3 起点 11+9=20，"yy " 后 → 中. 23..27
            assertEquals(2, spans.size(), "prefilter 必须以 GBK 编码扫出候选行: " + spans);
            assertEquals(3, spans.get(0).byteStart());
            assertEquals(7, spans.get(0).byteEnd());
            assertEquals(23, spans.get(1).byteStart());
            assertTrue(searcher.candidateScanCount > 0, "必须走候选行路径（接线验证）");
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        org.junit.jupiter.api.Assertions.assertTrue(cond, msg);
    }

    /** 输出侧：aggregate 按 GBK 解码 text（漏改回退 UTF-8 时输出乱码必红）。 */
    @Test
    public void testAggregatorGbkOutputDecode() throws Exception {
        byte[] corpus = "needle 中文\n".getBytes(GBK);
        Path file = tempDir.resolve("gbk3.txt");
        Files.write(file, corpus);
        try (MappedFileReader reader = new MappedFileReader(file)) {
            MemorySegment seg = reader.getSegment();
            List<MatchSpan> spans = List.of(new MatchSpan(0, 6));
            FileMatches matches = MatchAggregator.aggregate(spans, seg, reader.getSize(), 0,
                    true, true, GBK);
            LineMatch line = matches.getLines().get(0);
            assertEquals("needle 中文", line.getText(), "GBK 输出文本必须正确解码");
            // GBK 字节非法 UTF-8 → bytes 非 null（R2：出现条件与 charset 无关）；含终止符共 12 字节
            assertEquals(12, line.getContentBytes().length);
            assertNull(line.getSubmatches().get(0).bytes()); // needle 区间为合法 ASCII
        }
    }
}
