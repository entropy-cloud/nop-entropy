package io.nop.treesitter.language;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.codegen.ExtractedGrammar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime loader for the parse-table binary blob documented in
 * {@code src/main/resources/blob-format.md}.
 *
 * <p>Decodes all sixteen sections — symbol table, symbol metadata, parse action
 * groups, large + small parse tables, small-table map, primary state ids, lex
 * modes, keyword lex modes, field names, field map slices/entries, alias
 * sequences, the non-terminal alias map and the lexer automata — and validates
 * the magic, format version and cross-section counts before exposing them to
 * the lexer and parser. The decode is an independent implementation of the
 * format document (the codegen-side {@code BlobReader} is deliberately kept
 * separate so the two implementations cross-check each other).</p>
 *
 * <p>Format violations (wrong magic, unsupported format version, truncated or
 * trailing data, inconsistent counts) raise {@link IllegalStateException}
 * instead of silently defaulting, per the format document's reader contract.</p>
 */
public final class Language {

    public static final int FORMAT_VERSION = 3;
    public static final int INITIAL_STATE = 1;

    private final int abiVersion;
    private final int stateCount;
    private final int largeStateCount;
    private final int symbolCount;
    private final int aliasCount;
    private final int tokenCount;
    private final int externalTokenCount;
    private final int productionIdCount;
    private final int fieldCount;
    private final int maxAliasSequenceLength;
    private final int keywordCaptureToken;

    private final String[] symbolNames;
    private final byte[] symbolFlags;
    private final ActionGroup[] parseActionGroups;
    private final Map<Integer, ActionGroup> groupByIndex;
    private final int[] largeParseTable;
    private final int[] smallParseTable;
    private final int[] smallParseTableMap;
    private final int[] primaryStateIds;
    private final int[] lexModes;
    private final int[] externalLexStates;
    private final int[] reservedWordSetIds;
    private final int[] keywordLexModes;

    private final String[] fieldNames;
    private final FieldMapSlice[] fieldMapSlices;
    private final FieldMapEntry[] fieldMapEntries;
    private final int[][] aliasSequences;
    private final int[] nonTerminalAliasMap;
    private final LexerAutomaton lexer;
    private final LexerAutomaton keywordLexer;

    private final int[] externalScannerSymbolMap;
    private final boolean[][] externalScannerStates;
    private final int[][] reservedWords;
    private final byte[] scannerProgram;

    private Language(int abiVersion, int stateCount, int largeStateCount, int symbolCount, int aliasCount,
                     int tokenCount, int externalTokenCount, int productionIdCount, int fieldCount,
                     int maxAliasSequenceLength, int keywordCaptureToken, String[] symbolNames, byte[] symbolFlags,
                     ActionGroup[] parseActionGroups, int[] largeParseTable, int[] smallParseTable,
                     int[] smallParseTableMap, int[] primaryStateIds, int[] lexModes, int[] externalLexStates,
                     int[] reservedWordSetIds, int[] keywordLexModes,
                     String[] fieldNames, FieldMapSlice[] fieldMapSlices, FieldMapEntry[] fieldMapEntries,
                     int[][] aliasSequences, int[] nonTerminalAliasMap, LexerAutomaton lexer,
                     LexerAutomaton keywordLexer, int[] externalScannerSymbolMap, boolean[][] externalScannerStates,
                     int[][] reservedWords, byte[] scannerProgram) {
        this.abiVersion = abiVersion;
        this.stateCount = stateCount;
        this.largeStateCount = largeStateCount;
        this.symbolCount = symbolCount;
        this.aliasCount = aliasCount;
        this.tokenCount = tokenCount;
        this.externalTokenCount = externalTokenCount;
        this.productionIdCount = productionIdCount;
        this.fieldCount = fieldCount;
        this.maxAliasSequenceLength = maxAliasSequenceLength;
        this.keywordCaptureToken = keywordCaptureToken;
        this.symbolNames = symbolNames;
        this.symbolFlags = symbolFlags;
        this.parseActionGroups = parseActionGroups;
        this.groupByIndex = new HashMap<>();
        for (ActionGroup group : parseActionGroups) {
            groupByIndex.put(group.index(), group);
        }
        this.largeParseTable = largeParseTable;
        this.smallParseTable = smallParseTable;
        this.smallParseTableMap = smallParseTableMap;
        this.primaryStateIds = primaryStateIds;
        this.lexModes = lexModes;
        this.externalLexStates = externalLexStates;
        this.reservedWordSetIds = reservedWordSetIds;
        this.keywordLexModes = keywordLexModes;
        this.fieldNames = fieldNames;
        this.fieldMapSlices = fieldMapSlices;
        this.fieldMapEntries = fieldMapEntries;
        this.aliasSequences = aliasSequences;
        this.nonTerminalAliasMap = nonTerminalAliasMap;
        this.lexer = lexer;
        this.keywordLexer = keywordLexer;
        this.externalScannerSymbolMap = externalScannerSymbolMap;
        this.externalScannerStates = externalScannerStates;
        this.reservedWords = reservedWords;
        this.scannerProgram = scannerProgram;
    }

    /**
     * Decodes a blob from raw bytes. Throws {@link IllegalStateException} for
     * every format violation; never returns a partially initialized language.
     */
    public static Language fromBytes(byte[] blob) {
        if (blob.length < 96) {
            throw new IllegalStateException("blob truncated: " + blob.length + " bytes, header needs 96");
        }
        try {
            return decode(blob);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IllegalStateException("blob truncated: " + blob.length + " bytes end before the "
                    + "declared sections are fully read", e);
        }
    }

    private static Language decode(byte[] blob) {
        ByteBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (magic[0] != 'T' || magic[1] != 'S' || magic[2] != 'J' || magic[3] != 'B') {
            throw new IllegalStateException("bad blob magic: "
                    + String.format("%02x %02x %02x %02x", magic[0], magic[1], magic[2], magic[3]));
        }
        int formatVersion = buf.get() & 0xFF;
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalStateException("unsupported blob format version: " + formatVersion
                    + " (expected " + FORMAT_VERSION + ")");
        }
        int abiVersion = buf.get() & 0xFF;
        buf.getShort(); // reserved
        int symbolCount = buf.getShort() & 0xFFFF;
        int stateCount = buf.getShort() & 0xFFFF;
        int largeStateCount = buf.getShort() & 0xFFFF;
        int tokenCount = buf.getShort() & 0xFFFF;
        int productionIdCount = buf.getShort() & 0xFFFF;
        int fieldCount = buf.getShort() & 0xFFFF;
        int parseActionGroupCount = buf.getShort() & 0xFFFF;
        int smallParseTableWordCount = buf.getShort() & 0xFFFF;
        int smallParseTableMapCount = buf.getShort() & 0xFFFF;
        int lexModeCount = buf.getShort() & 0xFFFF;
        int keywordLexModeCount = buf.getShort() & 0xFFFF;
        int primaryStateIdCount = buf.getShort() & 0xFFFF;
        int aliasCount = buf.getShort() & 0xFFFF;
        int maxAliasSequenceLength = buf.getShort() & 0xFFFF;
        int fieldNameCount = buf.getShort() & 0xFFFF;
        int fieldMapSliceCount = buf.getShort() & 0xFFFF;
        int fieldMapEntryCount = buf.getInt();
        int aliasSequenceElementCount = buf.getInt();
        int nonTerminalAliasMapCount = buf.getInt();
        int keywordCaptureToken = buf.getShort() & 0xFFFF;
        int externalTokenCount = buf.getShort() & 0xFFFF;
        int externalLexStateCount = buf.getShort() & 0xFFFF;
        int reservedWordSetCount = buf.getShort() & 0xFFFF;
        int maxReservedWordSetSize = buf.getShort() & 0xFFFF;
        int scannerProgramLength = buf.getInt();
        int lexerFnCount = buf.get() & 0xFF;
        for (int i = 0; i < 29; i++) {
            buf.get();
        }
        if (largeStateCount > stateCount) {
            throw new IllegalStateException("large_state_count " + largeStateCount
                    + " exceeds state_count " + stateCount);
        }
        if (smallParseTableMapCount != stateCount - largeStateCount) {
            throw new IllegalStateException("small_parse_table_map_count " + smallParseTableMapCount
                    + " != state_count - large_state_count (" + (stateCount - largeStateCount) + ")");
        }
        if (primaryStateIdCount != stateCount) {
            throw new IllegalStateException("primary_state_id_count " + primaryStateIdCount
                    + " != state_count " + stateCount);
        }
        if (lexModeCount != stateCount) {
            throw new IllegalStateException("lex_mode_count " + lexModeCount + " != state_count " + stateCount);
        }
        if (fieldMapSliceCount != productionIdCount) {
            throw new IllegalStateException("field_map_slice_count " + fieldMapSliceCount
                    + " != production_id_count " + productionIdCount);
        }
        if (aliasSequenceElementCount != productionIdCount * maxAliasSequenceLength) {
            throw new IllegalStateException("alias_sequence_element_count " + aliasSequenceElementCount
                    + " != production_id_count * max_alias_sequence_length ("
                    + (productionIdCount * maxAliasSequenceLength) + ")");
        }
        if (lexerFnCount < 1 || lexerFnCount > 2) {
            throw new IllegalStateException("lexer_fn_count " + lexerFnCount + " out of range [1,2]");
        }
        if (scannerProgramLength < 0 || scannerProgramLength > blob.length) {
            throw new IllegalStateException("scanner_program_length " + scannerProgramLength
                    + " out of range for blob size " + blob.length);
        }
        if (externalTokenCount == 0 && (externalLexStateCount != 0 || reservedWordSetCount != 0
                || maxReservedWordSetSize != 0)) {
            throw new IllegalStateException("external_token_count 0 but external lex state / reserved word "
                    + "counts are non-zero");
        }

        int totalSymbolCount = symbolCount + aliasCount;
        String[] symbolNames = new String[totalSymbolCount];
        for (int i = 0; i < totalSymbolCount; i++) {
            int len = buf.get() & 0xFF;
            byte[] name = new byte[len];
            buf.get(name);
            symbolNames[i] = new String(name, StandardCharsets.UTF_8);
        }
        if (symbolCount > 0 && !"end".equals(symbolNames[0])) {
            throw new IllegalStateException("symbol 0 must be the built-in end token, got: " + symbolNames[0]);
        }

        byte[] symbolFlags = new byte[totalSymbolCount];
        buf.get(symbolFlags);

        ActionGroup[] groups = new ActionGroup[parseActionGroupCount];
        for (int i = 0; i < parseActionGroupCount; i++) {
            int index = buf.getShort() & 0xFFFF;
            int count = buf.get() & 0xFF;
            int reusable = buf.get() & 0xFF;
            Action[] actions = new Action[count];
            for (int j = 0; j < count; j++) {
                int type = buf.get() & 0xFF;
                int flags = buf.get() & 0xFF;
                int a = buf.getShort() & 0xFFFF;
                int b = buf.getShort() & 0xFFFF;
                int c = buf.getShort();
                int d = buf.getShort() & 0xFFFF;
                switch (type) {
                    case Action.SHIFT -> actions[j] = new Action(type, a, 0, 0, 0, 0,
                            (flags & 0x01) != 0, (flags & 0x02) != 0);
                    case Action.REDUCE -> actions[j] = new Action(type, 0, a, b, c, d, false, false);
                    default -> actions[j] = new Action(type, 0, 0, 0, 0, 0, false, false);
                }
            }
            groups[i] = new ActionGroup(index, count, reusable == 1, actions);
        }

        int[] largeParseTable = new int[largeStateCount * symbolCount];
        for (int i = 0; i < largeParseTable.length; i++) {
            largeParseTable[i] = buf.getShort() & 0xFFFF;
        }

        int[] smallParseTable = new int[smallParseTableWordCount];
        for (int i = 0; i < smallParseTable.length; i++) {
            smallParseTable[i] = buf.getShort() & 0xFFFF;
        }

        int[] smallParseTableMap = new int[smallParseTableMapCount];
        for (int i = 0; i < smallParseTableMap.length; i++) {
            smallParseTableMap[i] = buf.getInt();
        }

        int[] primaryStateIds = new int[primaryStateIdCount];
        for (int i = 0; i < primaryStateIds.length; i++) {
            primaryStateIds[i] = buf.getShort() & 0xFFFF;
        }

        int[] lexModes = new int[lexModeCount];
        int[] externalLexStates = new int[lexModeCount];
        int[] reservedWordSetIds = new int[lexModeCount];
        for (int i = 0; i < lexModeCount; i++) {
            lexModes[i] = buf.getShort() & 0xFFFF;
            externalLexStates[i] = buf.getShort() & 0xFFFF;
            reservedWordSetIds[i] = buf.getShort() & 0xFFFF;
        }
        if (externalLexStateCount > 0) {
            for (int i = 0; i < lexModeCount; i++) {
                if (externalLexStates[i] >= externalLexStateCount) {
                    throw new IllegalStateException("parse state " + i + " external_lex_state "
                            + externalLexStates[i] + " out of range [0," + externalLexStateCount + ")");
                }
            }
        }
        if (reservedWordSetCount > 0) {
            for (int i = 0; i < lexModeCount; i++) {
                if (reservedWordSetIds[i] >= reservedWordSetCount) {
                    throw new IllegalStateException("parse state " + i + " reserved_word_set_id "
                            + reservedWordSetIds[i] + " out of range [0," + reservedWordSetCount + ")");
                }
            }
        }

        int[] keywordLexModes = new int[keywordLexModeCount];
        for (int i = 0; i < keywordLexModes.length; i++) {
            keywordLexModes[i] = buf.getShort() & 0xFFFF;
        }

        String[] fieldNames = new String[fieldNameCount];
        for (int i = 0; i < fieldNames.length; i++) {
            int len = buf.get() & 0xFF;
            byte[] name = new byte[len];
            buf.get(name);
            fieldNames[i] = len == 0 ? null : new String(name, StandardCharsets.UTF_8);
        }

        FieldMapSlice[] fieldMapSlices = new FieldMapSlice[fieldMapSliceCount];
        for (int i = 0; i < fieldMapSliceCount; i++) {
            fieldMapSlices[i] = new FieldMapSlice(buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF);
        }

        FieldMapEntry[] fieldMapEntries = new FieldMapEntry[fieldMapEntryCount];
        for (int i = 0; i < fieldMapEntryCount; i++) {
            fieldMapEntries[i] = new FieldMapEntry(buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.get() != 0);
        }

        int[][] aliasSequences = new int[productionIdCount][maxAliasSequenceLength];
        for (int i = 0; i < productionIdCount; i++) {
            for (int j = 0; j < maxAliasSequenceLength; j++) {
                aliasSequences[i][j] = buf.getShort() & 0xFFFF;
            }
        }

        int[] nonTerminalAliasMap = new int[nonTerminalAliasMapCount];
        for (int i = 0; i < nonTerminalAliasMap.length; i++) {
            nonTerminalAliasMap[i] = buf.getShort() & 0xFFFF;
        }

        int sectionLexerFnCount = buf.get() & 0xFF;
        if (sectionLexerFnCount != lexerFnCount) {
            throw new IllegalStateException("lexer function count mismatch: header says "
                    + lexerFnCount + ", section says " + sectionLexerFnCount);
        }
        LexerAutomaton lexer = readLexerAutomaton(buf);
        LexerAutomaton keywordLexer = null;
        if (lexerFnCount >= 2) {
            keywordLexer = readLexerAutomaton(buf);
        }

        int[] externalScannerSymbolMap = new int[externalTokenCount];
        for (int i = 0; i < externalTokenCount; i++) {
            externalScannerSymbolMap[i] = buf.getShort() & 0xFFFF;
        }

        boolean[][] externalScannerStates = new boolean[externalLexStateCount][externalTokenCount];
        for (int s = 0; s < externalLexStateCount; s++) {
            for (int i = 0; i < externalTokenCount; i++) {
                externalScannerStates[s][i] = buf.get() != 0;
            }
        }

        int[][] reservedWords = new int[reservedWordSetCount][];
        for (int s = 0; s < reservedWordSetCount; s++) {
            int len = buf.get() & 0xFF;
            if (len > maxReservedWordSetSize) {
                throw new IllegalStateException("reserved word set " + s + " length " + len
                        + " exceeds max_reserved_word_set_size " + maxReservedWordSetSize);
            }
            int[] row = new int[len];
            for (int i = 0; i < len; i++) {
                row[i] = buf.getShort() & 0xFFFF;
            }
            reservedWords[s] = row;
        }

        byte[] scannerProgram = readScannerProgram(buf, scannerProgramLength);

        if (buf.hasRemaining()) {
            throw new IllegalStateException("blob has trailing bytes: " + buf.remaining());
        }
        return new Language(abiVersion, stateCount, largeStateCount, symbolCount, aliasCount,
                tokenCount, externalTokenCount, productionIdCount, fieldCount, maxAliasSequenceLength,
                keywordCaptureToken, symbolNames, symbolFlags, groups, largeParseTable, smallParseTable,
                smallParseTableMap, primaryStateIds, lexModes, externalLexStates, reservedWordSetIds,
                keywordLexModes, fieldNames, fieldMapSlices, fieldMapEntries, aliasSequences,
                nonTerminalAliasMap, lexer, keywordLexer, externalScannerSymbolMap, externalScannerStates,
                reservedWords, scannerProgram);
    }

    private static byte[] readScannerProgram(ByteBuffer buf, int headerLength) {
        int sectionLength = buf.getInt();
        if (sectionLength != headerLength) {
            throw new IllegalStateException("scanner program length mismatch: header says "
                    + headerLength + ", section says " + sectionLength);
        }
        byte[] program = new byte[sectionLength];
        buf.get(program);
        return program;
    }

    private static LexerAutomaton readLexerAutomaton(ByteBuffer buf) {
        LexerAutomaton dfa = new LexerAutomaton();
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
        List<LexerAutomaton.Transition>[] transitions = new List[stateCount];
        for (int i = 0; i < stateCount; i++) {
            transitions[i] = new ArrayList<>(perStateCounts[i]);
        }
        for (int state = 0; state < stateCount; state++) {
            for (int i = 0; i < perStateCounts[state]; i++) {
                int target = buf.getShort() & 0xFFFF;
                int skip = buf.get() & 0xFF;
                int clauseCount = buf.get() & 0xFF;
                List<LexerAutomaton.Clause> clauses = new ArrayList<>(clauseCount);
                for (int c = 0; c < clauseCount; c++) {
                    int literalCount = buf.get() & 0xFF;
                    List<LexerAutomaton.Literal> literals = new ArrayList<>(literalCount);
                    for (int l = 0; l < literalCount; l++) {
                        int kind = buf.get() & 0xFF;
                        int a = buf.getInt();
                        int b = buf.getInt();
                        literals.add(new LexerAutomaton.Literal(kind, a, b));
                    }
                    clauses.add(new LexerAutomaton.Clause(literals));
                }
                transitions[state].add(new LexerAutomaton.Transition(target, skip != 0, clauses));
            }
        }
        dfa.transitions = transitions;
        return dfa;
    }

    /**
     * Loads and decodes a blob from a classpath resource (e.g.
     * {@code "/grammars/json/tree-sitter-json-blob.bin"}).
     */
    public static Language fromClasspath(String classpathPath) {
        try (InputStream in = Language.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new TreeSitterException("language blob not found on classpath: " + classpathPath);
            }
            return fromBytes(in.readAllBytes());
        } catch (IOException e) {
            throw new TreeSitterException("failed to read language blob: " + classpathPath, e);
        }
    }

    public int abiVersion() {
        return abiVersion;
    }

    public int stateCount() {
        return stateCount;
    }

    public int largeStateCount() {
        return largeStateCount;
    }

    public int symbolCount() {
        return symbolCount;
    }

    public int aliasCount() {
        return aliasCount;
    }

    public int tokenCount() {
        return tokenCount;
    }

    public int externalTokenCount() {
        return externalTokenCount;
    }

    public int productionIdCount() {
        return productionIdCount;
    }

    public int fieldCount() {
        return fieldCount;
    }

    public int maxAliasSequenceLength() {
        return maxAliasSequenceLength;
    }

    public int keywordCaptureToken() {
        return keywordCaptureToken;
    }

    /**
     * Name of the grammar symbol with the given id; id 0 is the built-in end token.
     * Alias symbols (ids {@code >= symbolCount}) resolve through the alias table too.
     */
    public String symbolName(int symbolId) {
        checkSymbolRange(symbolId);
        return symbolNames[symbolId];
    }

    public boolean symbolVisible(int symbolId) {
        checkSymbolRange(symbolId);
        return (symbolFlags[symbolId] & 0x01) != 0;
    }

    public boolean symbolNamed(int symbolId) {
        checkSymbolRange(symbolId);
        return (symbolFlags[symbolId] & 0x02) != 0;
    }

    public boolean symbolSupertype(int symbolId) {
        checkSymbolRange(symbolId);
        return (symbolFlags[symbolId] & 0x04) != 0;
    }

    /**
     * Symbol id for a symbol name, or -1 when no visible symbol with the given
     * name and namedness exists (mirrors the C runtime's
     * {@code ts_language_symbol_for_name}: only visible symbols participate,
     * hidden {@code _}-prefixed aux symbols never match a query type name).
     */
    public int symbolId(String name, boolean named) {
        for (int i = 0; i < symbolCount; i++) {
            if (symbolNamed(i) == named && symbolVisible(i) && name.equals(symbolNames[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Lex state that parse state {@code parseState} must lex with.
     */
    public int lexState(int parseState) {
        if (parseState < 0 || parseState >= stateCount) {
            throw new TreeSitterException("parse state " + parseState + " out of range [0," + stateCount + ")");
        }
        return lexModes[parseState];
    }

    /**
     * External scanner lex state for parse state {@code parseState}; 0 when the
     * grammar has no external scanner (the C runtime's {@code external_lex_state}
     * — the external scan fires only when non-zero).
     */
    public int externalLexState(int parseState) {
        if (parseState < 0 || parseState >= stateCount) {
            throw new TreeSitterException("parse state " + parseState + " out of range [0," + stateCount + ")");
        }
        return externalLexStates[parseState];
    }

    /**
     * Reserved-word set id for parse state {@code parseState}; 0 when the
     * grammar has no reserved-word data (abi &lt; 15 grammars).
     */
    public int reservedWordSetId(int parseState) {
        if (parseState < 0 || parseState >= stateCount) {
            throw new TreeSitterException("parse state " + parseState + " out of range [0," + stateCount + ")");
        }
        return reservedWordSetIds[parseState];
    }

    /**
     * True when the parse state's reserved-word set admits {@code symbol} (abi
     * &ge; 15 semantics). A keyword in the reserved-word set must stay a keyword
     * and cannot be reinterpreted as an identifier in this state; grammars
     * without reserved-word data always return false.
     */
    public boolean isReservedWord(int parseState, int symbol) {
        if (parseState < 0 || parseState >= stateCount) {
            throw new TreeSitterException("parse state " + parseState + " out of range [0," + stateCount + ")");
        }
        int setId = reservedWordSetIds[parseState];
        if (setId == 0 || setId >= reservedWords.length) {
            return false;
        }
        for (int reserved : reservedWords[setId]) {
            if (reserved == symbol) {
                return true;
            }
        }
        return false;
    }

    /**
     * External scanner symbol map: external token ordinal -> symbol id (C
     * {@code ts_external_scanner_symbol_map}). Empty when the grammar has no
     * external scanner.
     */
    public int[] externalSymbolMap() {
        return externalScannerSymbolMap;
    }

    /**
     * External scanner states matrix: external lex state -> per-ordinal validity
     * (C {@code ts_external_scanner_states}). Empty when the grammar has no
     * external scanner.
     */
    public boolean[][] externalStates() {
        return externalScannerStates;
    }

    /**
     * Reserved-word set for a set id (C {@code ts_reserved_words}), or an empty
     * array for the null set / out-of-range id.
     */
    public int[] reservedWordSet(int setId) {
        if (setId < 0 || setId >= reservedWords.length) {
            return new int[0];
        }
        return reservedWords[setId];
    }

    /**
     * The compiled external-scanner bytecode program (the roadmap ISA documented
     * in {@code blob-format.md} section 20); empty when the grammar has no
     * external scanner.
     */
    public byte[] scannerProgram() {
        return scannerProgram;
    }

    /**
     * Keyword lex state for parse state {@code parseState}; 0 when the grammar
     * has no keyword lexer (e.g. the shipped JSON grammar).
     */
    public int keywordLexState(int parseState) {
        if (parseState < 0 || parseState >= stateCount) {
            throw new TreeSitterException("parse state " + parseState + " out of range [0," + stateCount + ")");
        }
        if (keywordLexModes.length == 0) {
            return 0;
        }
        return keywordLexModes[parseState];
    }

    /**
     * Raw parse-table cell for {@code (state, symbol)}: an action-group index for
     * terminals and a goto state id for non-terminals, or 0 when the table has no
     * entry for that symbol.
     */
    public int tableCell(int state, int symbol) {
        if (state < 0 || state >= stateCount) {
            throw new TreeSitterException("parse state " + state + " out of range [0," + stateCount + ")");
        }
        if (symbol < 0 || symbol >= symbolCount) {
            throw new TreeSitterException("symbol id " + symbol + " out of range [0," + symbolCount + ")");
        }
        if (state < largeStateCount) {
            return largeParseTable[state * symbolCount + symbol];
        }
        int offset = smallParseTableMap[state - largeStateCount];
        int groupCount = smallParseTable[offset];
        int p = offset + 1;
        for (int i = 0; i < groupCount; i++) {
            int value = smallParseTable[p++];
            int count = smallParseTable[p++];
            for (int j = 0; j < count; j++) {
                if (smallParseTable[p] == symbol) {
                    return value;
                }
                p++;
            }
        }
        return 0;
    }

    /**
     * True when the parse table has any entry for {@code (state, symbol)} — the
     * runtime's {@code ts_language_has_actions} check used by keyword capture.
     */
    public boolean hasActions(int state, int symbol) {
        return tableCell(state, symbol) != 0;
    }

    /**
     * The parse action group registered under {@code index}, or null if the blob
     * declares no group with that index (a table cell of 0 means "no entry").
     */
    public ActionGroup actionGroup(int index) {
        return groupByIndex.get(index);
    }

    /** Field name for a field id, or null for id 0. */
    public String fieldName(int fieldId) {
        if (fieldId < 0 || fieldId >= fieldNames.length) {
            throw new TreeSitterException("field id " + fieldId + " out of range [0," + fieldNames.length + ")");
        }
        return fieldNames[fieldId];
    }

    /**
     * Field id for a field name, or 0 when the grammar declares no such field
     * (0 is the "no field" sentinel).
     */
    public int fieldId(String name) {
        for (int i = 1; i < fieldNames.length; i++) {
            if (name.equals(fieldNames[i])) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The field map for a production id (the child slots that carry a field
     * name), or an empty array when the production has no fields.
     */
    public FieldMapEntry[] fieldMap(int productionId) {
        if (productionId < 0 || productionId >= fieldMapSlices.length) {
            throw new TreeSitterException("production id " + productionId
                    + " out of range [0," + fieldMapSlices.length + ")");
        }
        FieldMapSlice slice = fieldMapSlices[productionId];
        if (slice.length() == 0) {
            return new FieldMapEntry[0];
        }
        FieldMapEntry[] result = new FieldMapEntry[slice.length()];
        System.arraycopy(fieldMapEntries, slice.index(), result, 0, slice.length());
        return result;
    }

    /**
     * The alias symbol applied to the child at structural index {@code childIndex}
     * of a node reduced with {@code productionId}; 0 when the child is not aliased.
     */
    public int aliasAt(int productionId, int childIndex) {
        if (productionId == 0 || productionId >= aliasSequences.length) {
            return 0;
        }
        if (childIndex < 0 || childIndex >= maxAliasSequenceLength) {
            return 0;
        }
        return aliasSequences[productionId][childIndex];
    }

    public int[] nonTerminalAliasMap() {
        return nonTerminalAliasMap;
    }

    /** The main lexer automaton decoded from {@code ts_lex}. */
    public LexerAutomaton lexerAutomaton() {
        return lexer;
    }

    /** The keyword lexer automaton decoded from {@code ts_lex_keywords}, or null. */
    public LexerAutomaton keywordLexerAutomaton() {
        return keywordLexer;
    }

    // --- package-visible accessors for tests and diagnostics -----------------

    String[] symbolNames() {
        return symbolNames;
    }

    ActionGroup[] parseActionGroups() {
        return parseActionGroups;
    }

    int[] largeParseTable() {
        return largeParseTable;
    }

    int[] smallParseTable() {
        return smallParseTable;
    }

    int[] smallParseTableMap() {
        return smallParseTableMap;
    }

    int[] primaryStateIds() {
        return primaryStateIds;
    }

    int[] lexModes() {
        return lexModes;
    }

    int[] keywordLexModes() {
        return keywordLexModes;
    }

    int[][] aliasSequences() {
        return aliasSequences;
    }

    String[] fieldNames() {
        return fieldNames;
    }

    private void checkSymbolRange(int symbolId) {
        if (symbolId < 0 || symbolId >= symbolCount + aliasCount) {
            throw new TreeSitterException("symbol id " + symbolId + " out of range [0,"
                    + (symbolCount + aliasCount) + ")");
        }
    }

    /**
     * A single parse action decoded from the blob (§5 of the format document).
     * Fields are interpreted by action type: shift ({@code state}, {@code extra},
     * {@code repetition}), reduce ({@code symbol}, {@code childCount},
     * {@code dynamicPrecedence}, {@code productionId}), accept / recover (no payload).
     */
    public record Action(int type, int state, int symbol, int childCount,
                         int dynamicPrecedence, int productionId,
                         boolean extra, boolean repetition) {

        public static final int SHIFT = 0;
        public static final int REDUCE = 1;
        public static final int ACCEPT = 2;
        public static final int RECOVER = 3;
    }

    public record ActionGroup(int index, int count, boolean reusable, Action[] actions) {
    }

    public record FieldMapSlice(int index, int length) {
    }

    public record FieldMapEntry(int fieldId, int childIndex, boolean inherited) {
    }

    /**
     * A lexer DFA decoded from a {@code ts_lex} / {@code ts_lex_keywords} function
     * body, with ordered transitions and per-state accepts.
     */
    public static final class LexerAutomaton {
        public int stateCount;
        public int[] acceptSymbol;
        public boolean[] acceptAtEntry;
        public List<Transition>[] transitions;
        public List<int[]>[] charSets;

        public int acceptCount() {
            int n = 0;
            for (int sym : acceptSymbol) {
                if (sym >= 0) {
                    n++;
                }
            }
            return n;
        }

        public record Transition(int targetState, boolean skip, List<Clause> clauses) {
        }

        public record Clause(List<Literal> literals) {
        }

        public record Literal(int kind, int a, int b) {

            public static final int CHAR_EQ = 1;
            public static final int CHAR_NEQ = 2;
            public static final int RANGE = 3;
            public static final int SET = 4;
            public static final int NONZERO = 5;
            public static final int EOF = 6;
        }
    }
}