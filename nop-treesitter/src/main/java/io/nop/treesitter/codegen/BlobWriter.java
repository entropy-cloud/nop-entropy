package io.nop.treesitter.codegen;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serializes an {@link ExtractedGrammar} into the compact binary blob format
 * documented in {@code src/main/resources/blob-format.md}.
 *
 * <p>All multi-byte integers are big-endian. The writer throws
 * {@link IllegalStateException} when a value does not fit the declared width
 * of its section instead of truncating silently.</p>
 */
public final class BlobWriter {

    private static final byte[] MAGIC = {'T', 'S', 'J', 'B'};
    private static final int FORMAT_VERSION = 4;

    private BlobWriter() {
    }

    public static byte[] write(ExtractedGrammar g) {
        int size = headerSize() + namesSize(g) + metadataSize(g) + actionsSize(g)
                + parseTableSize(g) + smallTableSize(g) + smallTableMapSize(g)
                + primaryStateIdsSize(g) + lexModesSize(g) + keywordLexModesSize(g)
                + fieldNamesSize(g) + fieldMapSlicesSize(g) + fieldMapEntriesSize(g)
                + aliasSequencesSize(g) + nonTerminalAliasMapSize(g) + lexerAutomataSize(g)
                + externalSymbolMapSize(g) + externalStatesSize(g) + reservedWordsSize(g)
                + scannerProgramSize(g);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.order(java.nio.ByteOrder.BIG_ENDIAN);

        writeHeader(buf, g);
        writeSymbolNames(buf, g);
        writeSymbolMetadata(buf, g);
        writeParseActions(buf, g);
        writeParseTable(buf, g);
        writeSmallParseTable(buf, g);
        writeSmallParseTableMap(buf, g);
        writePrimaryStateIds(buf, g);
        writeLexModes(buf, g);
        writeKeywordLexModes(buf, g);
        writeFieldNames(buf, g);
        writeFieldMapSlices(buf, g);
        writeFieldMapEntries(buf, g);
        writeAliasSequences(buf, g);
        writeNonTerminalAliasMap(buf, g);
        writeLexerAutomata(buf, g);
        writeExternalSymbolMap(buf, g);
        writeExternalStates(buf, g);
        writeReservedWords(buf, g);
        writeScannerProgram(buf, g);

        if (buf.position() != size) {
            throw new IllegalStateException("blob size mismatch: wrote " + buf.position() + " of " + size);
        }
        return buf.array();
    }

    private static int headerSize() {
        return 96;
    }

    private static void writeHeader(ByteBuffer buf, ExtractedGrammar g) {
        checkRange(g.symbolCount, 0xFFFF, "symbol_count");
        checkRange(g.stateCount, 0xFFFF, "state_count");
        checkRange(g.largeStateCount, 0xFFFF, "large_state_count");
        checkRange(g.tokenCount, 0xFFFF, "token_count");
        checkRange(g.productionIdCount, 0xFFFF, "production_id_count");
        checkRange(g.fieldCount, 0xFFFF, "field_count");
        checkRange(g.parseActions.length, 0xFFFF, "parse_action_group_count");
        checkRange(g.smallParseTable.length, 0xFFFFFFFFL, "small_parse_table_word_count");
        checkRange(g.smallParseTableMap.length, 0xFFFF, "small_parse_table_map_count");
        checkRange(g.lexModes.length, 0xFFFF, "lex_mode_count");
        checkRange(g.keywordLexModes.length, 0xFFFF, "keyword_lex_mode_count");
        checkRange(g.primaryStateIds.length, 0xFFFF, "primary_state_id_count");
        checkRange(g.aliasCount, 0xFFFF, "alias_count");
        checkRange(g.maxAliasSequenceLength, 0xFFFF, "max_alias_sequence_length");
        checkRange(g.fieldNames == null ? 0 : g.fieldNames.length, 0xFFFF, "field_name_count");
        checkRange(g.fieldMapSlices == null ? 0 : g.fieldMapSlices.length, 0xFFFF, "field_map_slice_count");
        checkRange(g.fieldMapEntries == null ? 0 : g.fieldMapEntries.length, 0xFFFFFFFFL, "field_map_entry_count");
        checkRange(g.aliasSequences == null ? 0 : aliasSequenceElementCount(g), 0xFFFFFFFFL, "alias_sequence_element_count");
        checkRange(g.nonTerminalAliasMap == null ? 0 : g.nonTerminalAliasMap.length, 0xFFFFFFFFL, "non_terminal_alias_map_count");
        checkRange(g.keywordCaptureToken, 0xFFFF, "keyword_capture_token");
        checkRange(g.externalTokenCount, 0xFFFF, "external_token_count");
        checkRange(g.externalScannerStates == null ? 0 : g.externalScannerStates.length, 0xFFFF,
                "external_lex_state_count");
        checkRange(g.reservedWords == null ? 0 : g.reservedWords.length, 0xFFFF, "reserved_word_set_count");
        checkRange(g.maxReservedWordSetSize, 0xFFFF, "max_reserved_word_set_size");
        checkRange(g.scannerProgram == null ? 0 : g.scannerProgram.length, 0xFFFFFFFFL, "scanner_program_length");

        buf.put(MAGIC);
        buf.put((byte) FORMAT_VERSION);
        buf.put((byte) g.languageVersion);
        buf.putShort((short) 0);
        buf.putShort((short) g.symbolCount);
        buf.putShort((short) g.stateCount);
        buf.putShort((short) g.largeStateCount);
        buf.putShort((short) g.tokenCount);
        buf.putShort((short) g.productionIdCount);
        buf.putShort((short) g.fieldCount);
        buf.putShort((short) g.parseActions.length);
        buf.putInt(g.smallParseTable.length);
        buf.putShort((short) g.smallParseTableMap.length);
        buf.putShort((short) g.lexModes.length);
        buf.putShort((short) g.keywordLexModes.length);
        buf.putShort((short) g.primaryStateIds.length);
        buf.putShort((short) g.aliasCount);
        buf.putShort((short) g.maxAliasSequenceLength);
        buf.putShort((short) (g.fieldNames == null ? 0 : g.fieldNames.length));
        buf.putShort((short) (g.fieldMapSlices == null ? 0 : g.fieldMapSlices.length));
        buf.putInt(g.fieldMapEntries == null ? 0 : g.fieldMapEntries.length);
        buf.putInt(aliasSequenceElementCount(g));
        buf.putInt(g.nonTerminalAliasMap == null ? 0 : g.nonTerminalAliasMap.length);
        buf.putShort((short) g.keywordCaptureToken);
        buf.putShort((short) g.externalTokenCount);
        buf.putShort((short) (g.externalScannerStates == null ? 0 : g.externalScannerStates.length));
        buf.putShort((short) (g.reservedWords == null ? 0 : g.reservedWords.length));
        buf.putShort((short) g.maxReservedWordSetSize);
        buf.putInt(g.scannerProgram == null ? 0 : g.scannerProgram.length);
        int fnCount = (g.keywordLexer != null) ? 2 : 1;
        buf.put((byte) fnCount);
        for (int i = 0; i < 27; i++) {
            buf.put((byte) 0);
        }
    }

    private static int aliasSequenceElementCount(ExtractedGrammar g) {
        if (g.aliasSequences == null || g.aliasSequences.length == 0) {
            return 0;
        }
        return g.aliasSequences.length * g.aliasSequences[0].length;
    }

    private static int namesSize(ExtractedGrammar g) {
        int n = 0;
        for (String name : g.symbolNames) {
            byte[] b = name.getBytes(StandardCharsets.UTF_8);
            checkRange(b.length, 0xFF, "symbol name length");
            n += 1 + b.length;
        }
        return n;
    }

    private static void writeSymbolNames(ByteBuffer buf, ExtractedGrammar g) {
        for (String name : g.symbolNames) {
            byte[] b = name.getBytes(StandardCharsets.UTF_8);
            buf.put((byte) b.length);
            buf.put(b);
        }
    }

    private static int metadataSize(ExtractedGrammar g) {
        return g.symbolMetadata.length;
    }

    private static void writeSymbolMetadata(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.SymbolMeta m : g.symbolMetadata) {
            int flags = 0;
            if (m.visible()) {
                flags |= 0x01;
            }
            if (m.named()) {
                flags |= 0x02;
            }
            if (m.supertype()) {
                flags |= 0x04;
            }
            buf.put((byte) flags);
        }
    }

    private static int actionsSize(ExtractedGrammar g) {
        int n = 0;
        for (ExtractedGrammar.ParseActionGroup group : g.parseActions) {
            checkRange(group.count(), 0xFF, "parse action group count");
            n += 4 + group.actions().length * 10;
        }
        return n;
    }

    private static void writeParseActions(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.ParseActionGroup group : g.parseActions) {
            checkRange(group.index(), 0xFFFF, "parse action group index");
            checkRange(group.count(), 0xFF, "parse action group count");
            if (group.actions().length != group.count()) {
                throw new IllegalStateException("parse action group " + group.index() + ": declared "
                        + group.count() + " actions but " + group.actions().length + " present");
            }
            buf.putShort((short) group.index());
            buf.put((byte) group.count());
            buf.put((byte) (group.reusable() ? 1 : 0));
            for (ExtractedGrammar.ParseAction a : group.actions()) {
                writeAction(buf, a);
            }
        }
    }

    private static void writeAction(ByteBuffer buf, ExtractedGrammar.ParseAction a) {
        int flags = (a.extra() ? 0x01 : 0x00) | (a.repetition() ? 0x02 : 0x00);
        buf.put((byte) a.type());
        buf.put((byte) flags);
        switch (a.type()) {
            case ExtractedGrammar.ParseAction.SHIFT -> {
                checkRange(a.state(), 0xFFFF, "shift state");
                buf.putShort((short) a.state());
                buf.putShort((short) 0);
                buf.putShort((short) 0);
                buf.putShort((short) 0);
            }
            case ExtractedGrammar.ParseAction.REDUCE -> {
                checkRange(a.symbol(), 0xFFFF, "reduce symbol");
                checkRange(a.childCount(), 0xFFFF, "reduce child count");
                checkRange(a.productionId(), 0xFFFF, "reduce production id");
                buf.putShort((short) a.symbol());
                buf.putShort((short) a.childCount());
                buf.putShort((short) a.dynamicPrecedence());
                buf.putShort((short) a.productionId());
            }
            case ExtractedGrammar.ParseAction.ACCEPT, ExtractedGrammar.ParseAction.RECOVER -> {
                buf.putShort((short) 0);
                buf.putShort((short) 0);
                buf.putShort((short) 0);
                buf.putShort((short) 0);
            }
            default -> throw new IllegalStateException("unknown parse action type: " + a.type());
        }
    }

    private static int parseTableSize(ExtractedGrammar g) {
        return g.largeStateCount * g.symbolCount * 2;
    }

    private static void writeParseTable(ByteBuffer buf, ExtractedGrammar g) {
        for (int state = 0; state < g.largeStateCount; state++) {
            for (int sym = 0; sym < g.symbolCount; sym++) {
                int v = g.parseTable[state][sym];
                checkRange(v, 0xFFFF, "parse table cell");
                buf.putShort((short) v);
            }
        }
    }

    private static int smallTableSize(ExtractedGrammar g) {
        return g.smallParseTable.length * 2;
    }

    private static void writeSmallParseTable(ByteBuffer buf, ExtractedGrammar g) {
        for (int w : g.smallParseTable) {
            checkRange(w, 0xFFFF, "small parse table word");
            buf.putShort((short) w);
        }
    }

    private static int smallTableMapSize(ExtractedGrammar g) {
        return g.smallParseTableMap.length * 4;
    }

    private static void writeSmallParseTableMap(ByteBuffer buf, ExtractedGrammar g) {
        for (int off : g.smallParseTableMap) {
            buf.putInt(off);
        }
    }

    private static int primaryStateIdsSize(ExtractedGrammar g) {
        return g.primaryStateIds.length * 2;
    }

    private static void writePrimaryStateIds(ByteBuffer buf, ExtractedGrammar g) {
        for (int id : g.primaryStateIds) {
            checkRange(id, 0xFFFF, "primary state id");
            buf.putShort((short) id);
        }
    }

    private static int lexModesSize(ExtractedGrammar g) {
        return g.lexModes.length * 6;
    }

    private static void writeLexModes(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.LexMode m : g.lexModes) {
            checkRange(m.lexState(), 0xFFFF, "lex state");
            checkRange(m.externalLexState(), 0xFFFF, "external lex state");
            checkRange(m.reservedWordSetId(), 0xFFFF, "reserved word set id");
            buf.putShort((short) m.lexState());
            buf.putShort((short) m.externalLexState());
            buf.putShort((short) m.reservedWordSetId());
        }
    }

    private static int keywordLexModesSize(ExtractedGrammar g) {
        return g.keywordLexModes.length * 2;
    }

    private static void writeKeywordLexModes(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.LexMode m : g.keywordLexModes) {
            checkRange(m.lexState(), 0xFFFF, "keyword lex state");
            buf.putShort((short) m.lexState());
        }
    }

    // ------------------------------------------------------------------
    // v2 sections: fields, aliases, lexer automata
    // ------------------------------------------------------------------

    private static int fieldNamesSize(ExtractedGrammar g) {
        if (g.fieldNames == null) {
            return 0;
        }
        int n = 0;
        for (String name : g.fieldNames) {
            if (name == null) {
                n += 1;
                continue;
            }
            byte[] b = name.getBytes(StandardCharsets.UTF_8);
            checkRange(b.length, 0xFF, "field name length");
            n += 1 + b.length;
        }
        return n;
    }

    private static void writeFieldNames(ByteBuffer buf, ExtractedGrammar g) {
        if (g.fieldNames == null) {
            return;
        }
        for (String name : g.fieldNames) {
            if (name == null) {
                buf.put((byte) 0);
                continue;
            }
            byte[] b = name.getBytes(StandardCharsets.UTF_8);
            buf.put((byte) b.length);
            buf.put(b);
        }
    }

    private static int fieldMapSlicesSize(ExtractedGrammar g) {
        return g.fieldMapSlices == null ? 0 : g.fieldMapSlices.length * 4;
    }

    private static void writeFieldMapSlices(ByteBuffer buf, ExtractedGrammar g) {
        if (g.fieldMapSlices == null) {
            return;
        }
        for (ExtractedGrammar.FieldMapSlice s : g.fieldMapSlices) {
            checkRange(s.index(), 0xFFFF, "field map slice index");
            checkRange(s.length(), 0xFFFF, "field map slice length");
            buf.putShort((short) s.index());
            buf.putShort((short) s.length());
        }
    }

    private static int fieldMapEntriesSize(ExtractedGrammar g) {
        return g.fieldMapEntries == null ? 0 : g.fieldMapEntries.length * 5;
    }

    private static void writeFieldMapEntries(ByteBuffer buf, ExtractedGrammar g) {
        if (g.fieldMapEntries == null) {
            return;
        }
        for (ExtractedGrammar.FieldMapEntry e : g.fieldMapEntries) {
            checkRange(e.fieldId(), 0xFFFF, "field map entry field id");
            checkRange(e.childIndex(), 0xFFFF, "field map entry child index");
            buf.putShort((short) e.fieldId());
            buf.putShort((short) e.childIndex());
            buf.put((byte) (e.inherited() ? 1 : 0));
        }
    }

    private static int aliasSequencesSize(ExtractedGrammar g) {
        return aliasSequenceElementCount(g) * 2;
    }

    private static void writeAliasSequences(ByteBuffer buf, ExtractedGrammar g) {
        if (g.aliasSequences == null) {
            return;
        }
        for (int[] row : g.aliasSequences) {
            for (int symbol : row) {
                checkRange(symbol, 0xFFFF, "alias sequence symbol");
                buf.putShort((short) symbol);
            }
        }
    }

    private static int nonTerminalAliasMapSize(ExtractedGrammar g) {
        return g.nonTerminalAliasMap == null ? 0 : g.nonTerminalAliasMap.length * 2;
    }

    private static void writeNonTerminalAliasMap(ByteBuffer buf, ExtractedGrammar g) {
        if (g.nonTerminalAliasMap == null) {
            return;
        }
        for (int symbol : g.nonTerminalAliasMap) {
            checkRange(symbol, 0xFFFF, "non-terminal alias map symbol");
            buf.putShort((short) symbol);
        }
    }

    private static int lexerAutomataSize(ExtractedGrammar g) {
        int n = 1;
        n += lexerAutomatonSize(g.lexer);
        if (g.keywordLexer != null) {
            n += lexerAutomatonSize(g.keywordLexer);
        }
        return n;
    }

    private static int lexerAutomatonSize(ExtractedGrammar.LexerAutomaton dfa) {
        int n = 0;
        int stateCount = dfa.stateCount;
        n += 2 + 2 + 4 + 2 + 4; // state_count, accept_count, transition_count, set_count, set_range_count
        n += 5 * dfa.acceptCount();
        n += 10 * dfa.setRangeCount();
        n += stateCount; // per-state transition counts
        for (int state = 0; state < stateCount; state++) {
            for (ExtractedGrammar.LexerAutomaton.Transition t : dfa.transitions[state]) {
                n += 4 + transitionClauseBytes(t);
            }
        }
        return n;
    }

    private static int transitionClauseBytes(ExtractedGrammar.LexerAutomaton.Transition t) {
        int n = 0;
        for (ExtractedGrammar.LexerAutomaton.Clause c : t.clauses()) {
            n += 1 + c.literals().size() * 9;
        }
        return n;
    }

    private static void writeLexerAutomata(ByteBuffer buf, ExtractedGrammar g) {
        buf.put((byte) (g.keywordLexer != null ? 2 : 1));
        writeLexerAutomaton(buf, g.lexer);
        if (g.keywordLexer != null) {
            writeLexerAutomaton(buf, g.keywordLexer);
        }
    }

    private static void writeLexerAutomaton(ByteBuffer buf, ExtractedGrammar.LexerAutomaton dfa) {
        int stateCount = dfa.stateCount;
        int acceptCount = dfa.acceptCount();
        int transitionCount = dfa.transitionCount();
        int setCount = dfa.charSets.length;
        int setRangeCount = dfa.setRangeCount();
        buf.putShort((short) stateCount);
        buf.putShort((short) acceptCount);
        buf.putInt(transitionCount);
        buf.putShort((short) setCount);
        buf.putInt(setRangeCount);

        for (int state = 0; state < stateCount; state++) {
            int sym = dfa.acceptSymbol[state];
            if (sym >= 0) {
                checkRange(sym, 0xFFFF, "lexer accept symbol");
                buf.putShort((short) state);
                buf.putShort((short) sym);
                buf.put((byte) (dfa.acceptAtEntry[state] ? 1 : 0));
            }
        }

        for (int setId = 0; setId < setCount; setId++) {
            for (int[] range : dfa.charSets[setId]) {
                buf.putShort((short) setId);
                buf.putInt(range[0]);
                buf.putInt(range[1]);
            }
        }

        for (int state = 0; state < stateCount; state++) {
            int cnt = dfa.transitions[state].size();
            checkRange(cnt, 0xFF, "per-state transition count");
            buf.put((byte) cnt);
        }

        for (int state = 0; state < stateCount; state++) {
            for (ExtractedGrammar.LexerAutomaton.Transition t : dfa.transitions[state]) {
                checkRange(t.targetState(), 0xFFFF, "lexer transition target");
                buf.putShort((short) t.targetState());
                buf.put((byte) (t.skip() ? 1 : 0));
                buf.put((byte) t.clauses().size());
                for (ExtractedGrammar.LexerAutomaton.Clause c : t.clauses()) {
                    buf.put((byte) c.literals().size());
                    for (ExtractedGrammar.LexerAutomaton.Literal lit : c.literals()) {
                        buf.put((byte) lit.kind());
                        buf.putInt(lit.a());
                        buf.putInt(lit.b());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // v3 sections: external scanner tables and scanner program
    // ------------------------------------------------------------------

    private static int externalSymbolMapSize(ExtractedGrammar g) {
        return g.externalScannerSymbolMap == null ? 0 : g.externalScannerSymbolMap.length * 2;
    }

    private static void writeExternalSymbolMap(ByteBuffer buf, ExtractedGrammar g) {
        if (g.externalScannerSymbolMap == null) {
            return;
        }
        for (int symbol : g.externalScannerSymbolMap) {
            checkRange(symbol, 0xFFFF, "external scanner symbol map symbol");
            buf.putShort((short) symbol);
        }
    }

    private static int externalStatesSize(ExtractedGrammar g) {
        if (g.externalScannerStates == null) {
            return 0;
        }
        int n = 0;
        for (boolean[] row : g.externalScannerStates) {
            if (row.length != g.externalTokenCount) {
                throw new IllegalStateException("external scanner state row width "
                        + row.length + " != external_token_count " + g.externalTokenCount);
            }
            n += row.length;
        }
        return n;
    }

    private static void writeExternalStates(ByteBuffer buf, ExtractedGrammar g) {
        if (g.externalScannerStates == null) {
            return;
        }
        for (boolean[] row : g.externalScannerStates) {
            for (boolean bit : row) {
                buf.put((byte) (bit ? 1 : 0));
            }
        }
    }

    private static int reservedWordsSize(ExtractedGrammar g) {
        if (g.reservedWords == null) {
            return 0;
        }
        int n = 0;
        for (int[] row : g.reservedWords) {
            checkRange(row.length, 0xFF, "reserved word row length");
            n += 1 + row.length * 2;
        }
        return n;
    }

    private static void writeReservedWords(ByteBuffer buf, ExtractedGrammar g) {
        if (g.reservedWords == null) {
            return;
        }
        for (int[] row : g.reservedWords) {
            checkRange(row.length, 0xFF, "reserved word row length");
            buf.put((byte) row.length);
            for (int symbol : row) {
                checkRange(symbol, 0xFFFF, "reserved word symbol");
                buf.putShort((short) symbol);
            }
        }
    }

    private static int scannerProgramSize(ExtractedGrammar g) {
        return 4 + (g.scannerProgram == null ? 0 : g.scannerProgram.length);
    }

    private static void writeScannerProgram(ByteBuffer buf, ExtractedGrammar g) {
        byte[] program = g.scannerProgram == null ? new byte[0] : g.scannerProgram;
        buf.putInt(program.length);
        buf.put(program);
    }

    private static void checkRange(int value, long max, String what) {
        if (value < 0 || value > max) {
            throw new IllegalStateException(what + " out of declared width range: " + value
                    + " (max " + max + ")");
        }
    }
}