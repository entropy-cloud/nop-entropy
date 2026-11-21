package io.nop.commons.path;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration pin for nop-ib's former GlobMatcher engine (analysis
 * 2026-11-22-nop-ib-nop-git-platform-reuse): segment globs with fail-loud
 * rejection of regex metacharacters, {@code **} crossing segments, and the
 * {@code a/**}-also-matches-{@code a} boundary rule (design 08 §二 semantics).
 */
class SegmentGlobPathMatcherTest {
    private final SegmentGlobPathMatcher matcher = SegmentGlobPathMatcher.instance();

    @Test
    void starDoesNotCrossSegments() {
        assertTrue(matcher.match("src/*/x.txt", "src/main/x.txt"));
        assertFalse(matcher.match("src/*/x.txt", "src/main/sub/x.txt"));
    }

    @Test
    void doubleStarCrossesSegments() {
        assertTrue(matcher.match("src/**", "src/a/b/c.java"));
        // '**' followed by '/' is swallowed: "a/**/b" also matches "a/b" itself
        assertTrue(matcher.match("a/**/b", "a/b"));
        assertTrue(matcher.match("**/*.java", "a/b/C.java"));
        assertFalse(matcher.match("**/*.java", "a/b/C.txt"));
        // migrated engine semantics (unchanged from nop-ib GlobMatcher): a
        // trailing "a/**" compiles to "a/.*" and does NOT match "a" alone
        assertFalse(matcher.match("src/**", "src"));
    }

    @Test
    void questionMarkMatchesOneNonSlashChar() {
        assertTrue(matcher.match("a?c", "abc"));
        assertFalse(matcher.match("a?c", "ac"));
        assertFalse(matcher.match("a?c", "a/c"));
    }

    @Test
    void regexMetaCharsRejectedAtCompile() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> matcher.compile("src/[ab]/*.java"));
        assertTrue(e.getMessage().contains("has no glob meaning"));
        // migrated engine semantics: '|' is NOT in the rejected set — it is
        // escaped and matched literally (same as nop-ib GlobMatcher)
        assertTrue(matcher.match("a|b", "a|b"));
        assertFalse(matcher.match("a|b", "axb"));
    }

    @Test
    void compiledPatternReusableAndBackslashNormalized() {
        Pattern p = matcher.compile("**/*.json");
        assertTrue(matcher.matches(p, "a/b/c.json"));
        assertTrue(matcher.matches(p, "a\\b\\c.json"));
        assertFalse(matcher.matches(p, "a/b/c.yaml"));
    }

    @Test
    void isPatternAndInterfaceContract() {
        assertTrue(matcher.isPattern("a/*.java"));
        assertFalse(matcher.isPattern("a/literal.java"));
        assertEquals("a/b.java", matcher.extractPathWithinPattern("a/*.java", "a/b.java"));
        assertTrue(matcher.extractUriTemplateVariables("a/*", "a/b").isEmpty());
        // combine through a recursive tail
        assertEquals("src/**/gen/X.java", matcher.combine("src/**", "/gen/X.java"));
    }

    @Test
    void matchStartApproximatesPrefixMatch() {
        assertTrue(matcher.matchStart("src/main/**", "src/main/io/x/Foo.java"));
        assertTrue(matcher.matchStart("src/main", "src/main/io/x/Foo.java"));
        assertFalse(matcher.matchStart("src/main/**", "src/test/io/x/Foo.java"));
    }
}
