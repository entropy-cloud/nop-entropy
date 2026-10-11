package io.nop.treesitter.language;

import io.nop.treesitter.codegen.BlobReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1: the Language blob loader — decodes symbol table, metadata, state
 * table, parse actions, lex modes and keyword lex modes from the shipped blob
 * resource, and validates magic / version / counts.
 */
class LanguageTest {

    private static final String BLOB_RESOURCE = "/grammars/json/tree-sitter-json-blob.bin";

    private static Language loadShipped() {
        return Language.fromClasspath(BLOB_RESOURCE);
    }

    private static byte[] readShipped() throws IOException {
        Path shipped = Path.of("src/main/resources/grammars/json/tree-sitter-json-blob.bin");
        return Files.readAllBytes(shipped);
    }

    @Test
    void loaderDecodesShippedBlobHeader() throws IOException {
        Language lang = loadShipped();
        assertEquals(14, lang.abiVersion());
        assertEquals(25, lang.symbolCount());
        assertEquals(32, lang.stateCount());
        assertEquals(7, lang.largeStateCount());
        assertEquals(15, lang.tokenCount());
    }

    @Test
    void loaderDecodesSymbolTableInSymbolIdOrder() {
        Language lang = loadShipped();
        assertEquals("end", lang.symbolName(0));
        assertEquals("{", lang.symbolName(1));
        assertEquals("comment", lang.symbolName(14));
        assertEquals("string", lang.symbolName(20));
        assertEquals("array_repeat1", lang.symbolName(24));
        assertThrows(io.nop.treesitter.TreeSitterException.class, () -> lang.symbolName(25));
    }

    @Test
    void loaderDecodesSymbolMetadata() {
        Language lang = loadShipped();
        assertFalse(lang.symbolVisible(0), "end must be invisible");
        assertTrue(lang.symbolNamed(0), "end must be named");
        assertTrue(lang.symbolVisible(1), "'{' is visible");
        assertFalse(lang.symbolNamed(1), "'{' is not named");
        assertTrue(lang.symbolNamed(14), "comment is named");
        assertTrue(lang.symbolVisible(14), "comment is visible");
        assertFalse(lang.symbolVisible(21), "aux_sym__string_content is invisible");
        assertFalse(lang.symbolNamed(21), "aux_sym__string_content is unnamed");
    }

    @Test
    void loaderDecodesLexModesPerParseState() {
        Language lang = loadShipped();
        assertEquals(32, lang.lexModes().length);
        assertEquals(0, lang.lexState(0));
        assertEquals(0, lang.lexState(1));
        assertEquals(1, lang.lexState(17));
        assertEquals(1, lang.lexState(18));
        assertEquals(1, lang.lexState(19));
        assertEquals(0, lang.lexState(20));
        assertEquals(0, lang.lexState(31));
    }

    @Test
    void loaderDecodesEmptyKeywordLexModes() {
        Language lang = loadShipped();
        assertEquals(0, lang.keywordLexModes().length);
        assertEquals(0, lang.keywordLexState(1));
        assertEquals(0, lang.keywordLexState(31));
    }

    @Test
    void loaderResolvesParseTableCells() {
        Language lang = loadShipped();
        assertEquals(9, lang.tableCell(1, 5));    // state 1 '[' → ACTIONS(9)
        assertEquals(30, lang.tableCell(1, 15));  // state 1 document → STATE(30)
        assertEquals(31, lang.tableCell(4, 6));   // state 4 ']' → ACTIONS(31)
        assertEquals(1, lang.tableCell(0, 1));    // state 0 '{' → ACTIONS(1) (recover)
        assertEquals(3, lang.tableCell(7, 14));   // small state 7 comment → ACTIONS(3)
        assertEquals(0, lang.tableCell(1, 24));   // no entry for a nonterminal in state 1's token row
    }

    @Test
    void loaderResolvesActionGroupsByIndex() {
        Language lang = loadShipped();
        Language.ActionGroup group = lang.actionGroup(9);
        assertNotNull(group);
        assertEquals(9, group.index());
        assertEquals(1, group.count());
        assertTrue(group.reusable());
        Language.Action action = group.actions()[0];
        assertEquals(Language.Action.SHIFT, action.type());
        assertEquals(4, action.state());
        assertFalse(action.extra());

        Language.ActionGroup reduce = lang.actionGroup(17);
        assertEquals(Language.Action.REDUCE, reduce.actions()[0].type());
        assertEquals(22, reduce.actions()[0].symbol()); // aux_sym_document_repeat1
        assertEquals(2, reduce.actions()[0].childCount());

        assertNotNull(lang.actionGroup(92));
        assertEquals(Language.Action.ACCEPT, lang.actionGroup(92).actions()[0].type());
    }

    @Test
    void loaderMatchesIndependentBlobReaderImplementation() throws IOException {
        byte[] blob = readShipped();
        Language lang = Language.fromBytes(blob);
        BlobReader.Decoded decoded = BlobReader.read(blob);

        assertArrayEquals(decoded.symbolNames(), lang.symbolNames());
        assertArrayEquals(decoded.lexModes(), lang.lexModes());
        assertArrayEquals(decoded.primaryStateIds(), lang.primaryStateIds());
        assertArrayEquals(decoded.smallParseTable(), lang.smallParseTable());
        assertArrayEquals(decoded.smallParseTableMap(), lang.smallParseTableMap());
        assertEquals(decoded.parseActions().length, lang.parseActionGroups().length);
        for (int i = 0; i < decoded.parseActions().length; i++) {
            assertEquals(decoded.parseActions()[i].index(), lang.parseActionGroups()[i].index());
            assertEquals(decoded.parseActions()[i].count(), lang.parseActionGroups()[i].count());
            assertEquals(decoded.parseActions()[i].actions().length,
                    lang.parseActionGroups()[i].actions().length);
        }
        for (int state = 0; state < 7; state++) {
            for (int sym = 0; sym < 25; sym++) {
                assertEquals(lang.tableCell(state, sym), decoded.largeParseTable()[state * 25 + sym],
                        "large table cell [" + state + "][" + sym + "]");
            }
        }
    }

    @Test
    void loaderRejectsBadMagic() throws IOException {
        byte[] blob = readShipped();
        byte[] bad = Arrays.copyOf(blob, blob.length);
        bad[0] = 'X';
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Language.fromBytes(bad));
        assertTrue(ex.getMessage().contains("magic"), ex.getMessage());
    }

    @Test
    void loaderRejectsWrongFormatVersion() throws IOException {
        byte[] blob = readShipped();
        for (int badVersion : new int[]{1, 2}) {
            byte[] bad = Arrays.copyOf(blob, blob.length);
            bad[4] = (byte) badVersion;
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Language.fromBytes(bad));
            assertTrue(ex.getMessage().contains("format version"), ex.getMessage());
        }
    }

    @Test
    void loaderRejectsTruncatedBlob() throws IOException {
        byte[] blob = readShipped();
        byte[] truncated = Arrays.copyOf(blob, 40);
        assertThrows(IllegalStateException.class, () -> Language.fromBytes(truncated));
        byte[] midTruncated = Arrays.copyOf(blob, 200);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Language.fromBytes(midTruncated));
        assertTrue(ex.getMessage().contains("truncated"), ex.getMessage());
    }

    @Test
    void loaderRejectsTrailingBytes() throws IOException {
        byte[] blob = readShipped();
        byte[] padded = Arrays.copyOf(blob, blob.length + 4);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Language.fromBytes(padded));
        assertTrue(ex.getMessage().contains("trailing"), ex.getMessage());
    }

    @Test
    void loaderRejectsInconsistentSectionCounts() throws IOException {
        byte[] blob = readShipped();
        byte[] bad = Arrays.copyOf(blob, blob.length);
        // lex_mode_count (v4 header offset 28) must equal state_count
        bad[28] = 0;
        bad[29] = 31;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> Language.fromBytes(bad));
        assertTrue(ex.getMessage().contains("lex_mode_count"), ex.getMessage());
    }

    @Test
    void loaderIsLoadableFromClasspath() {
        Language lang = Language.fromClasspath(BLOB_RESOURCE);
        assertEquals(25, lang.symbolCount());
        try (InputStream in = LanguageTest.class.getResourceAsStream(BLOB_RESOURCE)) {
            assertNotNull(in);
        } catch (IOException e) {
            throw new io.nop.treesitter.TreeSitterException("failed to open classpath resource " + BLOB_RESOURCE, e);
        }
    }
}