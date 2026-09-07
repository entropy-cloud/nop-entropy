package io.nop.treesitter.language;

import io.nop.treesitter.codegen.BlobReader;
import io.nop.treesitter.codegen.BlobWriter;
import io.nop.treesitter.codegen.ExtractedGrammar;
import io.nop.treesitter.codegen.ParserCExtractor;
import io.nop.treesitter.codegen.Ts2Java;
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
 * Phase 2: the Java grammar blob — built end-to-end by the CLI from the
 * vendored {@code parser.c}, classpath-loadable, and decodable by both the
 * runtime loader and the independent codegen reader with the same
 * symbol/state/field/alias values the C static tables carry.
 */
class JavaBlobTest {

    private static final String BLOB_RESOURCE = "/grammars/java/tree-sitter-java-blob.bin";

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-java/src/parser.c");

    private static final Path SHIPPED_BLOB = Path.of(
            "src/main/resources/grammars/java/tree-sitter-java-blob.bin");

    @Test
    void loaderDecodesJavaBlobCounts() {
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertEquals(14, lang.abiVersion());
        assertEquals(320, lang.symbolCount());
        assertEquals(1385, lang.stateCount());
        assertEquals(406, lang.largeStateCount());
        assertEquals(138, lang.tokenCount());
        assertEquals(1, lang.aliasCount());
        assertEquals(40, lang.fieldCount());
        assertEquals(208, lang.productionIdCount());
        assertEquals(11, lang.maxAliasSequenceLength());
        assertEquals(1, lang.keywordCaptureToken(), "keyword capture is sym_identifier");
        assertNotNull(lang.keywordLexerAutomaton());
        assertEquals(194, lang.lexerAutomaton().stateCount);
        assertTrue(lang.keywordLexerAutomaton().stateCount >= 299,
                "keyword lexer has ~299 DFA states");
    }

    @Test
    void loaderMatchesParserCStaticTables() throws IOException {
        String source = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        ExtractedGrammar g = ParserCExtractor.extract(source);
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertEquals(g.stateCount, lang.stateCount());
        assertEquals(g.symbolCount, lang.symbolCount());
        assertEquals(g.tokenCount, lang.tokenCount());
        assertEquals(g.fieldCount, lang.fieldCount());
        assertEquals(g.aliasCount, lang.aliasCount());
        assertEquals(g.productionIdCount, lang.productionIdCount());
        assertEquals(g.keywordCaptureToken, lang.keywordCaptureToken());
        assertArrayEquals(g.primaryStateIds, lang.primaryStateIds());
        assertArrayEquals(java.util.Arrays.stream(g.lexModes).map(m -> m.lexState()).mapToInt(Integer::intValue).toArray(),
                lang.lexModes());
        assertArrayEquals(g.smallParseTable, lang.smallParseTable());
        assertArrayEquals(g.smallParseTableMap, lang.smallParseTableMap());
        assertArrayEquals(g.fieldNames, lang.fieldNames());
        for (int i = 0; i < g.fieldMapSlices.length; i++) {
            Language.FieldMapEntry[] actual = lang.fieldMap(i);
            assertEquals(g.fieldMapSlices[i].length(), actual.length, "field map length@" + i);
        }
        for (int i = 0; i < g.aliasSequences.length; i++) {
            assertArrayEquals(g.aliasSequences[i], lang.aliasSequences()[i], "alias row " + i);
        }
    }

    @Test
    void independentReaderDecodesShippedJavaBlob() throws IOException {
        byte[] blob = Files.readAllBytes(SHIPPED_BLOB);
        BlobReader.Decoded d = BlobReader.read(blob);
        assertEquals(2, d.header().formatVersion());
        assertEquals(320, d.header().symbolCount());
        assertEquals(1385, d.header().stateCount());
        assertEquals(40, d.header().fieldCount());
        assertEquals(1, d.header().aliasCount());
        assertEquals(208, d.header().productionIdCount());
        assertEquals(1, d.header().keywordCaptureToken());
        assertEquals("identifier", d.symbolNames()[1]);
        assertEquals("type_identifier", d.symbolNames()[320]);
    }

    @Test
    void aliasSequencesCarryTypeIdentifierRelabeling() {
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertEquals(320, lang.aliasAt(2, 0), "production 2 aliases its first child");
        assertEquals("type_identifier", lang.symbolName(lang.aliasAt(2, 0)));
        assertEquals(320, lang.aliasAt(15, 1), "production 15 aliases its second child");
        assertEquals(320, lang.aliasAt(30, 0), "production 30 aliases children 0 and 2");
        assertEquals(320, lang.aliasAt(30, 2));
        assertEquals(1, lang.aliasAt(1, 0), "production 1 aliases to sym_identifier");
        assertEquals("identifier", lang.symbolName(lang.aliasAt(1, 0)));
        assertEquals(0, lang.aliasAt(0, 0), "production 0 has no aliases");
        assertEquals(0, lang.aliasAt(17, 0), "unlisted production has no aliases");
    }

    @Test
    void keywordCaptureResolvesThroughBlob() {
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertEquals(1, lang.keywordCaptureToken());
        assertEquals("identifier", lang.symbolName(lang.keywordCaptureToken()));
        assertTrue(lang.keywordLexerAutomaton().acceptCount() > 50,
                "keyword lexer accepts the Java keyword set");
    }

    @Test
    void keywordAutomatonDecodesIdenticallyToExtractor() throws IOException {
        String source = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        ExtractedGrammar g = ParserCExtractor.extract(source);
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        Language.LexerAutomaton kw = lang.keywordLexerAutomaton();
        assertEquals(g.keywordLexer.stateCount, kw.stateCount);
        assertArrayEquals(g.keywordLexer.acceptSymbol, kw.acceptSymbol);
        assertEquals(g.keywordLexer.transitions.length, kw.transitions.length);
    }

    @Test
    void aliasSymbolsRenderAsTypeIdentifier() {
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertTrue(lang.symbolVisible(320), "alias symbol is visible");
        assertTrue(lang.symbolNamed(320), "alias symbol is named");
        assertEquals("type_identifier", lang.symbolName(320));
    }
}