package io.nop.treesitter.codegen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal independent reader for the blob format documented in
 * {@code src/main/resources/blob-format.md}. Deliberately separate from
 * {@link BlobWriter} so that writer/reader round-trip tests exercise two
 * independent implementations of the same documented layout.
 *
 * <p>Throws {@link IllegalStateException} on magic / version mismatch or
 * truncated data — no silent defaulting.</p>
 */
public final class BlobReader {

    public static final int HEADER_SIZE = 96;

    private BlobReader() {
    }

    /**
     * Decoded blob header: the section counts the reader validates before
     * decoding (format version 4 layout, see blob-format.md).
     */
    public record Header(int formatVersion, int abiVersion, int symbolCount, int stateCount,
                         int largeStateCount, int tokenCount, int productionIdCount, int fieldCount,
                         int parseActionGroupCount, int smallParseTableWordCount, int smallParseTableMapCount,
                         int lexModeCount, int keywordLexModeCount, int primaryStateIdCount,
                         int aliasCount, int maxAliasSequenceLength, int fieldNameCount, int fieldMapSliceCount,
                         int fieldMapEntryCount, int aliasSequenceElementCount, int nonTerminalAliasMapCount,
                         int keywordCaptureToken, int externalTokenCount, int externalLexStateCount,
                         int reservedWordSetCount, int maxReservedWordSetSize, int scannerProgramLength,
                         int lexerFnCount) {
    }

    /**
     * The fully decoded grammar, one field per blob section.
     */
    public record Decoded(Header header, String[] symbolNames, int[] symbolFlags,
                          ExtractedGrammar.ParseActionGroup[] parseActions, int[] largeParseTable, int[] smallParseTable,
                          int[] smallParseTableMap, int[] primaryStateIds, int[] lexModes, int[] externalLexStates,
                          int[] reservedWordSetIds, int[] keywordLexModes,
                          String[] fieldNames, ExtractedGrammar.FieldMapSlice[] fieldMapSlices,
                          ExtractedGrammar.FieldMapEntry[] fieldMapEntries, int[][] aliasSequences,
                          int[] nonTerminalAliasMap, ExtractedGrammar.LexerAutomaton lexer,
                          ExtractedGrammar.LexerAutomaton keywordLexer, int[] externalScannerSymbolMap,
                          boolean[][] externalScannerStates, int[][] reservedWords, byte[] scannerProgram) {
    }

    public static Decoded read(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new IllegalStateException("blob truncated: " + data.length + " bytes, header needs " + HEADER_SIZE);
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (magic[0] != 'T' || magic[1] != 'S' || magic[2] != 'J' || magic[3] != 'B') {
            throw new IllegalStateException("bad blob magic: "
                    + String.format("%02x %02x %02x %02x", magic[0], magic[1], magic[2], magic[3]));
        }
        int formatVersion = buf.get() & 0xFF;
        int abiVersion = buf.get() & 0xFF;
        buf.getShort(); // reserved
        Header header = new Header(formatVersion, abiVersion,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getInt(), buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getInt(), buf.getInt(), buf.getInt(),
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getInt(), buf.get() & 0xFF);
        if (formatVersion != 4) {
            throw new IllegalStateException("unsupported blob format version: " + formatVersion
                    + " (expected 4)");
        }
        for (int i = 0; i < 27; i++) {
            buf.get();
        }

        int symbolCount = header.symbolCount() + header.aliasCount();
        String[] symbolNames = new String[symbolCount];
        for (int i = 0; i < symbolCount; i++) {
            int len = buf.get() & 0xFF;
            byte[] b = new byte[len];
            buf.get(b);
            symbolNames[i] = new String(b, StandardCharsets.UTF_8);
        }

        int[] symbolFlags = new int[symbolCount];
        for (int i = 0; i < symbolCount; i++) {
            symbolFlags[i] = buf.get() & 0xFF;
        }

        ExtractedGrammar.ParseActionGroup[] groups = new ExtractedGrammar.ParseActionGroup[header.parseActionGroupCount()];
        for (int i = 0; i < groups.length; i++) {
            int index = buf.getShort() & 0xFFFF;
            int count = buf.get() & 0xFF;
            int reusable = buf.get() & 0xFF;
            ExtractedGrammar.ParseAction[] actions = new ExtractedGrammar.ParseAction[count];
            for (int j = 0; j < count; j++) {
                int type = buf.get() & 0xFF;
                int flags = buf.get() & 0xFF;
                int a = buf.getShort() & 0xFFFF;
                int b = buf.getShort() & 0xFFFF;
                int c = buf.getShort();
                int d = buf.getShort() & 0xFFFF;
                switch (type) {
                    case ExtractedGrammar.ParseAction.SHIFT ->
                            actions[j] = new ExtractedGrammar.ParseAction(type, a, 0, 0, 0, 0,
                                    (flags & 0x01) != 0, (flags & 0x02) != 0);
                    case ExtractedGrammar.ParseAction.REDUCE ->
                            actions[j] = new ExtractedGrammar.ParseAction(type, 0, a, b, c, d,
                                    false, false);
                    default ->
                            actions[j] = new ExtractedGrammar.ParseAction(type, 0, 0, 0, 0, 0,
                                    false, false);
                }
            }
            groups[i] = new ExtractedGrammar.ParseActionGroup(index, count, reusable == 1, actions);
        }

        int[] largeParseTable = new int[header.largeStateCount() * header.symbolCount()];
        for (int i = 0; i < largeParseTable.length; i++) {
            largeParseTable[i] = buf.getShort() & 0xFFFF;
        }

        int[] smallParseTable = new int[header.smallParseTableWordCount()];
        for (int i = 0; i < smallParseTable.length; i++) {
            smallParseTable[i] = buf.getShort() & 0xFFFF;
        }

        int[] smallParseTableMap = new int[header.smallParseTableMapCount()];
        for (int i = 0; i < smallParseTableMap.length; i++) {
            smallParseTableMap[i] = buf.getInt();
        }

        int[] primaryStateIds = new int[header.primaryStateIdCount()];
        for (int i = 0; i < primaryStateIds.length; i++) {
            primaryStateIds[i] = buf.getShort() & 0xFFFF;
        }

        int[] lexModes = new int[header.lexModeCount()];
        int[] externalLexStates = new int[header.lexModeCount()];
        int[] reservedWordSetIds = new int[header.lexModeCount()];
        for (int i = 0; i < header.lexModeCount(); i++) {
            lexModes[i] = buf.getShort() & 0xFFFF;
            externalLexStates[i] = buf.getShort() & 0xFFFF;
            reservedWordSetIds[i] = buf.getShort() & 0xFFFF;
        }

        int[] keywordLexModes = new int[header.keywordLexModeCount()];
        for (int i = 0; i < keywordLexModes.length; i++) {
            keywordLexModes[i] = buf.getShort() & 0xFFFF;
        }

        // --- v2 sections ---

        String[] fieldNames = new String[header.fieldNameCount()];
        for (int i = 0; i < fieldNames.length; i++) {
            int len = buf.get() & 0xFF;
            byte[] b = new byte[len];
            buf.get(b);
            fieldNames[i] = len == 0 ? null : new String(b, StandardCharsets.UTF_8);
        }

        ExtractedGrammar.FieldMapSlice[] fieldMapSlices = new ExtractedGrammar.FieldMapSlice[header.fieldMapSliceCount()];
        for (int i = 0; i < fieldMapSlices.length; i++) {
            fieldMapSlices[i] = new ExtractedGrammar.FieldMapSlice(
                    buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF);
        }

        ExtractedGrammar.FieldMapEntry[] fieldMapEntries = new ExtractedGrammar.FieldMapEntry[header.fieldMapEntryCount()];
        for (int i = 0; i < fieldMapEntries.length; i++) {
            fieldMapEntries[i] = new ExtractedGrammar.FieldMapEntry(
                    buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.get() != 0);
        }

        int prodCount = header.productionIdCount();
        int maxAlias = header.maxAliasSequenceLength();
        int[][] aliasSequences = new int[prodCount][maxAlias];
        for (int i = 0; i < prodCount; i++) {
            for (int j = 0; j < maxAlias; j++) {
                aliasSequences[i][j] = buf.getShort() & 0xFFFF;
            }
        }

        int[] nonTerminalAliasMap = new int[header.nonTerminalAliasMapCount()];
        for (int i = 0; i < nonTerminalAliasMap.length; i++) {
            nonTerminalAliasMap[i] = buf.getShort() & 0xFFFF;
        }

        int lexerFnCount = buf.get() & 0xFF;
        if (lexerFnCount != header.lexerFnCount()) {
            throw new IllegalStateException("lexer function count mismatch: header says "
                    + header.lexerFnCount() + ", section says " + lexerFnCount);
        }
        ExtractedGrammar.LexerAutomaton lexer = readLexerAutomaton(buf);
        ExtractedGrammar.LexerAutomaton keywordLexer = null;
        if (lexerFnCount >= 2) {
            keywordLexer = readLexerAutomaton(buf);
        }

        // --- v3 sections ---

        int[] externalScannerSymbolMap = new int[header.externalTokenCount()];
        for (int i = 0; i < externalScannerSymbolMap.length; i++) {
            externalScannerSymbolMap[i] = buf.getShort() & 0xFFFF;
        }

        boolean[][] externalScannerStates = new boolean[header.externalLexStateCount()][header.externalTokenCount()];
        for (int s = 0; s < externalScannerStates.length; s++) {
            for (int i = 0; i < header.externalTokenCount(); i++) {
                externalScannerStates[s][i] = buf.get() != 0;
            }
        }

        int[][] reservedWords = new int[header.reservedWordSetCount()][];
        for (int s = 0; s < reservedWords.length; s++) {
            int len = buf.get() & 0xFF;
            int[] row = new int[len];
            for (int i = 0; i < len; i++) {
                row[i] = buf.getShort() & 0xFFFF;
            }
            reservedWords[s] = row;
        }

        int scannerProgramLength = buf.getInt();
        if (scannerProgramLength < 0 || scannerProgramLength > buf.remaining()) {
            throw new IllegalStateException("scanner program length " + scannerProgramLength
                    + " out of range for remaining " + buf.remaining() + " bytes");
        }
        byte[] scannerProgram = new byte[scannerProgramLength];
        buf.get(scannerProgram);

        if (buf.hasRemaining()) {
            throw new IllegalStateException("blob has trailing bytes: " + buf.remaining());
        }
        return new Decoded(header, symbolNames, symbolFlags, groups, largeParseTable,
                smallParseTable, smallParseTableMap, primaryStateIds, lexModes, externalLexStates,
                reservedWordSetIds, keywordLexModes,
                fieldNames, fieldMapSlices, fieldMapEntries, aliasSequences, nonTerminalAliasMap,
                lexer, keywordLexer, externalScannerSymbolMap, externalScannerStates, reservedWords,
                scannerProgram);
    }

    private static ExtractedGrammar.LexerAutomaton readLexerAutomaton(ByteBuffer buf) {
        ExtractedGrammar.LexerAutomaton dfa = new ExtractedGrammar.LexerAutomaton();
        int stateCount = buf.getShort() & 0xFFFF;
        int acceptCount = buf.getShort() & 0xFFFF;
        int transitionCount = buf.getInt();
        int setCount = buf.getShort() & 0xFFFF;
        int setRangeCount = buf.getInt();
        dfa.stateCount = stateCount;
        dfa.acceptSymbol = new int[stateCount];
        java.util.Arrays.fill(dfa.acceptSymbol, -1);
        dfa.acceptAtEntry = new boolean[stateCount];
        for (int i = 0; i < acceptCount; i++) {
            int state = buf.getShort() & 0xFFFF;
            int symbol = buf.getShort() & 0xFFFF;
            int atEntry = buf.get() & 0xFF;
            if (state >= stateCount) {
                throw new IllegalStateException("lexer accept state " + state + " out of range " + stateCount);
            }
            dfa.acceptSymbol[state] = symbol;
            dfa.acceptAtEntry[state] = atEntry != 0;
        }
        @SuppressWarnings("unchecked")
        List<int[]>[] charSets = new List[setCount];
        for (int i = 0; i < setCount; i++) {
            charSets[i] = new ArrayList<>();
        }
        for (int i = 0; i < setRangeCount; i++) {
            int setId = buf.getShort() & 0xFFFF;
            int start = buf.getInt();
            int end = buf.getInt();
            charSets[setId].add(new int[]{start, end});
        }
        dfa.charSets = charSets;
        int[] perStateCounts = new int[stateCount];
        for (int i = 0; i < stateCount; i++) {
            perStateCounts[i] = buf.get() & 0xFF;
        }
        @SuppressWarnings("unchecked")
        List<ExtractedGrammar.LexerAutomaton.Transition>[] transitions = new List[stateCount];
        for (int i = 0; i < stateCount; i++) {
            transitions[i] = new ArrayList<>(perStateCounts[i]);
        }
        for (int state = 0; state < stateCount; state++) {
            for (int i = 0; i < perStateCounts[state]; i++) {
                int target = buf.getShort() & 0xFFFF;
                int skip = buf.get() & 0xFF;
                int clauseCount = buf.get() & 0xFF;
                List<ExtractedGrammar.LexerAutomaton.Clause> clauses = new ArrayList<>(clauseCount);
                for (int c = 0; c < clauseCount; c++) {
                    int literalCount = buf.get() & 0xFF;
                    List<ExtractedGrammar.LexerAutomaton.Literal> literals = new ArrayList<>(literalCount);
                    for (int l = 0; l < literalCount; l++) {
                        int kind = buf.get() & 0xFF;
                        int a = buf.getInt();
                        int b = buf.getInt();
                        literals.add(new ExtractedGrammar.LexerAutomaton.Literal(kind, a, b));
                    }
                    clauses.add(new ExtractedGrammar.LexerAutomaton.Clause(literals));
                }
                transitions[state].add(new ExtractedGrammar.LexerAutomaton.Transition(target, skip != 0, clauses));
            }
        }
        dfa.transitions = transitions;
        return dfa;
    }
}