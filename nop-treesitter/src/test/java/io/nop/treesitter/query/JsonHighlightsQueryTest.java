package io.nop.treesitter.query;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.cursor.TSTreeCursor;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end: the vendored upstream JSON {@code highlights.scm} runs
 * over the 7 vendored corpus fixtures plus curated snippets through the single
 * {@code TSParser.parse → TSQuery.compile → TSQueryCursor} path. Expected
 * capture sets are hand-derived from the corpus s-expressions and the highlight
 * annotations; every emitted capture is additionally cross-checked against a
 * fresh {@link TSTreeCursor} walk of the same tree (type, text and byte range
 * all reachable in the walk the s-expression flattens).
 */
class JsonHighlightsQueryTest {

    private static final Path CORPUS = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-json/test/corpus/main.txt");
    private static final Path HIGHLIGHTS = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-json/queries/highlights.scm");

    private record Section(String title, String input) {
    }

    private record ExpectedMatch(int patternIndex, String captureName, String nodeType, String text) {
    }

    private record ObservedMatch(int patternIndex, TSQueryMatch match) {
    }

    private record TestInput(String source, List<ExpectedMatch> expected) {
    }

    private static List<Section> readCorpusSections() throws IOException {
        List<String> lines = Files.readAllLines(CORPUS, StandardCharsets.UTF_8);
        List<Section> sections = new ArrayList<>();
        int i = 0;
        int n = lines.size();
        while (i < n) {
            if (!lines.get(i).startsWith("=")) {
                i++;
                continue;
            }
            String title = lines.get(i + 1);
            i += 3;
            while (i < n && lines.get(i).isEmpty()) {
                i++;
            }
            List<String> input = new ArrayList<>();
            while (i < n && !lines.get(i).matches("-{3,}")) {
                input.add(lines.get(i));
                i++;
            }
            sections.add(new Section(title, String.join("\n", input)));
            i++;
            while (i < n && !lines.get(i).startsWith("=")) {
                i++;
            }
        }
        return sections;
    }

    private static TSQuery loadHighlights(Language language) throws IOException {
        String source = Files.readString(HIGHLIGHTS, StandardCharsets.UTF_8);
        return TSQuery.compile(language, source);
    }

    private static List<ObservedMatch> runQuery(TSQuery query, TSTree tree, String source) {
        TSQueryCursor cursor = new TSQueryCursor(query, tree, source);
        List<ObservedMatch> matches = new ArrayList<>();
        TSQueryMatch match;
        while ((match = cursor.nextMatch()) != null) {
            matches.add(new ObservedMatch(match.patternIndex(), match));
        }
        return matches;
    }

    private static void assertMatches(List<ObservedMatch> actual, List<ExpectedMatch> expected,
                                      byte[] source) {
        assertEquals(expected.size(), actual.size(),
                "expected " + expected.size() + " matches, got " + actual.size() + ": " + actual);
        for (int i = 0; i < expected.size(); i++) {
            ExpectedMatch want = expected.get(i);
            ObservedMatch got = actual.get(i);
            assertEquals(want.patternIndex(), got.patternIndex(),
                    "match " + i + " pattern index (expected captures: " + want.captureName() + ")");
            assertEquals(1, got.match().captures().size(),
                    "match " + i + " capture count (pattern " + got.patternIndex() + ")");
            TSQueryMatch.Capture capture = got.match().captures().get(0);
            assertEquals(want.captureName(), capture.name(), "match " + i + " capture name");
            assertEquals(want.nodeType(), capture.node().type(), "match " + i + " node type");
            assertEquals(want.text(), textOf(capture.node(), source), "match " + i + " node text");
        }
    }

    private static String textOf(TSNode node, byte[] source) {
        return TSQueryCursor.nodeText(node, source);
    }

    /**
     * Cross-check invariant (Anti-Hollow): every emitted capture node is
     * reachable through the same TSTreeCursor walk that toSExpression flattens
     * — its type appears at the same byte position in the walk, and the walk's
     * named-node sequence equals the s-expression's node sequence.
     */
    private static void assertCapturesReachable(TSTree tree, String source, List<ObservedMatch> matches) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        List<Integer> walkStarts = new ArrayList<>();
        List<String> walkTypes = new ArrayList<>();
        List<String> walkNamedTypes = new ArrayList<>();
        TSTreeCursor walker = tree.cursor();
        do {
            TSNode node = walker.currentNode();
            walkStarts.add(node.startByte());
            walkTypes.add(node.type());
            if (node.named()) {
                walkNamedTypes.add(node.type());
            }
        } while (advancePreOrder(walker));

        List<String> sexpTypes = new ArrayList<>();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\\(\\s*([^)\\s]+)").matcher(tree.toSExpression());
        while (matcher.find()) {
            sexpTypes.add(matcher.group(1));
        }
        assertEquals(sexpTypes, walkNamedTypes, "walk named-node sequence must equal the s-expression flattening");

        for (ObservedMatch observed : matches) {
            TSNode node = observed.match().captures().get(0).node();
            int start = node.startByte();
            int end = node.endByte();
            assertTrue(start >= 0 && end <= bytes.length, "capture byte range out of bounds: " + start + ".." + end);
            boolean found = false;
            for (int i = 0; i < walkStarts.size(); i++) {
                if (walkStarts.get(i) == start && walkTypes.get(i).equals(node.type())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "captured node (" + node.type() + " at " + start + ") must appear in the cursor walk");
        }
    }

    private static boolean advancePreOrder(TSTreeCursor cursor) {
        if (cursor.gotoFirstChild()) {
            return true;
        }
        while (true) {
            if (cursor.gotoNextSibling()) {
                return true;
            }
            if (!cursor.gotoParent()) {
                return false;
            }
        }
    }

    @Test
    void vendoredHighlightsRunOverCorpusFixtures() throws IOException {
        Language language = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        TSQuery query = loadHighlights(language);
        List<Section> sections = readCorpusSections();
        assertFalse(sections.isEmpty());

        List<ExpectedMatch> arrays = List.of(
                new ExpectedMatch(2, "number", "number", "345"),
                new ExpectedMatch(2, "number", "number", "10.1"),
                new ExpectedMatch(2, "number", "number", "10"),
                new ExpectedMatch(2, "number", "number", "-10"),
                new ExpectedMatch(3, "constant.builtin", "null", "null"),
                new ExpectedMatch(3, "constant.builtin", "true", "true"),
                new ExpectedMatch(3, "constant.builtin", "false", "false"),
                new ExpectedMatch(0, "string.special.key", "string", "\"stuff\""),
                new ExpectedMatch(1, "string", "string", "\"stuff\""),
                new ExpectedMatch(1, "string", "string", "\"good\""));

        List<ExpectedMatch> strings = List.of(
                new ExpectedMatch(1, "string", "string", "\"\""),
                new ExpectedMatch(1, "string", "string", "\"abc\""),
                new ExpectedMatch(1, "string", "string", "\"def\\n\""),
                new ExpectedMatch(4, "escape", "escape_sequence", "\\n"),
                new ExpectedMatch(1, "string", "string", "\"ghi\\t\""),
                new ExpectedMatch(4, "escape", "escape_sequence", "\\t"),
                new ExpectedMatch(1, "string", "string", "\"jkl\\f\""),
                new ExpectedMatch(4, "escape", "escape_sequence", "\\f"),
                new ExpectedMatch(1, "string", "string", "\"//\""),
                new ExpectedMatch(1, "string", "string", "\"/**/\""));

        List<ExpectedMatch> topLevelNumbers = List.of(
                new ExpectedMatch(2, "number", "number", "-1"));

        List<ExpectedMatch> exponents = List.of(
                new ExpectedMatch(2, "number", "number", "1e10"),
                new ExpectedMatch(2, "number", "number", "1e+10"),
                new ExpectedMatch(2, "number", "number", "1E+10"),
                new ExpectedMatch(2, "number", "number", "1e-10"),
                new ExpectedMatch(2, "number", "number", "1.5e+10"),
                new ExpectedMatch(2, "number", "number", "-1.5E+10"));

        List<ExpectedMatch> topLevelNull = List.of(
                new ExpectedMatch(3, "constant.builtin", "null", "null"));

        List<ExpectedMatch> comments = List.of(
                new ExpectedMatch(0, "string.special.key", "string", "\"a\""),
                new ExpectedMatch(1, "string", "string", "\"a\""),
                new ExpectedMatch(2, "number", "number", "1"),
                new ExpectedMatch(5, "comment", "comment", "// we allow comments, because several"),
                new ExpectedMatch(5, "comment", "comment", "// commonly used tools allow comments in"),
                new ExpectedMatch(5, "comment", "comment", "// files with the extension `.json`"),
                new ExpectedMatch(0, "string.special.key", "string", "\"b\""),
                new ExpectedMatch(1, "string", "string", "\"b\""),
                new ExpectedMatch(1, "string", "string", "\"2\""),
                new ExpectedMatch(5, "comment", "comment", "/*\n   * Block comments are also ok\n   */"),
                new ExpectedMatch(0, "string.special.key", "string", "\"c\""),
                new ExpectedMatch(1, "string", "string", "\"c\""),
                new ExpectedMatch(2, "number", "number", "3"));

        List<ExpectedMatch> topLevelObjects = List.of();

        List<List<ExpectedMatch>> expectations = List.of(arrays, strings, topLevelNumbers, exponents,
                topLevelNull, comments, topLevelObjects);
        assertEquals(expectations.size(), sections.size());

        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            TSTree tree = TSParser.parse(language, section.input());
            byte[] bytes = section.input().getBytes(StandardCharsets.UTF_8);
            List<ObservedMatch> matches = runQuery(query, tree, section.input());
            assertMatches(matches, expectations.get(i), bytes);
            assertCapturesReachable(tree, section.input(), matches);
        }
    }

    @Test
    void vendoredHighlightsRunOverCuratedSnippets() throws IOException {
        Language language = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        TSQuery query = loadHighlights(language);

        String nested = "{\"a\": {\"b\": [1, true]}}";
        List<ExpectedMatch> nestedExpected = List.of(
                new ExpectedMatch(0, "string.special.key", "string", "\"a\""),
                new ExpectedMatch(1, "string", "string", "\"a\""),
                new ExpectedMatch(0, "string.special.key", "string", "\"b\""),
                new ExpectedMatch(1, "string", "string", "\"b\""),
                new ExpectedMatch(2, "number", "number", "1"),
                new ExpectedMatch(3, "constant.builtin", "true", "true"));

        String literals = "[null, true, false]";
        List<ExpectedMatch> literalsExpected = List.of(
                new ExpectedMatch(3, "constant.builtin", "null", "null"),
                new ExpectedMatch(3, "constant.builtin", "true", "true"),
                new ExpectedMatch(3, "constant.builtin", "false", "false"));

        String escapes = "\"esc\\t\\u0041\\n\"";
        List<ExpectedMatch> escapesExpected = List.of(
                new ExpectedMatch(1, "string", "string", "\"esc\\t\\u0041\\n\""),
                new ExpectedMatch(4, "escape", "escape_sequence", "\\t"),
                new ExpectedMatch(4, "escape", "escape_sequence", "\\u"),
                new ExpectedMatch(4, "escape", "escape_sequence", "\\n"));
        // note: the vendored grammar's escape_sequence is backslash + one char
        // (the "b|f|n|r|t|u" class), so the unicode escape lexes as backslash-u
        // plus string_content "0041"

        String loneComment = "// only a comment\n";
        List<ExpectedMatch> loneCommentExpected = List.of(
                new ExpectedMatch(5, "comment", "comment", "// only a comment"));

        String trailing = "{ \"key\": \"value\" } // trailing\n";
        List<ExpectedMatch> trailingExpected = List.of(
                new ExpectedMatch(0, "string.special.key", "string", "\"key\""),
                new ExpectedMatch(1, "string", "string", "\"key\""),
                new ExpectedMatch(1, "string", "string", "\"value\""),
                new ExpectedMatch(5, "comment", "comment", "// trailing"));

        String exponentMix = "[ 1e10, -2.5E-3, 42 ]";
        List<ExpectedMatch> exponentMixExpected = List.of(
                new ExpectedMatch(2, "number", "number", "1e10"),
                new ExpectedMatch(2, "number", "number", "-2.5E-3"),
                new ExpectedMatch(2, "number", "number", "42"));

        List<TestInput> snippets = List.of(
                new TestInput(nested, nestedExpected),
                new TestInput(literals, literalsExpected),
                new TestInput(escapes, escapesExpected),
                new TestInput(loneComment, loneCommentExpected),
                new TestInput(trailing, trailingExpected),
                new TestInput(exponentMix, exponentMixExpected));

        for (TestInput snippet : snippets) {
            TSTree tree = TSParser.parse(language, snippet.source());
            byte[] bytes = snippet.source().getBytes(StandardCharsets.UTF_8);
            List<ObservedMatch> matches = runQuery(query, tree, snippet.source());
            assertMatches(matches, snippet.expected(), bytes);
            assertCapturesReachable(tree, snippet.source(), matches);
        }
    }

    @Test
    void keyPatternCapturesOnlyKeyChildren() throws IOException {
        Language language = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        TSQuery query = TSQuery.compile(language, "(pair key: (_) @k)");
        String source = "{\"a\": {\"b\": \"c\"}}";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        TSTree tree = TSParser.parse(language, source);
        List<ObservedMatch> matches = runQuery(query, tree, source);
        assertEquals(2, matches.size());
        assertEquals("\"a\"", textOf(matches.get(0).match().node("k"), bytes));
        assertEquals("\"b\"", textOf(matches.get(1).match().node("k"), bytes));
        for (ObservedMatch match : matches) {
            assertEquals("string", match.match().node("k").type());
            assertNotNull(match.match().node("k"));
        }
    }
}