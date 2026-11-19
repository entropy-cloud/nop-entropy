package io.nop.rg.core.coordinator;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 5 item 19 等价与接线测试（plan 2574 Phase C）：候选行两级路径与整文件路径
 * 逐 span 一致；跨行 pattern 落回退路径；prefilter 路径确实被调用。
 */
public class RegexPrefilterEquivalenceTest {

    private MemorySegment seg(byte[] data) {
        return Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
    }

    /**
     * 测试侧 ground truth：整文件解码 + Pattern + Utf8OffsetMap（复用被测映射类）。
     */
    private List<RegexSearcher.ByteSpan> wholeFileExpected(String content, String regex, boolean ignoreCase) {
        Pattern pattern = Pattern.compile(regex, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        RegexSearcher.Utf8OffsetMap map = new RegexSearcher.Utf8OffsetMap(content);
        Matcher m = pattern.matcher(content);
        List<RegexSearcher.ByteSpan> spans = new ArrayList<>();
        while (m.find()) {
            spans.add(new RegexSearcher.ByteSpan(map.byteOffsetOf(m.start()), map.byteOffsetOf(m.end())));
        }
        return spans;
    }

    private void assertEquivalent(String data, String regex, boolean ignoreCase) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        RegexSearcher searcher = new RegexSearcher(regex, ignoreCase);
        List<RegexSearcher.ByteSpan> actual = searcher.findAll(seg(bytes), bytes.length);
        List<RegexSearcher.ByteSpan> expected = wholeFileExpected(data, regex, ignoreCase);
        assertEquals(expected, actual,
                () -> "regex=" + regex + " ignoreCase=" + ignoreCase + " data=" + data);
    }

    @Test
    public void testPrefilterPathTakenForLiteralBearingPattern() {
        // 接线验证：常见字面量 pattern 走候选行路径（candidateScanCount > 0，fullScan == 0）
        byte[] data = "xx foo12bar yy\nfoo99bar\nno match here".getBytes(StandardCharsets.UTF_8);
        RegexSearcher searcher = new RegexSearcher("foo\\d+bar", false);
        List<RegexSearcher.ByteSpan> spans = searcher.findAll(seg(data), data.length);
        assertEquals(2, spans.size());
        assertTrue(searcher.candidateScanCount > 0, "候选行路径应被调用");
        assertEquals(0, searcher.fullScanCount, "不应回退整文件路径");
    }

    @Test
    public void testCrossLinePatternFallsBackToWholeFile() {
        // 钉子：跨行风险 pattern（\s 可匹配 LF）必须回退整文件路径且结果一致
        byte[] data = "start middle\nend tail\nnothing".getBytes(StandardCharsets.UTF_8);
        RegexSearcher searcher = new RegexSearcher("middle\\s*end", false);
        List<RegexSearcher.ByteSpan> spans = searcher.findAll(seg(data), data.length);
        assertEquals(1, spans.size());
        assertTrue(searcher.fullScanCount > 0, "跨行 pattern 应回退整文件路径");
        assertEquals(0, searcher.candidateScanCount, "跨行 pattern 不得走候选行路径");
        List<RegexSearcher.ByteSpan> expected =
                wholeFileExpected(new String(data, StandardCharsets.UTF_8), "middle\\s*end", false);
        assertEquals(expected, spans);
    }

    @Test
    public void testEquivalenceZeroHits() {
        byte[] data = "alpha beta\ngamma delta\n".getBytes(StandardCharsets.UTF_8);
        RegexSearcher searcher = new RegexSearcher("zzz\\d+qqq", false);
        assertTrue(searcher.candidateScanCount == 0); // find 之后才计数——先跑
        List<RegexSearcher.ByteSpan> spans = searcher.findAll(seg(data), data.length);
        assertTrue(spans.isEmpty());
        assertTrue(searcher.candidateScanCount > 0);
    }

    @Test
    public void testEquivalenceLineStartEndAndMultipleHitsPerLine() {
        // 行首/行尾命中 + 同行多次命中（行只确认一次，不重复产出 span）
        String data = "foo7bar leading foo88bar trailing foo9bar\n"
                + "no hit line\n"
                + "foo42bar\n"
                + "trailing hit foo5bar";
        assertEquivalent(data, "foo\\d+bar", false);
    }

    @Test
    public void testEquivalenceIgnoreCase() {
        String data = "Foo12BAR mid\nFOO7bar\nmiss\nfoo99BaR end";
        assertEquivalent(data, "foo\\d+bar", true);
    }

    @Test
    public void testEquivalenceMultibyteContent() {
        // 多字节字符在非命中行与命中行共存，验证行内 UTF-16→字节映射平移正确
        String data = "中文注释 λλ 字符行\nfoo3bar after 日本語\nfoo4bar";
        assertEquivalent(data, "foo\\d+bar", false);
    }

    @Test
    public void testEquivalenceCrlfLineEndings() {
        // CRLF：\r 留在行内解码，匹配不含 \r（跨行构造已拒绝）
        String data = "foo1bar\r\nplain\r\nfoo2bar\r\n";
        assertEquivalent(data, "foo\\d+bar", false);
    }

    @Test
    public void testEquivalenceAdjacentLinesAndLastLineNoNewline() {
        String data = "hit0 foo1bar\nfoo2bar\nfoo3bar";
        assertEquivalent(data, "foo\\d+bar", false);
    }

    @Test
    public void testFuzzEquivalenceOverRandomCorpus() {
        java.util.Random random = new java.util.Random(2026);
        String[] tokens = {"foo1bar", "foo", "bar", "foo77bar", "x", "\n", "数据"};
        for (int iter = 0; iter < 200; iter++) {
            StringBuilder sb = new StringBuilder();
            int parts = 1 + random.nextInt(12);
            for (int i = 0; i < parts; i++) {
                sb.append(tokens[random.nextInt(tokens.length)]);
            }
            // 保证以整行结束（避免末行截断形态系统性缺测）
            if (sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            String data = sb.toString();
            boolean ignoreCase = random.nextBoolean();
            assertEquivalent(data, "foo\\d+bar", ignoreCase);
            assertEquivalent(data, "foo[0-9]+bar", random.nextBoolean());
        }
    }
}
