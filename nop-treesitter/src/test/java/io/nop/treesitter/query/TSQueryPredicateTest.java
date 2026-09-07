package io.nop.treesitter.query;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: predicate evaluation at match time — {@code #eq?} exact text
 * comparison and {@code #match?} full-match regex against captured node text;
 * a failing predicate suppresses the match. All queries are synthetic and run
 * over JSON parse trees through the same executor as the highlights pipeline.
 */
class TSQueryPredicateTest {

    private static final Language JSON = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");

    private static List<TSQueryMatch> run(String querySource, String source) {
        TSQuery query = TSQuery.compile(JSON, querySource);
        TSTree tree = TSParser.parse(JSON, source);
        TSQueryCursor cursor = new TSQueryCursor(query, tree, source);
        List<TSQueryMatch> matches = new ArrayList<>();
        TSQueryMatch match;
        while ((match = cursor.nextMatch()) != null) {
            matches.add(match);
        }
        return matches;
    }

    private static String text(TSQueryMatch match, String captureName, byte[] source) {
        return TSQueryCursor.nodeText(match.node(captureName), source);
    }

    @Test
    void eqTruePathKeepsMatch() {
        String source = "[42, 43]";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        List<TSQueryMatch> matches = run("(number) @n (#eq? @n \"42\")", source);
        assertEquals(1, matches.size());
        assertEquals("42", text(matches.get(0), "n", bytes));
    }

    @Test
    void eqFalsePathSuppressesMatch() {
        List<TSQueryMatch> matches = run("(number) @n (#eq? @n \"99\")", "[42]");
        assertTrue(matches.isEmpty(), "failing #eq? must suppress the structurally valid match");
    }

    @Test
    void matchTruePathKeepsMatch() {
        String source = "[\"abc\", \"ABC\"]";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        List<TSQueryMatch> matches = run("(string) @s (#match? @s \"\\\"[a-z]+\\\"\")", source);
        assertEquals(1, matches.size());
        assertEquals("\"abc\"", text(matches.get(0), "s", bytes));
    }

    @Test
    void matchFalsePathSuppressesMatch() {
        List<TSQueryMatch> matches = run("(string) @s (#match? @s \"\\\"[a-z]+\\\"\")", "[\"ABC\"]");
        assertTrue(matches.isEmpty(), "failing #match? must suppress the match");
    }

    @Test
    void matchRequiresFullTextMatch() {
        List<TSQueryMatch> matches = run("(number) @n (#match? @n \"42\")", "[42, 142, 420]");
        assertEquals(1, matches.size(), "unanchored regex must still match the entire node text");
    }

    @Test
    void matchWithRegexMetacharacters() {
        List<TSQueryMatch> matches = run("(number) @n (#match? @n \"\\\\d+(\\\\.\\\\d+)?\")", "[42, 10.1, -3]");
        assertEquals(2, matches.size(), "\\\\d+\\\\.\\\\d+ must match 42 and 10.1 but not -3");
    }

    @Test
    void predicateOnCaptureInsideAlternation() {
        List<TSQueryMatch> matches = run("[ (number) (true) ] @v (#eq? @v \"true\")", "[true, 42]");
        assertEquals(1, matches.size());
        assertEquals("true", matches.get(0).node("v").type());
    }

    @Test
    void multiplePredicatesMustAllPass() {
        List<TSQueryMatch> matches = run("(number) @n (#eq? @n \"42\") (#match? @n \"4.\")", "[42, 43]");
        assertEquals(1, matches.size());
        assertEquals("42", text(matches.get(0), "n",
                "[42, 43]".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void patternWithoutPredicateStillEmitsAllMatches() {
        List<TSQueryMatch> matches = run("(number) @n", "[1, 2, 3]");
        assertEquals(3, matches.size());
        assertEquals("number", matches.get(0).node("n").type());
    }
}