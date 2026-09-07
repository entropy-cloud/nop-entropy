package io.nop.treesitter.codegen;

import java.util.Map;

/**
 * Structured data extracted from an upstream tree-sitter {@code parser.c}.
 *
 * <p>Mirrors the {@code TSLanguage} structure of the C runtime: symbol table,
 * symbol metadata, parse actions, parse state table (large + small), primary
 * state ids, lex modes and keyword lex modes. Field maps, alias sequences and
 * the public symbol map are also captured so that every table referenced by the
 * language initializer is either extracted or diagnosed (no silent skip).</p>
 *
 * <p>Only the tables required by the blob loader contract are serialized by
 * {@link BlobWriter}; the rest are retained here so extraction coverage is
 * testable against the vendored {@code parser.c}.</p>
 */
public final class ExtractedGrammar {

    public int languageVersion;
    public int stateCount;
    public int largeStateCount;
    public int symbolCount;
    public int aliasCount;
    public int tokenCount;
    public int externalTokenCount;
    public int fieldCount;
    public int productionIdCount;
    public int maxAliasSequenceLength;

    public String[] symbolNames;
    public SymbolMeta[] symbolMetadata;
    public int[] symbolMap;
    public String[] fieldNames;
    public FieldMapSlice[] fieldMapSlices;
    public FieldMapEntry[] fieldMapEntries;
    public int[][] aliasSequences;
    public int[] nonTerminalAliasMap;
    public int[] primaryStateIds;

    public int[][] parseTable;
    public int[] smallParseTable;
    public int[] smallParseTableMap;

    public ParseActionGroup[] parseActions;

    public LexMode[] lexModes;
    public LexMode[] keywordLexModes;

    /** lex state id -> accepted symbol id, decoded from the {@code ts_lex} function body. */
    public Map<Integer, Integer> lexStateAcceptSymbol;
    /** keyword lex state id -> accepted symbol id, decoded from {@code ts_lex_keywords}. */
    public Map<Integer, Integer> keywordLexStateAcceptSymbol;

    public record SymbolMeta(boolean visible, boolean named, boolean supertype) {
    }

    public record FieldMapSlice(int index, int length) {
    }

    public record FieldMapEntry(int fieldId, int childIndex, boolean inherited) {
    }

    public record LexMode(int lexState, int externalLexState, int reservedWordSetId) {
    }

    public record ParseAction(int type, int state, int symbol, int childCount,
                              int dynamicPrecedence, int productionId,
                              boolean extra, boolean repetition) {

        public static final int SHIFT = 0;
        public static final int REDUCE = 1;
        public static final int ACCEPT = 2;
        public static final int RECOVER = 3;
    }

    public record ParseActionGroup(int index, int count, boolean reusable, ParseAction[] actions) {
    }
}
