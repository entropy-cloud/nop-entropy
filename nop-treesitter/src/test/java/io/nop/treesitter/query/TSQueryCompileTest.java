package io.nop.treesitter.query;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2: query compilation — AST to matcher IR against the language's symbol
 * and field tables, capture-name interning, and compile-time validation of
 * every name the query references.
 */
class TSQueryCompileTest {

    private static final Path HIGHLIGHTS = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-json/queries/highlights.scm");

    private static Language json() {
        return Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
    }

    private static TSQuery compile(String source) {
        return TSQuery.compile(json(), source);
    }

    @Test
    void compilesTypePatternWithCapture() {
        TSQuery query = compile("(string) @string");
        assertEquals(1, query.patternCount());
        assertEquals(List.of("string"), query.captureNames());
        TSQuery.TypeMatcher root = assertInstanceOf(TSQuery.TypeMatcher.class, query.pattern(0).root());
        assertEquals(json().symbolId("string", true), root.symbolId());
        assertEquals(0, root.captureId());
        assertTrue(root.children().isEmpty());
    }

    @Test
    void compilesWildcardPattern() {
        TSQuery query = compile("(_) @any");
        TSQuery.WildcardMatcher root = assertInstanceOf(TSQuery.WildcardMatcher.class, query.pattern(0).root());
        assertEquals(0, root.captureId());
        assertTrue(root.children().isEmpty());
    }

    @Test
    void compilesAnonymousTokenPattern() {
        TSQuery query = compile("(\":\")");
        TSQuery.AnonymousMatcher root = assertInstanceOf(TSQuery.AnonymousMatcher.class, query.pattern(0).root());
        assertEquals(json().symbolId(":", false), root.symbolId());
        assertEquals(-1, root.captureId());
    }

    @Test
    void compilesFieldQualifiedWildcardChild() {
        TSQuery query = compile("(pair key: (_) @k)");
        TSQuery.TypeMatcher root = assertInstanceOf(TSQuery.TypeMatcher.class, query.pattern(0).root());
        assertEquals(json().symbolId("pair", true), root.symbolId());
        assertEquals(1, root.children().size());
        TSQuery.ChildMatcher child = root.children().get(0);
        assertEquals(json().fieldId("key"), child.fieldId());
        TSQuery.WildcardMatcher wildcard = assertInstanceOf(TSQuery.WildcardMatcher.class, child.matcher());
        assertEquals(0, wildcard.captureId());
    }

    @Test
    void compilesAlternationAndFlattensNestedCapturelessGroups() {
        TSQuery query = compile("[ (null) [ (true) (false) ] ] @constant.builtin");
        TSQuery.AlternationMatcher root = assertInstanceOf(TSQuery.AlternationMatcher.class,
                query.pattern(0).root());
        assertEquals(3, root.elements().size(), "nested captureless alternation is flattened");
        assertEquals(json().symbolId("null", true), ((TSQuery.TypeMatcher) root.elements().get(0)).symbolId());
        assertEquals(json().symbolId("true", true), ((TSQuery.TypeMatcher) root.elements().get(1)).symbolId());
        assertEquals(json().symbolId("false", true), ((TSQuery.TypeMatcher) root.elements().get(2)).symbolId());
        assertEquals(0, root.captureId());
    }

    @Test
    void keepsCapturedNestedAlternationNested() {
        TSQuery query = compile("[ (null) [ (true) (false) ] @inner ]");
        TSQuery.AlternationMatcher root = assertInstanceOf(TSQuery.AlternationMatcher.class,
                query.pattern(0).root());
        assertEquals(2, root.elements().size());
        TSQuery.AlternationMatcher inner = assertInstanceOf(TSQuery.AlternationMatcher.class,
                root.elements().get(1));
        assertEquals(0, inner.captureId(), "captured inner alternation keeps its capture slot");
        assertEquals(2, inner.elements().size());
    }

    @Test
    void captureNamesAreInternedAcrossPatterns() {
        TSQuery query = compile("(string) @s (number) @s");
        assertEquals(List.of("s"), query.captureNames(), "duplicate capture names intern to one id");
        assertEquals(0, ((TSQuery.TypeMatcher) query.pattern(0).root()).captureId());
        assertEquals(0, ((TSQuery.TypeMatcher) query.pattern(1).root()).captureId());
    }

    @Test
    void unknownNodeTypeFailsLoud() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> compile("(widget) @x"));
        assertTrue(e.getMessage().contains("widget"), e.getMessage());
    }

    @Test
    void hiddenSymbolCannotBeReferenced() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> compile("(document_repeat1)"));
        assertTrue(e.getMessage().contains("document_repeat1"), e.getMessage());
    }

    @Test
    void unknownAnonymousTokenFailsLoud() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> compile("(\"@@\")"));
        assertTrue(e.getMessage().contains("@@"), e.getMessage());
    }

    @Test
    void unknownFieldFailsLoud() {
        TreeSitterException e = assertThrows(TreeSitterException.class, () -> compile("(pair nope: (_))"));
        assertTrue(e.getMessage().contains("nope"), e.getMessage());
    }

    @Test
    void invalidRegexFailsLoud() {
        TreeSitterException e = assertThrows(TreeSitterException.class,
                () -> compile("(string) @s (#match? @s \"(\")"));
        assertTrue(e.getMessage().contains("invalid regex"), e.getMessage());
    }

    @Test
    void predicateCaptureMustExistInPattern() {
        TreeSitterException e = assertThrows(TreeSitterException.class,
                () -> compile("(string) (#eq? @x \"y\")"));
        assertTrue(e.getMessage().contains("unknown capture '@x'"), e.getMessage());
    }

    @Test
    void unsupportedPredicateFailsAtCompile() {
        TreeSitterException e = assertThrows(TreeSitterException.class,
                () -> compile("(string) @s (#any-of? @s \"a\" \"b\")"));
        assertTrue(e.getMessage().contains("#any-of?"), e.getMessage());
    }

    @Test
    void vendoredJsonHighlightsCompilesToSixPatterns() throws IOException {
        String source = Files.readString(HIGHLIGHTS, StandardCharsets.UTF_8);
        TSQuery query = compile(source);
        assertEquals(6, query.patternCount());
        assertEquals(List.of("string.special.key", "string", "number", "constant.builtin",
                "escape", "comment"), query.captureNames());

        TSQuery.Pattern pair = query.pattern(0);
        TSQuery.TypeMatcher pairRoot = assertInstanceOf(TSQuery.TypeMatcher.class, pair.root());
        assertEquals(json().symbolId("pair", true), pairRoot.symbolId());
        assertEquals(1, pairRoot.children().size());
        assertEquals(json().fieldId("key"), pairRoot.children().get(0).fieldId());
        assertEquals(0, ((TSQuery.WildcardMatcher) pairRoot.children().get(0).matcher()).captureId());

        assertInstanceOf(TSQuery.TypeMatcher.class, query.pattern(1).root());
        assertEquals(json().symbolId("string", true), ((TSQuery.TypeMatcher) query.pattern(1).root()).symbolId());

        assertEquals(json().symbolId("number", true), ((TSQuery.TypeMatcher) query.pattern(2).root()).symbolId());

        TSQuery.AlternationMatcher constants = assertInstanceOf(TSQuery.AlternationMatcher.class,
                query.pattern(3).root());
        assertEquals(3, constants.elements().size());
        assertEquals(List.of("null", "true", "false"), constants.elements().stream()
                .map(m -> json().symbolName(((TSQuery.TypeMatcher) m).symbolId())).toList());

        assertEquals(json().symbolId("escape_sequence", true),
                ((TSQuery.TypeMatcher) query.pattern(4).root()).symbolId());
        assertEquals(json().symbolId("comment", true),
                ((TSQuery.TypeMatcher) query.pattern(5).root()).symbolId());
    }
}