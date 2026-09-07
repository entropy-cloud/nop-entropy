package io.nop.treesitter.codegen;

import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1: the v15 JavaScript grammar blob — external scanner tables (symbol
 * map, states matrix, reserved word sets) decoded from the shipped blob match
 * the vendored {@code parser.c} statics, the loader exposes them, and the
 * shipped blob is byte-identical to a fresh CLI run.
 */
class JsBlobTest {

    private static final String BLOB_RESOURCE = "/grammars/javascript/tree-sitter-javascript-blob.bin";

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/src/parser.c");

    private static final Path SHIPPED_BLOB = Path.of(
            "src/main/resources/grammars/javascript/tree-sitter-javascript-blob.bin");

    private static ExtractedGrammar extractVendored() throws IOException {
        String source = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        return ParserCExtractor.extract(source);
    }

    @Test
    void loaderDecodesJsBlobCounts() {
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertEquals(15, lang.abiVersion());
        assertEquals(261, lang.symbolCount());
        assertEquals(1870, lang.stateCount());
        assertEquals(134, lang.tokenCount());
        assertEquals(8, lang.externalTokenCount());
        assertEquals(1, lang.keywordCaptureToken(), "keyword capture is sym_identifier");
        assertNotNull(lang.keywordLexerAutomaton());
        assertEquals(0, lang.scannerProgram().length, "scanner program empty until Phase 3 compiles it");
    }

    @Test
    void externalScannerTablesMatchParserC() throws IOException {
        ExtractedGrammar g = extractVendored();
        Language lang = Language.fromClasspath(BLOB_RESOURCE);

        assertArrayEquals(g.externalScannerSymbolMap, lang.externalSymbolMap());
        assertEquals(8, lang.externalSymbolMap().length);
        assertEquals(129, lang.externalSymbolMap()[0], "automatic_semicolon symbol id");
        assertEquals(130, lang.externalSymbolMap()[1], "template_chars symbol id");
        assertEquals(78, lang.externalSymbolMap()[4], "LOGICAL_OR is anon_sym_PIPE_PIPE");
        assertEquals(133, lang.externalSymbolMap()[7], "jsx_text symbol id");

        boolean[][] expectedStates = g.externalScannerStates;
        boolean[][] actualStates = lang.externalStates();
        assertEquals(10, actualStates.length, "external scanner states rows (indices 0..9)");
        assertEquals(8, actualStates[0].length);
        for (int s = 0; s < expectedStates.length; s++) {
            assertArrayEquals(expectedStates[s], actualStates[s], "external scanner states row " + s);
        }
        assertTrue(actualStates[2][3], "state 2 admits html_comment");
        assertTrue(actualStates[7][1], "state 7 admits template_chars");
        assertTrue(actualStates[9][6], "state 9 admits regex_pattern");
    }

    @Test
    void reservedWordSetsMatchParserC() throws IOException {
        ExtractedGrammar g = extractVendored();
        Language lang = Language.fromClasspath(BLOB_RESOURCE);

        assertEquals(g.maxReservedWordSetSize, 35);
        assertEquals(14, g.reservedWords.length);
        for (int s = 0; s < g.reservedWords.length; s++) {
            assertArrayEquals(g.reservedWords[s], lang.reservedWordSet(s), "reserved word set " + s);
        }
        assertArrayEquals(new int[0], lang.reservedWordSet(14), "out-of-range set id is the empty set");
        int identifier = g.keywordCaptureToken;
        assertTrue(lang.isReservedWord(1, g.reservedWords[1][0]), "state 1 reserves a keyword from set 1");
        assertTrue(!lang.isReservedWord(0, identifier), "state 0 does not reserve identifiers");
    }

    @Test
    void externalLexStateAndReservedWordSetIdPerParseState() throws IOException {
        ExtractedGrammar g = extractVendored();
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        for (int state = 0; state < g.stateCount; state++) {
            assertEquals(g.lexModes[state].externalLexState(), lang.externalLexState(state),
                    "external_lex_state@" + state);
            assertEquals(g.lexModes[state].reservedWordSetId(), lang.reservedWordSetId(state),
                    "reserved_word_set_id@" + state);
        }
        assertEquals(1, lang.externalLexState(0), "state 0 scans with external lex state 1");
        assertEquals(2, lang.externalLexState(1), "state 1 scans with external lex state 2");
        assertEquals(4, lang.externalLexState(15), "state 15 scans with external lex state 4");
    }

    @Test
    void shippedJsBlobMatchesFreshCliOutput() throws IOException {
        Path out = Files.createTempFile("ts2java-js", ".bin");
        Ts2Java.run(PARSER_C, out);
        byte[] fresh = Files.readAllBytes(out);
        byte[] shipped = Files.readAllBytes(SHIPPED_BLOB);
        Files.deleteIfExists(out);
        assertArrayEquals(shipped, fresh,
                "shipped JS blob must be byte-identical to a fresh CLI run of Ts2Java");
    }

    @Test
    void jsBlobLoadsFromClasspath() {
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertEquals("_automatic_semicolon", lang.symbolName(lang.externalSymbolMap()[0]));
        assertEquals("string_fragment", lang.symbolName(lang.externalSymbolMap()[1]));
        assertEquals("?", lang.symbolName(lang.externalSymbolMap()[2]));
        assertEquals("html_comment", lang.symbolName(lang.externalSymbolMap()[3]));
        assertEquals("jsx_text", lang.symbolName(lang.externalSymbolMap()[7]));
        assertEquals("string_fragment", lang.symbolName(130));
    }

    @Test
    void jsonAndJavaBlobsAreV3WithNoExternalScanner() {
        Language json = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        assertEquals(0, json.externalTokenCount());
        assertEquals(0, json.externalStates().length);
        assertEquals(0, json.scannerProgram().length);
        for (int state = 0; state < json.stateCount(); state++) {
            assertEquals(0, json.externalLexState(state));
            assertEquals(0, json.reservedWordSetId(state));
        }

        Language java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        assertEquals(0, java.externalTokenCount());
        assertEquals(0, java.externalStates().length);
        assertEquals(0, java.scannerProgram().length);
    }
}