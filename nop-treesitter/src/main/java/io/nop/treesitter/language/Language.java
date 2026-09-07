package io.nop.treesitter.language;

import io.nop.treesitter.TreeSitterException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime loader for the parse-table binary blob documented in
 * {@code src/main/resources/blob-format.md}.
 *
 * <p>Decodes all nine sections — symbol table, symbol metadata, parse action
 * groups, large + small parse tables, small-table map, primary state ids,
 * lex modes and keyword lex modes — and validates the magic, format version and
 * cross-section counts before exposing them to the lexer and parser. The decode
 * is an independent implementation of the format document (the codegen-side
 * {@code BlobReader} is deliberately kept separate so the two implementations
 * cross-check each other).</p>
 *
 * <p>Format violations (wrong magic, unsupported format version, truncated or
 * trailing data, inconsistent counts) raise {@link IllegalStateException}
 * instead of silently defaulting, per the format document's reader contract.</p>
 */
public final class Language {

    public static final int FORMAT_VERSION = 1;
    public static final int INITIAL_STATE = 1;

    private final int abiVersion;
    private final int stateCount;
    private final int largeStateCount;
    private final int symbolCount;
    private final int tokenCount;

    private final String[] symbolNames;
    private final byte[] symbolFlags;
    private final ActionGroup[] parseActionGroups;
    private final Map<Integer, ActionGroup> groupByIndex;
    private final int[] largeParseTable;
    private final int[] smallParseTable;
    private final int[] smallParseTableMap;
    private final int[] primaryStateIds;
    private final int[] lexModes;
    private final int[] keywordLexModes;

    private Language(int abiVersion, int stateCount, int largeStateCount, int symbolCount, int tokenCount,
                     String[] symbolNames, byte[] symbolFlags, ActionGroup[] parseActionGroups,
                     int[] largeParseTable, int[] smallParseTable, int[] smallParseTableMap,
                     int[] primaryStateIds, int[] lexModes, int[] keywordLexModes) {
        this.abiVersion = abiVersion;
        this.stateCount = stateCount;
        this.largeStateCount = largeStateCount;
        this.symbolCount = symbolCount;
        this.tokenCount = tokenCount;
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
        this.keywordLexModes = keywordLexModes;
    }

    /**
     * Decodes a blob from raw bytes. Throws {@link IllegalStateException} for
     * every format violation; never returns a partially initialized language.
     */
    public static Language fromBytes(byte[] blob) {
        if (blob.length < 64) {
            throw new IllegalStateException("blob truncated: " + blob.length + " bytes, header needs 64");
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
        buf.getShort(); // production id count
        buf.getShort(); // field count
        int parseActionGroupCount = buf.getShort() & 0xFFFF;
        int smallParseTableWordCount = buf.getShort() & 0xFFFF;
        int smallParseTableMapCount = buf.getShort() & 0xFFFF;
        int lexModeCount = buf.getShort() & 0xFFFF;
        int keywordLexModeCount = buf.getShort() & 0xFFFF;
        int primaryStateIdCount = buf.getShort() & 0xFFFF;
        for (int i = 0; i < 32; i++) {
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

        String[] symbolNames = new String[symbolCount];
        for (int i = 0; i < symbolCount; i++) {
            int len = buf.get() & 0xFF;
            byte[] name = new byte[len];
            buf.get(name);
            symbolNames[i] = new String(name, StandardCharsets.UTF_8);
        }
        if (symbolCount > 0 && !"end".equals(symbolNames[0])) {
            throw new IllegalStateException("symbol 0 must be the built-in end token, got: " + symbolNames[0]);
        }

        byte[] symbolFlags = new byte[symbolCount];
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
        for (int i = 0; i < lexModes.length; i++) {
            lexModes[i] = buf.getShort() & 0xFFFF;
        }

        int[] keywordLexModes = new int[keywordLexModeCount];
        for (int i = 0; i < keywordLexModes.length; i++) {
            keywordLexModes[i] = buf.getShort() & 0xFFFF;
        }

        if (buf.hasRemaining()) {
            throw new IllegalStateException("blob has trailing bytes: " + buf.remaining());
        }
        return new Language(abiVersion, stateCount, largeStateCount, symbolCount, tokenCount,
                symbolNames, symbolFlags, groups, largeParseTable, smallParseTable, smallParseTableMap,
                primaryStateIds, lexModes, keywordLexModes);
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

    public int tokenCount() {
        return tokenCount;
    }

    /**
     * Name of the grammar symbol with the given id; id 0 is the built-in end token.
     */
    public String symbolName(int symbolId) {
        if (symbolId < 0 || symbolId >= symbolCount) {
            throw new TreeSitterException("symbol id " + symbolId + " out of range [0," + symbolCount + ")");
        }
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
     * The parse action group registered under {@code index}, or null if the blob
     * declares no group with that index (a table cell of 0 means "no entry").
     */
    public ActionGroup actionGroup(int index) {
        return groupByIndex.get(index);
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

    private void checkSymbolRange(int symbolId) {
        if (symbolId < 0 || symbolId >= symbolCount) {
            throw new TreeSitterException("symbol id " + symbolId + " out of range [0," + symbolCount + ")");
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
}