package io.nop.treesitter.parser;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Error-recovery acceptance on JSON (roadmap item 11 phase 2): every expected
 * tree below was produced by the upstream C runtime itself — an oracle binary
 * built from the reference {@code lib/src/lib.c} plus the vendored
 * {@code tree-sitter-json/src/parser.c} ({@code _tmp/ts-oracle/}, run
 * 2026-09-10) — and is rendered in the {@code ts_node_string} flat form with
 * field-name prefixes stripped to match the vendored corpus conventions.
 */
class JsonErrorRecoveryTest {

    private static final Language LANGUAGE = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");

    private static String parseFlat(String source) {
        return TSParser.parse(LANGUAGE, source).toSexpString(false);
    }

    @Test
    void unclosedArrayInsertsMissingCloseBracket() {
        assertEquals("(document (array (number) (number) (MISSING \"]\")))", parseFlat("[1, 2"));
    }

    @Test
    void loneOpenBraceRecoversToDocumentLevelError() {
        assertEquals("(document (ERROR))", parseFlat("{"));
        assertEquals("(document (ERROR))", parseFlat("["));
    }

    @Test
    void midTokenGarbageBecomesUnexpectedLeaf() {
        assertEquals("(document (array (true) (ERROR (UNEXPECTED 'x'))))", parseFlat("[truex]"));
    }

    @Test
    void unclosedObjectInsertsMissingCloseBraceAfterPair() {
        assertEquals("(document (object (pair (string (string_content)) (number)) (MISSING \"}\")))",
                parseFlat("{\"a\":1"));
    }

    @Test
    void trailingCommaInsertsMissingValue() {
        assertEquals("(document (array (number) (number) (MISSING number)))", parseFlat("[1, 2,]"));
    }

    @Test
    void doubleCommaProducesEmptyErrorBetweenValues() {
        assertEquals("(document (array (number) (ERROR) (number)))", parseFlat("[1,, 2]"));
    }

    @Test
    void unclosedStringInsertsMissingQuote() {
        assertEquals("(document (string (string_content) (MISSING \"\"\")))", parseFlat("\"abc"));
    }

    @Test
    void truncatedObjectValueWrapsStringInError() {
        assertEquals("(document (array (number) (ERROR (string (string_content)))))",
                parseFlat("[1, {\"k\": ]"));
    }

    @Test
    void missingValueInPairInsertsMissingNumber() {
        assertEquals("(document (array (object (pair (string (string_content)) (MISSING number)))))",
                parseFlat("[{\"a\":}]"));
    }

    @Test
    void unknownKeywordAtTopLevelSkipsAllCharacters() {
        assertEquals("(document (ERROR (UNEXPECTED '\\0')))", parseFlat("nul"));
    }

    @Test
    void garbageCharacterInsideArrayIsWrappedInError() {
        assertEquals("(document (array (ERROR (UNEXPECTED '#'))))", parseFlat("[#]"));
    }

    @Test
    void missingSeparatorWrapsSecondValueInError() {
        assertEquals("(document (array (ERROR (number)) (number)))", parseFlat("[1 2]"));
    }

    @Test
    void commaAfterBraceProducesEmptyError() {
        assertEquals("(document (object (ERROR)))", parseFlat("{,}"));
        assertEquals("(document (array (ERROR)))", parseFlat("[}]"));
    }

    /**
     * Multi-round recovery (previously adjudicated as divergent): after the
     * absolute-padding fix (item 11 follow-up, 2026-09-10) the surviving trees
     * match the C oracle byte-for-byte on these interleaved-round inputs.
     */
    @Test
    void multiRoundRecoveryMatchesTheCOracleByteExact() {
        assertEquals("(document (object (ERROR (string (string_content)) (number))))",
                parseFlat("{\"a\" 1}"));
        assertEquals("(document (ERROR (ERROR (number)) (number)))", parseFlat("[1 2,"));
    }

    @Test
    void extraTokenAfterCompleteValueIsSkipped() {
        assertEquals("(document (string (string_content)) (ERROR (UNEXPECTED 'd')))", parseFlat("\"abc\"def"));
        assertEquals("(document (object (pair (string (string_content)) (number))) (ERROR))",
                parseFlat("{\"a\":1}}"));
    }
}
