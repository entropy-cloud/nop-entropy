package io.nop.treesitter.query;

import io.nop.treesitter.TreeSitterException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1: the query S-expression parser — AST shapes for type patterns,
 * anonymous literals, wildcards, field qualifiers, alternations and captures,
 * plus strict fail-loud behavior for every malformed or unsupported construct.
 */
class TSQueryParserTest {

    private static Pattern single(String source) {
        Query query = TSQueryParser.parse(source);
        assertEquals(1, query.patterns().size(), "expected exactly one pattern");
        return query.patterns().get(0);
    }

    @Test
    void parsesTypePattern() {
        PatternNode root = single("(pair)").root();
        assertInstanceOf(PatternNode.Type.class, root);
        assertEquals("pair", ((PatternNode.Type) root).typeName());
        assertNull(root.capture());
        assertTrue(root.children().isEmpty());
    }

    @Test
    void parsesAnonymousLiteralPattern() {
        PatternNode root = single("(\":\")").root();
        assertInstanceOf(PatternNode.Anonymous.class, root);
        assertEquals(":", ((PatternNode.Anonymous) root).text());
    }

    @Test
    void parsesWildcardPattern() {
        PatternNode root = single("(_)").root();
        assertInstanceOf(PatternNode.Wildcard.class, root);
    }

    @Test
    void parsesFieldQualifiedChild() {
        PatternNode root = single("(pair key: (_) @k)").root();
        PatternNode.Type type = (PatternNode.Type) root;
        assertEquals("pair", type.typeName());
        assertEquals(1, type.children().size());
        ChildPattern child = type.children().get(0);
        assertEquals("key", child.fieldName());
        assertInstanceOf(PatternNode.Wildcard.class, child.node());
        assertEquals("k", child.node().capture());
    }

    @Test
    void parsesCaptureOnPatternRoot() {
        assertEquals("string", single("(string) @string").root().capture());
        assertEquals("constant.builtin", single("[ (null) (true) ] @constant.builtin").root().capture());
    }

    @Test
    void parsesCaptureOnAlternationElement() {
        PatternNode root = single("[ (null) @x (true) ]").root();
        PatternNode.Alternation alt = (PatternNode.Alternation) root;
        assertEquals(2, alt.elements().size());
        assertEquals("x", alt.elements().get(0).capture());
        assertNull(alt.elements().get(1).capture());
        assertNull(alt.capture());
    }

    @Test
    void parsesNestedAlternation() {
        PatternNode root = single("[ (null) (true) [ (false) (string) ] ]").root();
        PatternNode.Alternation alt = (PatternNode.Alternation) root;
        assertEquals(3, alt.elements().size());
        PatternNode third = alt.elements().get(2);
        assertInstanceOf(PatternNode.Alternation.class, third);
        assertEquals(2, ((PatternNode.Alternation) third).elements().size());
    }

    @Test
    void parsesAnonymousChildAndBareWildcardChild() {
        PatternNode root = single("(pair key: \"a\")").root();
        PatternNode.Type type = (PatternNode.Type) root;
        assertEquals("a", ((PatternNode.Anonymous) type.children().get(0).node()).text());

        PatternNode root2 = single("(array _)").root();
        assertEquals(1, ((PatternNode.Type) root2).children().size());
        assertInstanceOf(PatternNode.Wildcard.class, ((PatternNode.Type) root2).children().get(0).node());
    }

    @Test
    void toleratesWhitespaceAndComments() {
        Query query = TSQueryParser.parse("""
                ; a comment line
                ( string ) @s ; trailing comment

                (number) @n
                """);
        assertEquals(2, query.patterns().size());
        assertEquals("s", query.patterns().get(0).root().capture());
        assertEquals("n", query.patterns().get(1).root().capture());
    }

    @Test
    void parsesMultiplePatternsInOrder() {
        Query query = TSQueryParser.parse("(string) @s (number) @n (comment) @c");
        assertEquals(3, query.patterns().size());
        assertEquals("string", ((PatternNode.Type) query.patterns().get(0).root()).typeName());
        assertEquals("number", ((PatternNode.Type) query.patterns().get(1).root()).typeName());
    }

    @Test
    void parsesPredicates() {
        Pattern pattern = single("(string) @s (#eq? @s \"text\") (#match? @s \"[a-z]+\")");
        assertEquals(2, pattern.predicates().size());
        Predicate first = pattern.predicates().get(0);
        assertInstanceOf(Predicate.Eq.class, first);
        assertEquals("s", first.captureName());
        assertEquals("text", first.value());
        Predicate second = pattern.predicates().get(1);
        assertInstanceOf(Predicate.Match.class, second);
        assertEquals("[a-z]+", second.value());
    }

    @Test
    void parsesStringEscapesInLiteralsAndPredicates() {
        PatternNode root = single("(\"\\\"\")").root();
        assertEquals("\"", ((PatternNode.Anonymous) root).text());
        Pattern pattern = single("(string) @s (#eq? @s \"a\\nb\")");
        assertEquals("a\nb", ((Predicate.Eq) pattern.predicates().get(0)).text());
    }

    @Test
    void rejectsUnclosedParenWithOffset() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("(pair"));
        assertTrue(e.getMessage().contains("offset"), e.getMessage());
        assertTrue(e.getMessage().contains("unclosed"), e.getMessage());
    }

    @Test
    void rejectsUnclosedAlternation() {
        TreeSitterException e = assertThrows(TreeSitterException.class,
                () -> TSQueryParser.parse("[ (null) (true)"));
        assertTrue(e.getMessage().contains("unclosed"), e.getMessage());
    }

    @Test
    void rejectsEmptyPattern() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("()"));
        assertTrue(e.getMessage().contains("empty pattern"), e.getMessage());
    }

    @Test
    void rejectsEmptyAlternation() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("[]"));
        assertTrue(e.getMessage().contains("empty alternation"), e.getMessage());
    }

    @Test
    void rejectsStrayClosingParen() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("(pair))"));
        assertTrue(e.getMessage().contains("offset"), e.getMessage());
    }

    @Test
    void rejectsBareIdentifierChild() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("(pair key)"));
        assertTrue(e.getMessage().contains("unexpected identifier"), e.getMessage());
    }

    @Test
    void rejectsCaptureNameMissing() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("(string) @"));
        assertTrue(e.getMessage().contains("capture name expected"), e.getMessage());
    }

    @Test
    void rejectsUnsupportedQuantifier() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("(string)+"));
        assertTrue(e.getMessage().contains("quantifier"), e.getMessage());
        assertTrue(e.getMessage().contains("offset"), e.getMessage());
    }

    @Test
    void rejectsUnsupportedAnchor() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("(pair key: (_) .)"));
        assertTrue(e.getMessage().contains("anchor"), e.getMessage());
    }

    @Test
    void rejectsUnsupportedPredicate() {
        TreeSitterException e = assertThrows(TreeSitterException.class,
                () -> TSQueryParser.parse("(string) @s (#any-of? @s \"a\" \"b\")"));
        assertTrue(e.getMessage().contains("#any-of?"), e.getMessage());
    }

    @Test
    void rejectsPredicateWithoutCaptureArg() {
        TreeSitterException e = assertThrows(TreeSitterException.class,
                () -> TSQueryParser.parse("(string) (#eq? \"a\")"));
        assertTrue(e.getMessage().contains("capture reference"), e.getMessage());
    }

    @Test
    void rejectsUnclosedStringLiteral() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> TSQueryParser.parse("(\"abc)"));
        assertTrue(e.getMessage().contains("unclosed string literal"), e.getMessage());
    }

    @Test
    void emptySourceYieldsEmptyQuery() {
        Query query = TSQueryParser.parse("  \n; nothing here\n");
        assertTrue(query.patterns().isEmpty());
        assertEquals(List.of(), query.patterns());
    }
}