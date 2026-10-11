package io.nop.find.core.score;

import io.nop.find.core.NopFindException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FuzzyFileScorer} 三级打分单元测试（plan 2379 Phase 1）。
 */
public class FuzzyFileScorerTest {

    private static int fieldScore(String text, String query) {
        return new FuzzyFileScorer(query, true).scoreFieldNormalized(text);
    }

    // ---------- 前缀档 ----------

    @Test
    public void testPrefixTier() {
        assertEquals(0, fieldScore("abc", "abc"));
        assertEquals(2, fieldScore("abcde", "abc"));
        assertEquals(5, fieldScore("abcdefgh", "abc"));
    }

    @Test
    public void testPrefixBeatsSubstringAndSubsequence() {
        // 前缀 4 < 子串 102 < 子序列 206
        int prefix = fieldScore("findable", "find");
        int substring = fieldScore("xxfind", "find");
        int subsequence = fieldScore("f-i-n-d", "find");
        assertEquals(4, prefix);
        assertEquals(FuzzyFileScorer.SUBSTRING_BASE + 2, substring);
        assertTrue(prefix < substring, "prefix=" + prefix + " substring=" + substring);
        assertTrue(substring < subsequence, "substring=" + substring + " subsequence=" + subsequence);
    }

    // ---------- 子串档 ----------

    @Test
    public void testSubstringTier() {
        assertEquals(FuzzyFileScorer.SUBSTRING_BASE + 2, fieldScore("xxabcyy", "abc"));
    }

    @Test
    public void testSubstringReportsFirstIndex() {
        assertEquals(FuzzyFileScorer.SUBSTRING_BASE + 1, fieldScore("xabab", "ab"));
    }

    // ---------- 子序列档 ----------

    @Test
    public void testSubsequenceTier() {
        // 200 + Σ相邻间隔(1+1+1) + (len(text) - len(query)) = 200 + 3 + 3
        assertEquals(FuzzyFileScorer.SUBSEQUENCE_BASE + 6, fieldScore("f-i-n-d", "find"));
    }

    @Test
    public void testSubsequenceIsRejectedWhenOrderViolated() {
        assertEquals(FuzzyFileScorer.NO_MATCH, fieldScore("find", "fnid"));
    }

    @Test
    public void testTighterSubsequenceScoresBetter() {
        int tight = fieldScore("f-i-n-d", "find");
        int loose = fieldScore("f----------i----------n----------d", "find");
        assertEquals(206, tight);
        assertEquals(260, loose);
        assertTrue(tight < loose, "tight=" + tight + " loose=" + loose);
    }

    @Test
    public void testShorterTextWithSameSubsequenceScoresBetter() {
        int shortText = fieldScore("f-i-n-d", "find");
        int longText = fieldScore("f-i-n-d-extra-tail", "find");
        assertTrue(shortText < longText, "short=" + shortText + " long=" + longText);
    }

    // ---------- 不匹配 ----------

    @Test
    public void testNoMatchReturnsSentinel() {
        assertEquals(FuzzyFileScorer.NO_MATCH, fieldScore("hello", "xyz"));
        assertEquals(FuzzyFileScorer.NO_MATCH, fieldScore("", "abc"));
        assertEquals(FuzzyFileScorer.NO_MATCH, fieldScore(null, "abc"));
    }

    @Test
    public void testQueryLongerThanText() {
        assertEquals(FuzzyFileScorer.NO_MATCH, fieldScore("ab", "abc"));
    }

    // ---------- 大小写 ----------

    @Test
    public void testCaseInsensitiveByDefault() {
        assertEquals(fieldScore("findable", "find"),
                new FuzzyFileScorer("FIND", false)
                        .scoreFieldNormalized(FuzzyFileScorer.normalize("FiNdAbLe")));
    }

    @Test
    public void testCaseSensitiveRejectsDifferentCase() {
        assertEquals(FuzzyFileScorer.NO_MATCH, fieldScore("FiNdAbLe", "find"));
    }

    @Test
    public void testCaseSensitiveAcceptsExactCase() {
        // 前缀命中：len("FinData") - len("Fin") = 4
        assertEquals(4, fieldScore("FinData", "Fin"));
    }

    @Test
    public void testNormalizeIsIdentityWhenAlreadyLowerCase() {
        String lower = "already/lower/path";
        assertSame(lower, FuzzyFileScorer.normalize(lower));
        assertEquals("", FuzzyFileScorer.normalize(null));
        assertEquals("", FuzzyFileScorer.normalize(""));
    }

    @Test
    public void testNormalizeHandlesPartialUpperCase() {
        assertEquals("abc/def.java", FuzzyFileScorer.normalize("abc/def.Java"));
    }

    @Test
    public void testAutoNormalizeEntryMatchesPreNormalizedEntry() {
        FuzzyFileScorer scorer = new FuzzyFileScorer("SRC", false);
        assertEquals(scorer.scoreNormalized("src", "src/main.java", "/root/src/main.java"),
                scorer.score("SRC", "SRC/main.java", "/root/SRC/main.java"));
    }

    // ---------- 三路取最优 ----------

    @Test
    public void testNameFieldWinsWhenBest() {
        FuzzyFileScorer scorer = new FuzzyFileScorer("abc", true);
        assertEquals(0, scorer.scoreNormalized("abc", "xxx/abcyyy", "/root/xxx/abcyyy"));
    }

    @Test
    public void testRelativePathFieldUsedWhenNameMisses() {
        // name/absolute 均不命中 'xxx'；relativePath "xxx/abcyyy" 前缀命中 → (10-3) + 25
        FuzzyFileScorer scorer = new FuzzyFileScorer("xxx", true);
        assertEquals(FuzzyFileScorer.RELATIVE_PATH_OFFSET + 7,
                scorer.scoreNormalized("main.java", "xxx/abcyyy", "/root/abc/main.java"));
    }

    @Test
    public void testAbsolutePathFieldUsedWhenOthersMiss() {
        // name/relative 均不命中 '/root'；absolute "/root/abc/main.java" 前缀命中 → (19-5) + 300
        FuzzyFileScorer scorer = new FuzzyFileScorer("/root", true);
        assertEquals(FuzzyFileScorer.PATH_OFFSET + 14,
                scorer.scoreNormalized("main.java", "sub/main.java", "/root/abc/main.java"));
    }

    @Test
    public void testAllFieldsMissReturnsSentinel() {
        FuzzyFileScorer scorer = new FuzzyFileScorer("abc", true);
        assertEquals(FuzzyFileScorer.NO_MATCH,
                scorer.scoreNormalized("main.java", "src/main.java", "/root/src/main.java"));
    }

    @Test
    public void testOffsetOrderingIsRespected() {
        assertTrue(FuzzyFileScorer.NAME_OFFSET < FuzzyFileScorer.RELATIVE_PATH_OFFSET);
        assertTrue(FuzzyFileScorer.RELATIVE_PATH_OFFSET < FuzzyFileScorer.PATH_OFFSET);
    }

    // ---------- 构造校验 ----------

    @Test
    public void testEmptyQueryRejected() {
        assertThrows(NopFindException.class, () -> new FuzzyFileScorer("", true));
        assertThrows(NopFindException.class, () -> new FuzzyFileScorer(null, true));
    }

    @Test
    public void testScorerIsStatelessAndReusable() {
        FuzzyFileScorer scorer = new FuzzyFileScorer("find", false);
        Path path = Paths.get("/root/find/Main.java");
        int first = scorer.score("find/Main.java", "find/Main.java", path.toString());
        int second = scorer.score("find/Main.java", "find/Main.java", path.toString());
        assertEquals(first, second);
        assertEquals("find", scorer.getQuery());
        assertTrue(!scorer.isCaseSensitive());
    }
}