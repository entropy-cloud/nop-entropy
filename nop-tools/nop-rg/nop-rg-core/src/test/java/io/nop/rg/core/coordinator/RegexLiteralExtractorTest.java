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
}
