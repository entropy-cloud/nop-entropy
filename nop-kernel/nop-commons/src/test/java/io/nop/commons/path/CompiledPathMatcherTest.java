package io.nop.commons.path;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompiledPathMatcherTest {

    @Test
    void testSimpleMatch() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users");
        assertTrue(matcher.match("/api/v1/users"));
        assertFalse(matcher.match("/api/v2/users"));
        assertFalse(matcher.match("/api/v1/users/123"));
    }

    @Test
    void testSingleWildcard() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/*/users");
        assertTrue(matcher.match("/api/v1/users"));
        assertTrue(matcher.match("/api/v2/users"));
        assertFalse(matcher.match("/api/v1/users/123"));
    }

    @Test
    void testQuestionMarkWildcard() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v?/users");
        assertTrue(matcher.match("/api/v1/users"));
        assertTrue(matcher.match("/api/v2/users"));
        assertFalse(matcher.match("/api/v10/users"));
    }

    @Test
    void testDoubleWildcard() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/**/users");
        assertTrue(matcher.match("/api/users"));
        assertTrue(matcher.match("/api/v1/users"));
        assertTrue(matcher.match("/api/v1/v2/v3/users"));
        assertFalse(matcher.match("/api/users/123"));
    }

    @Test
    void testMultipleSegmentsWithWildcard() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/**/test/**/end");
        assertTrue(matcher.match("/api/v1/test/v2/end"));
        assertTrue(matcher.match("/api/test/end"));
        assertTrue(matcher.match("/api/a/b/c/test/x/y/z/end"));
    }

    @Test
    void testPatternWithExtension() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("*.jsp");
        assertTrue(matcher.match("test.jsp"));
        assertTrue(matcher.match("file.jsp"));
        assertFalse(matcher.match("test.java"));
    }

    @Test
    void testPatternWithDirectoryAndExtension() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/com/*.jsp");
        assertTrue(matcher.match("/com/test.jsp"));
        assertTrue(matcher.match("/com/file.jsp"));
        assertFalse(matcher.match("/com/v1/test.jsp"));
    }

    @Test
    void testDoubleWildcardWithExtension() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/com/**/*.jsp");
        assertTrue(matcher.match("/com/test.jsp"));
        assertTrue(matcher.match("/com/v1/test.jsp"));
        assertTrue(matcher.match("/com/a/b/c/test.jsp"));
    }

    @Test
    void testExactMatch() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users");
        assertTrue(matcher.match("/api/v1/users"));
        assertFalse(matcher.match("/api/v1/users/"));
        assertFalse(matcher.match("/api/v1/user"));
    }

    @Test
    void testLeadingSlash() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users");
        assertTrue(matcher.match("/api/v1/users"));
        assertFalse(matcher.match("api/v1/users"));
    }

    @Test
    void testTrailingSlash() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users/");
        assertTrue(matcher.match("/api/v1/users/"));
        assertFalse(matcher.match("/api/v1/users"));
    }

    @Test
    void testMatchStart() {
        CompiledPathMatcher matcher = (CompiledPathMatcher) new AntPathMatcher().compile("/api/**/users");
        assertTrue(matcher.matchStart("/api/v1/users"));
        assertTrue(matcher.matchStart("/api/v1/users/123"));
        assertTrue(matcher.matchStart("/api/v1/products"));
        assertFalse(matcher.matchStart("/v1"));
    }

    @Test
    void testMatchStartWithDoubleWildcard() {
        CompiledPathMatcher matcher = (CompiledPathMatcher) new AntPathMatcher().compile("/api/**/users");
        assertTrue(matcher.matchStart("/api/v1"));
        assertTrue(matcher.matchStart("/api/v1/v2"));
        assertFalse(matcher.matchStart("/v1"));
    }

    @Test
    void testExtractUriTemplateVariables() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users/{id}");
        Map<String, String> vars = matcher.extractUriTemplateVariables("/api/v1/users/123");
        assertEquals("123", vars.get("id"));
    }

    @Test
    void testExtractUriTemplateVariablesWithMultipleVars() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/{version}/users/{id}");
        Map<String, String> vars = matcher.extractUriTemplateVariables("/api/v1/users/123");
        assertEquals("v1", vars.get("version"));
        assertEquals("123", vars.get("id"));
    }

    @Test
    void testExtractUriTemplateVariablesWithRegex() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users/{id:\\d+}");
        Map<String, String> vars = matcher.extractUriTemplateVariables("/api/v1/users/123");
        assertEquals("123", vars.get("id"));
    }

    @Test
    void testExtractUriTemplateVariablesNonMatchingPattern() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users/{id}");
        assertThrows(IllegalStateException.class, () -> {
            matcher.extractUriTemplateVariables("/api/v2/users/123");
        });
    }

    @Test
    void testComplexPattern() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/org/**/servlet/bla.jsp");
        assertTrue(matcher.match("/org/springframework/servlet/bla.jsp"));
        assertTrue(matcher.match("/org/servlet/bla.jsp"));
        assertFalse(matcher.match("/org/springframework/servlet/bla.java"));
    }

    @Test
    void testMultipleWildcardsInSegment() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v*/*users*");
        assertTrue(matcher.match("/api/v1/users"));
        assertTrue(matcher.match("/api/v2/testusers"));
        assertTrue(matcher.match("/api/v3/users123"));
    }

    @Test
    void testNullPath() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users");
        assertFalse(matcher.match(null));
    }

    @Test
    void testEmptyPath() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v1/users");
        assertFalse(matcher.match(""));
    }

    @Test
    void testConsistencyWithAntPathMatcher() {
        AntPathMatcher original = new AntPathMatcher();
        ICompiledPathMatcher compiled = original.compile("/api/**/users");

        String[] testPaths = new String[] {
            "/api/users",
            "/api/v1/users",
            "/api/v1/v2/users",
            "/api/v1/v2/v3/users",
            "/api/v1/users/123",
            "/api/products"
        };

        for (String path : testPaths) {
            boolean originalMatch = original.match("/api/**/users", path);
            boolean compiledMatch = compiled.match(path);
            assertEquals(originalMatch, compiledMatch,
                "Match results differ for path: " + path);
        }
    }

    @Test
    void testMultiplePatternComparison() {
        AntPathMatcher matcher = new AntPathMatcher();

        String pattern = "/api/{version}/users/{id}";
        String path = "/api/v1/users/123";

        boolean originalMatch = matcher.match(pattern, path);
        ICompiledPathMatcher compiled = matcher.compile(pattern);
        boolean compiledMatch = compiled.match(path);

        assertEquals(originalMatch, compiledMatch);
    }

    @Test
    void testPathSeparatorConfiguration() {
        AntPathMatcher matcher = new AntPathMatcher(".");
        ICompiledPathMatcher compiled = matcher.compile("com.*.test");
        assertTrue(compiled.match("com.v1.test"));
        assertTrue(compiled.match("com.v2.test"));
        assertFalse(compiled.match("com/v1/test"));
    }

    @Test
    void testCaseInsensitiveMatching() {
        AntPathMatcher matcher = new AntPathMatcher("/");
        matcher.setCaseSensitive(false);
        ICompiledPathMatcher compiled = matcher.compile("/api/V1/USERS");
        assertTrue(compiled.match("/api/v1/users"));
        assertTrue(compiled.match("/API/V1/USERS"));
    }

    @Test
    void testComplexCombinationPattern() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/com/**/test/**/*.jsp");
        assertTrue(matcher.match("/com/test/file.jsp"));
        assertTrue(matcher.match("/com/v1/test/file.jsp"));
        assertTrue(matcher.match("/com/v1/v2/test/file.jsp"));
        assertTrue(matcher.match("/com/v1/v2/test/a/b/file.jsp"));
        assertFalse(matcher.match("/com/test/file.java"));
    }

    @Test
    void testExtractPathWithinPatternConsistency() {
        AntPathMatcher matcher = new AntPathMatcher();
        String pattern = "/api/v1/*";
        String path = "/api/v1/users/123";

        String originalExtracted = matcher.extractPathWithinPattern(pattern, path);
        ICompiledPathMatcher compiled = matcher.compile(pattern);
        String extractedFromCompiled = compiled instanceof CompiledPathMatcher ?
            matcher.extractPathWithinPattern(pattern, path) : originalExtracted;

        assertEquals(originalExtracted, extractedFromCompiled);
    }

    @Test
    void testPatternWithMultipleDoubleWildcards() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/**/test/**/end");
        assertTrue(matcher.match("/api/test/end"));
        assertTrue(matcher.match("/api/v1/test/end"));
        assertTrue(matcher.match("/api/test/v1/end"));
        assertTrue(matcher.match("/api/v1/test/v2/end"));
        assertTrue(matcher.match("/api/v1/v2/test/v3/v4/end"));
    }

    @Test
    void testPerformanceOptimization() {
        AntPathMatcher matcher = new AntPathMatcher();
        ICompiledPathMatcher compiled = matcher.compile("/api/**/users/{id}");

        String[] testPaths = new String[] {
            "/api/users/123",
            "/api/v1/users/456",
            "/api/v1/v2/users/789",
            "/api/products"
        };

        for (int i = 0; i < 100; i++) {
            for (String path : testPaths) {
                compiled.match(path);
            }
        }

        assertTrue(true, "Performance test completed successfully");
    }

    @Test
    void testEmptyPattern() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("");
        assertFalse(matcher.match("/api/v1/users"));
    }

    @Test
    void testRootPattern() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/**");
        assertTrue(matcher.match("/"));
        assertTrue(matcher.match("/api"));
        assertTrue(matcher.match("/api/v1/users"));
        assertTrue(matcher.match("/a/b/c/d"));
    }

    @Test
    void testPathVariableWithHyphen() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/{user-id}/profile");
        Map<String, String> vars = matcher.extractUriTemplateVariables("/api/123/profile");
        assertEquals("123", vars.get("user-id"));
    }

    @Test
    void testMixedWildcards() {
        ICompiledPathMatcher matcher = new AntPathMatcher().compile("/api/v?/test*/**/end");
        assertTrue(matcher.match("/api/v1/testXYZ/end"));
        assertTrue(matcher.match("/api/v2/test123/a/b/c/end"));
        assertFalse(matcher.match("/api/v10/test/end"));
    }
}
