package io.nop.treesitter.codegen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: binary blob encoding — writer/reader round-trip, determinism, the
 * shipped classpath blob, and out-of-range writer protection.
 */
class BlobRoundTripTest {

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-json/src/parser.c");

    private static ExtractedGrammar extractVendored() throws IOException {
        String source = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        return ParserCExtractor.extract(source);
    }

    @Test
    void roundTripDecodesShippedBlobAgainstParserC() throws IOException {
        byte[] blob = readShippedBlob();
        BlobReader.Decoded d = BlobReader.read(blob);

        assertEquals('T', blob[0]);
        assertEquals('S', blob[1]);
        assertEquals('J', blob[2]);
        assertEquals('B', blob[3]);
        assertEquals(1, d.header().formatVersion());
        assertEquals(14, d.header().abiVersion());
        assertEquals(25, d.header().symbolCount());
        assertEquals(32, d.header().stateCount());
        assertEquals(7, d.header().largeStateCount());
        assertEquals(15, d.header().tokenCount());
        assertEquals(32, d.header().lexModeCount());
        assertEquals(0, d.header().keywordLexModeCount());

        ExtractedGrammar g = extractVendored();
        assertArrayEquals(g.symbolNames, d.symbolNames());
        assertEquals(g.primaryStateIds.length, d.primaryStateIds().length);
        assertEquals(g.lexModes.length, d.lexModes().length);
        assertEquals(g.parseActions.length, d.parseActions().length);
        for (int i = 0; i < g.parseActions.length; i++) {
            assertEquals(g.parseActions[i].index(), d.parseActions()[i].index());
            assertEquals(g.parseActions[i].count(), d.parseActions()[i].count());
            assertEquals(g.parseActions[i].reusable(), d.parseActions()[i].reusable());
        }
        assertEquals(g.smallParseTable.length, d.smallParseTable().length);
        assertEquals(g.smallParseTableMap.length, d.smallParseTableMap().length);
    }

    @Test
    void roundTripSymbolMetadataMatchesParserC() throws IOException {
        byte[] blob = writeBlob();
        BlobReader.Decoded d = BlobReader.read(blob);
        ExtractedGrammar g = extractVendored();
        for (int i = 0; i < g.symbolMetadata.length; i++) {
            int flags = d.symbolFlags()[i];
            assertEquals(g.symbolMetadata[i].visible(), (flags & 0x01) != 0, "visible@" + i);
            assertEquals(g.symbolMetadata[i].named(), (flags & 0x02) != 0, "named@" + i);
            assertEquals(g.symbolMetadata[i].supertype(), (flags & 0x04) != 0, "supertype@" + i);
        }
    }

    @Test
    void roundTripActionPayloadsMatchParserC() throws IOException {
        byte[] blob = writeBlob();
        BlobReader.Decoded d = BlobReader.read(blob);
        ExtractedGrammar g = extractVendored();
        for (int i = 0; i < g.parseActions.length; i++) {
            ExtractedGrammar.ParseAction[] expected = g.parseActions[i].actions();
            ExtractedGrammar.ParseAction[] actual = d.parseActions()[i].actions();
            assertEquals(expected.length, actual.length, "action count@group " + g.parseActions[i].index());
            for (int j = 0; j < expected.length; j++) {
                assertEquals(expected[j].type(), actual[j].type(), "type@" + i + "/" + j);
                assertEquals(expected[j].state(), actual[j].state(), "state@" + i + "/" + j);
                assertEquals(expected[j].symbol(), actual[j].symbol(), "symbol@" + i + "/" + j);
                assertEquals(expected[j].childCount(), actual[j].childCount(), "childCount@" + i + "/" + j);
                assertEquals(expected[j].dynamicPrecedence(), actual[j].dynamicPrecedence(), "dynPrec@" + i + "/" + j);
                assertEquals(expected[j].productionId(), actual[j].productionId(), "prodId@" + i + "/" + j);
                assertEquals(expected[j].extra(), actual[j].extra(), "extra@" + i + "/" + j);
                assertEquals(expected[j].repetition(), actual[j].repetition(), "repetition@" + i + "/" + j);
            }
        }
    }

    @Test
    void determinismTwoRunsYieldIdenticalBytes() throws IOException {
        byte[] first = writeBlob();
        byte[] second = writeBlob();
        assertArrayEquals(first, second);
    }

    @Test
    void shippedBlobMatchesFreshCliOutput() throws IOException {
        byte[] shipped = readShippedBlob();
        byte[] fresh = writeBlob();
        assertArrayEquals(fresh, shipped,
                "shipped blob must be byte-identical to a fresh CLI run of Ts2Java");
    }

    @Test
    void writerThrowsOnOutOfRangeValues() throws IOException {
        ExtractedGrammar g = new ExtractedGrammar();
        g.languageVersion = 14;
        g.symbolCount = 1;
        g.stateCount = 1;
        g.largeStateCount = 1;
        g.tokenCount = 1;
        g.symbolNames = new String[]{"x".repeat(300)};
        g.symbolMetadata = new ExtractedGrammar.SymbolMeta[]{new ExtractedGrammar.SymbolMeta(true, false, false)};
        g.primaryStateIds = new int[]{0};
        g.parseTable = new int[][]{{0}};
        g.smallParseTable = new int[0];
        g.smallParseTableMap = new int[0];
        g.lexModes = new ExtractedGrammar.LexMode[]{new ExtractedGrammar.LexMode(0, 0, 0)};
        g.keywordLexModes = new ExtractedGrammar.LexMode[0];
        g.parseActions = new ExtractedGrammar.ParseActionGroup[0];
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> BlobWriter.write(g));
        assertTrue(ex.getMessage().contains("symbol name length"), ex.getMessage());
    }

    @Test
    void readerRejectsBadMagic() throws IOException {
        byte[] blob = writeBlob();
        byte[] bad = Arrays.copyOf(blob, blob.length);
        bad[0] = 'X';
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> BlobReader.read(bad));
        assertTrue(ex.getMessage().contains("magic"), ex.getMessage());
    }

    @Test
    void readerRejectsTruncatedBlob() throws IOException {
        byte[] blob = writeBlob();
        byte[] truncated = Arrays.copyOf(blob, 32);
        assertThrows(IllegalStateException.class, () -> BlobReader.read(truncated));
    }

    @Test
    void readerRejectsTrailingBytes() throws IOException {
        byte[] blob = writeBlob();
        byte[] padded = Arrays.copyOf(blob, blob.length + 4);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> BlobReader.read(padded));
        assertTrue(ex.getMessage().contains("trailing"), ex.getMessage());
    }

    @Test
    void shippedBlobIsLoadableFromClasspath() throws IOException {
        try (InputStream in = BlobRoundTripTest.class.getResourceAsStream(
                "/grammars/json/tree-sitter-json-blob.bin")) {
            assertNotNull(in, "shipped blob missing from classpath");
            byte[] blob = in.readAllBytes();
            assertTrue(blob.length > 64, "shipped blob too small");
            BlobReader.read(blob);
        }
    }

    private static byte[] writeBlob() throws IOException {
        return BlobWriter.write(extractVendored());
    }

    private static byte[] readShippedBlob() throws IOException {
        Path shipped = Path.of("src/main/resources/grammars/json/tree-sitter-json-blob.bin");
        return Files.readAllBytes(shipped);
    }
}