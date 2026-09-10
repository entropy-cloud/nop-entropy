package io.nop.treesitter.codegen;

import java.util.List;
import java.util.Map;

/**
 * Structured data extracted from an upstream tree-sitter {@code parser.c}.
 *
 * <p>Mirrors the {@code TSLanguage} structure of the C runtime: symbol table,
 * symbol metadata, parse actions, parse state table (large + small), primary
 * state ids, lex modes, keyword lex modes, field names, field maps, alias
 * sequences, the non-terminal alias map and the lexer automata (decoded from
 * the {@code ts_lex} / {@code ts_lex_keywords} function bodies). Everything
 * the language initializer references is either extracted or diagnosed (no
 * silent skip).</p>
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
    public int maxReservedWordSetSize;
    public int supertypeCount;

    public String[] symbolNames;
    public SymbolMeta[] symbolMetadata;
    public int[] symbolMap;
    public String[] fieldNames;
    public FieldMapSlice[] fieldMapSlices;
    public FieldMapEntry[] fieldMapEntries;
    public int[][] aliasSequences;
    public int[] nonTerminalAliasMap;
    public int[] primaryStateIds;

    /**
     * External scanner tables (v15 ABI additions): {@code ts_external_scanner_symbol_map}
     * (external token ordinal -> symbol id), {@code ts_external_scanner_states}
     * (external lex state -> boolean per external token ordinal) and
     * {@code ts_reserved_words} (reserved word set id -> symbol ids).
     */
    public int[] externalScannerSymbolMap;
    public boolean[][] externalScannerStates;
    public int[][] reservedWords;

    /** Supertype tables (v15 ABI additions, used by the query engine — roadmap item 8). */
    public int[] supertypeMapSlices;
    public int[] supertypeMapEntries;
    public int[] supertypeSymbols;

    /**
     * Compiled external-scanner bytecode program (roadmap ISA, documented in
     * {@code blob-format.md}); empty when the grammar has no external scanner.
     */
    public byte[] scannerProgram = new byte[0];

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

    /** Symbol id that the main lexer produces for a bare word; the keyword lexer re-lexes it. */
    public int keywordCaptureToken;

    /** Full DFA decoded from the {@code ts_lex} function body. */
    public LexerAutomaton lexer;
    /** Full DFA decoded from the {@code ts_lex_keywords} function body, or null. */
    public LexerAutomaton keywordLexer;
    /** {@code TSCharacterRange} arrays from the generated lexer: set id -> sorted ranges. */
    public List<int[]>[] lexerCharSets;

    /**
     * Symbol flags decoded from the C {@code ts_symbol_metadata} table.
     */
    public record SymbolMeta(boolean visible, boolean named, boolean supertype) {
    }

    /**
     * Slice into the field-map entries owned by one production.
     */
    public record FieldMapSlice(int index, int length) {
    }

    /**
     * One field-map entry: field id, child index, inherited flag.
     */
    public record FieldMapEntry(int fieldId, int childIndex, boolean inherited) {
    }

    /**
     * Per-parse-state lexing mode: internal DFA state, external-scanner state,
     * reserved-word set.
     */
    public record LexMode(int lexState, int externalLexState, int reservedWordSetId) {
    }

    /**
     * One decoded parse action; fields are interpreted by {@code type}
     * (shift / reduce / accept / recover).
     */
    public record ParseAction(int type, int state, int symbol, int childCount,
                              int dynamicPrecedence, int productionId,
                              boolean extra, boolean repetition) {

        public static final int SHIFT = 0;
        public static final int REDUCE = 1;
        public static final int ACCEPT = 2;
        public static final int RECOVER = 3;
    }

    /**
     * An action group keyed by its table index; {@code reusable} mirrors the C
     * table's is_reusable bit used by incremental node reuse.
     */
    public record ParseActionGroup(int index, int count, boolean reusable, ParseAction[] actions) {
    }

    /**
     * A lexer DFA decoded mechanically from a {@code ts_lex} / {@code ts_lex_keywords}
     * function body: states, per-state accepts and ordered transitions.
     */
    public static final class LexerAutomaton {

        /** Number of states (max state id + 1). */
        public int stateCount;
        /** Accepted symbol per state, -1 when the state does not accept. */
        public int[] acceptSymbol;
        /** True when the accept fires at state entry (before transitions), false at state exit. */
        public boolean[] acceptAtEntry;
        /** Ordered transitions per state. */
        public List<Transition>[] transitions;
        /** Character sets referenced by SET literals: set id -> sorted (start,end) ranges. */
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

        public int transitionCount() {
            int n = 0;
            for (List<Transition> ts : transitions) {
                n += ts.size();
            }
            return n;
        }

        public int setRangeCount() {
            int n = 0;
            for (List<int[]> set : charSets) {
                n += set.size();
            }
            return n;
        }

        /**
         * One ordered transition: when the condition matches, consume the lookahead
         * (unless {@code skip}) and move to {@code targetState}. With {@code skip},
         * consumed characters become token padding (C {@code SKIP} macro).
         */
        public record Transition(int targetState, boolean skip, List<Clause> clauses) {
        }

        /** One OR clause of a condition: the clause matches when every literal matches. */
        public record Clause(List<Literal> literals) {
        }

        /**
     * One lexer-DFA matcher literal (kind-interpreted bounds).
     */
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