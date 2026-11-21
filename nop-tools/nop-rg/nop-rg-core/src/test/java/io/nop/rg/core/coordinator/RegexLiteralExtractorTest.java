package io.nop.rg.core.coordinator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 5 item 19 提取器单测（plan 2574 Phase C）：接受语法子集钉死——
 * 字面量 run、转义还原、字符类拒绝、量词截断、交替拒绝、跨行构造拒绝、大小写无关提取。
 */
public class RegexLiteralExtractorTest {

    @Test
    public void testPlainLiteralRun() {
        assertEquals("needle", RegexLiteralExtractor.extract("needle"));
        // 最长 run 当选：'X' 非元字符，属字面量，整串连续
        assertEquals("abcXlongest", RegexLiteralExtractor.extract("abcXlongest"));
    }

    @Test
    public void testEscapedPunctuationIsLiteral() {
        assertEquals("a.b", RegexLiteralExtractor.extract("a\\.b"));
        assertEquals("x{y", RegexLiteralExtractor.extract("x\\{y"));
        assertEquals("a\\b", RegexLiteralExtractor.extract("a\\\\b"));
    }

    @Test
    public void testCharClassTerminatesRun() {
        // 类内容非字面量，两侧 run 中取最长（"x" vs "y" 同长取先 "x"）
        assertEquals("x", RegexLiteralExtractor.extract("x[0-9]y"));
        assertEquals("prefix", RegexLiteralExtractor.extract("prefix[abc]s"));
    }

    @Test
    public void testQuantifierChopsLastChar() {
        // abc+ : 'c' 被量化不再必然出现，run 截为 "ab"
        assertEquals("ab", RegexLiteralExtractor.extract("abc+"));
        // (abc)? : 组内不收集，组外无 run → null
        assertNull(RegexLiteralExtractor.extract("(abc)?"));
        // a{2,3}b : 'a' 被量化，"b" 当选
        assertEquals("b", RegexLiteralExtractor.extract("a{2,3}b"));
    }

    @Test
    public void testShorthandTerminatesRunButContinues() {
        // foo\d+bar : "foo" 与 "bar" 均为顶层必需 run，取先出现的 "foo"（同长不替换）
        assertEquals("foo", RegexLiteralExtractor.extract("foo\\d+bar"));
        // 更长 run 在 shorthand 之后时取后者
        assertEquals("after", RegexLiteralExtractor.extract("x\\w+after"));
    }

    @Test
    public void testAlternationRejected() {
        assertNull(RegexLiteralExtractor.extract("foo|bar"));
        assertNull(RegexLiteralExtractor.extract("aXc|abc"));
    }

    @Test
    public void testGroupBoundariesCloseRun() {
        assertEquals("c", RegexLiteralExtractor.extract("(abc)c"));
        // "pre"/"x" 被 ( 与 [ 截断，组后 run "tail" 最长当选
        assertEquals("tail", RegexLiteralExtractor.extract("pre(look)x[0-9]{2}tail"));
    }

    @Test
    public void testAnchorsAndDotCloseRun() {
        assertEquals("foo", RegexLiteralExtractor.extract("^foo$"));
        // '.' 非字面量，终止 run（"." 不匹配 LF 但也不是必须字面量）
        assertEquals("a", RegexLiteralExtractor.extract("a.b"));
    }

    @Test
    public void testNoExtractReturnsNull() {
        assertNull(RegexLiteralExtractor.extract(""));
        assertNull(RegexLiteralExtractor.extract("\\d+"));
        assertNull(RegexLiteralExtractor.extract("[a-z]+"));
        assertNull(RegexLiteralExtractor.extract("(foo)?(bar)?"));
    }

    @Test
    public void testCrossLineConstructsRejected() {
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\s*b"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("x[^y]z"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\nb"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\rb"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\Rb"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\Db"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\Wb"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\p{Alpha}b"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\\x0ab"));
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a\nb"));
        // 无 DOTALL 的 '.' 不匹配 LF，安全
        assertFalse(RegexLiteralExtractor.mayMatchAcrossLines("a.b"));
        assertFalse(RegexLiteralExtractor.mayMatchAcrossLines("foo\\d+bar"));
        assertFalse(RegexLiteralExtractor.mayMatchAcrossLines("^start[0-9]end$"));
    }

    // ---------------- plan 2587 钉：字符类内行终止构造 ----------------

    /**
     * plan 2587 钉（审查裁定钉子口径）：正字符类内的行终止构造必须被 mayMatchAcrossLines 检出。
     * 缺陷态（原实现复用 skipClass 整体跳过类内容）：三断言红。
     * 变异探针：回退 skipClassScanLineTerminators 接线（改回 skipClass）→ 本钉必红。
     */
    @Test
    public void testPlan2587_lineTerminatorEscapesInsideCharClassRejected() {
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a[\\n]b"),
                "字符类内 \\n 转义必须拒绝预滤（rg 行模式下该 pattern 0 命中）");
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a[\\x0A]b"),
                "字符类内 \\x 转义可能编码 LF，必须拒绝预滤");
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a[\\012]b"),
                "字符类内八进制转义（\\012=LF）必须拒绝预滤");
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a[\\rb]"),
                "字符类内 \\r 转义必须拒绝预滤");
        assertTrue(RegexLiteralExtractor.mayMatchAcrossLines("a[\nb]"),
                "字符类内真实行终止字符必须拒绝预滤");

        // 对照：类内不含行终止构造的正类保持安全（不过度拒绝）
        assertFalse(RegexLiteralExtractor.mayMatchAcrossLines("a[0-9]b"));
        assertFalse(RegexLiteralExtractor.mayMatchAcrossLines("a[\\d]b"));
        assertFalse(RegexLiteralExtractor.mayMatchAcrossLines("a[\\]]b"));
    }

    /**
     * plan 2587 钉：extract 对含行终止构造的字符类不把类内容拼进 literal run——
     * 类前/类后的顶层 run 各自独立提取，类内容（\n 等）绝不出现。
     */
    @Test
    public void testPlan2587_extractDoesNotConcatenateClassContentAcrossRuns() {
        // 类内容 "\n" 不得进入任何 run
        assertEquals("bc", RegexLiteralExtractor.extract("a[\\n]bc"));
        assertEquals("a", RegexLiteralExtractor.extract("a[\\x0A]b"));
        String fromOctal = RegexLiteralExtractor.extract("ab[\\012]cd");
        assertTrue("ab".equals(fromOctal) || "cd".equals(fromOctal),
                "两侧独立 run 取最长，类内容不得拼接: " + fromOctal);
        assertFalse(RegexLiteralExtractor.extract("x[\\n]y").contains("\n"),
                "literal run 不得含类内行终止构造字符");
    }

    /**
     * plan 2587 接线钉（guide rule 23）：含字符类内行终止构造的 pattern 经 RegexSearcher
     * 必须路由到逐行路径（rg 行模式语义）——跨行内容不得产出命中（rg 行模式 0 命中对齐）。
     * 缺陷态：预滤误判单行安全 + literal 提取走候选行路径（同样 0 命中）或整文件路径
     * （跨行命中 → 分叉红）；本钉同时断言路由计数，防路径选择回归。
     */
    @Test
    public void testPlan2587_searcherRoutesClassLineTerminatorToLineScan() throws Exception {
        byte[] bytes = "xa\nyb\nab\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.lang.foreign.MemorySegment seg = java.lang.foreign.MemorySegment.ofArray(bytes);

        io.nop.rg.core.coordinator.RegexSearcher searcher =
                new io.nop.rg.core.coordinator.RegexSearcher("a[\\n]b", false);
        java.lang.reflect.Field lineScan = io.nop.rg.core.coordinator.RegexSearcher.class.getDeclaredField("lineScanCount");
        lineScan.setAccessible(true);
        java.lang.reflect.Field candidateScan = io.nop.rg.core.coordinator.RegexSearcher.class.getDeclaredField("candidateScanCount");
        candidateScan.setAccessible(true);

        assertEquals(0, searcher.findAll(seg, bytes.length).size(),
                "rg 行模式下 a[\\n]b 不可能命中（跨行内容不得产出命中）");
        assertEquals(1, lineScan.get(searcher), "必须路由到逐行路径（缺陷态走候选行路径）");
        assertEquals(0, candidateScan.get(searcher), "不得走候选行路径（含行终止构造的类不可提取可靠 literal）");
    }
}
