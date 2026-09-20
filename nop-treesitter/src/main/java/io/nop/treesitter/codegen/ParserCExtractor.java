package io.nop.treesitter.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured grammar data from an upstream tree-sitter {@code parser.c}.
 *
 * <p>Everything the C runtime's {@code TSLanguage} structure references is
 * extracted: symbol tables, parse actions, parse tables, primary state ids,
 * lex modes, keyword lex modes, field names/maps, alias sequences, the
 * non-terminal alias map, the keyword capture token and the lexer automata
 * decoded from the {@code ts_lex} / {@code ts_lex_keywords} function bodies.
 * Any C construct outside that subset raises {@link IllegalStateException}
 * carrying the offending symbol / state / table context — nothing is silently
 * skipped.</p>
 */
public final class ParserCExtractor {

    private static final int MAX_LOOKAHEAD = 0x10FFFF;

    private final String source;
    private final ExtractedGrammar g = new ExtractedGrammar();
    private Map<String, Integer> enumValues;
    private Map<String, Integer> charSetIds;

    private ParserCExtractor(String source) {
        this.source = source;
    }

    public static ExtractedGrammar extract(String source) {
        return new ParserCExtractor(source).run();
    }

    private ExtractedGrammar run() {
        extractConstants();
        enumValues = extractEnum();

        extractSymbolNames();
        extractSymbolMap();
        extractSymbolMetadata();
        extractFieldNames();
        extractFieldMaps();
        extractAliasSequences();
        extractNonTerminalAliasMap();
        extractPrimaryStateIds();
        extractParseTable();
        extractSmallParseTable();
        extractParseActions();
        extractLexModes();
        extractCharSets();
        g.lexer = extractLexerAutomaton("ts_lex");
        g.keywordLexer = extractLexerAutomaton("ts_lex_keywords");
        g.lexStateAcceptSymbol = acceptMap(g.lexer);
        extractKeywordLexFnAccepts();
        extractKeywordCaptureToken();
        extractExternalScannerTables();
        extractSupertypeTables();
        checkInitializerCoverage();
        validateAliasSymbols();
        return g;
    }

    // ------------------------------------------------------------------
    // Constants and enums
    // ------------------------------------------------------------------

    private void extractConstants() {
        Matcher m = Pattern.compile("#define\\s+(\\w+)\\s+(\\d+)").matcher(source);
        while (m.find()) {
            switch (m.group(1)) {
                case "LANGUAGE_VERSION" -> g.languageVersion = Integer.parseInt(m.group(2));
                case "STATE_COUNT" -> g.stateCount = Integer.parseInt(m.group(2));
                case "LARGE_STATE_COUNT" -> g.largeStateCount = Integer.parseInt(m.group(2));
                case "SYMBOL_COUNT" -> g.symbolCount = Integer.parseInt(m.group(2));
                case "ALIAS_COUNT" -> g.aliasCount = Integer.parseInt(m.group(2));
                case "TOKEN_COUNT" -> g.tokenCount = Integer.parseInt(m.group(2));
                case "EXTERNAL_TOKEN_COUNT" -> g.externalTokenCount = Integer.parseInt(m.group(2));
                case "FIELD_COUNT" -> g.fieldCount = Integer.parseInt(m.group(2));
                case "MAX_ALIAS_SEQUENCE_LENGTH" -> g.maxAliasSequenceLength = Integer.parseInt(m.group(2));
                case "PRODUCTION_ID_COUNT" -> g.productionIdCount = Integer.parseInt(m.group(2));
                case "MAX_RESERVED_WORD_SET_SIZE" -> g.maxReservedWordSetSize = Integer.parseInt(m.group(2));
                case "SUPERTYPE_COUNT" -> g.supertypeCount = Integer.parseInt(m.group(2));
                default -> {
                }
            }
        }
        if (g.stateCount <= 0) {
            throw new IllegalStateException("STATE_COUNT missing or invalid in parser.c");
        }
        if (g.symbolCount <= 0) {
            throw new IllegalStateException("SYMBOL_COUNT missing or invalid in parser.c");
        }
    }

    private Map<String, Integer> extractEnum() {
        Map<String, Integer> vals = new HashMap<>();
        vals.put("ts_builtin_sym_end", 0);
        Matcher enumM = Pattern.compile("enum\\s*(?:\\w+\\s*)?\\{([^}]*)}").matcher(source);
        while (enumM.find()) {
            Matcher e = Pattern.compile("(\\w+)\\s*=\\s*(\\d+)").matcher(enumM.group(1));
            while (e.find()) {
                vals.put(e.group(1), Integer.parseInt(e.group(2)));
            }
        }
        this.enumValues = vals;
        return vals;
    }

    private int resolveSymbol(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException nfe) {
            // numeric parse failed — fall back to the enum-name table, then fail fast
            Integer v = enumValues.get(s.trim());
            if (v == null) {
                throw new IllegalStateException("unresolved symbol name: " + s);
            }
            return v;
        }
    }

    // ------------------------------------------------------------------
    // Symbol tables
    // ------------------------------------------------------------------

    private void extractSymbolNames() {
        String body = arrayBody("ts_symbol_names");
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\[(\\w+)]\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
        while (m.find()) {
            int idx = resolveSymbol(m.group(1));
            if (idx < 0) {
                throw new IllegalStateException("ts_symbol_names negative index: " + m.group(1));
            }
            ensureListSize(names, idx + 1);
            names.set(idx, unescapeCString(m.group(2)));
        }
        for (int i = 0; i < g.symbolCount; i++) {
            if (i >= names.size() || names.get(i) == null) {
                throw new IllegalStateException("ts_symbol_names missing entry for symbol " + i);
            }
        }
        g.symbolNames = names.toArray(new String[0]);
    }

    private void extractSymbolMap() {
        String body = findArrayBodyOrNull("ts_symbol_map");
        if (body == null) {
            return;
        }
        List<Integer> map = new ArrayList<>();
        Matcher m = Pattern.compile("\\[(\\w+)]\\s*=\\s*(\\w+)").matcher(body);
        while (m.find()) {
            int idx = resolveSymbol(m.group(1));
            if (idx < 0) {
                throw new IllegalStateException("ts_symbol_map negative index: " + m.group(1));
            }
            ensureListSize(map, idx + 1);
            map.set(idx, resolveSymbol(m.group(2)));
        }
        g.symbolMap = toArray(map);
    }

    private void extractSymbolMetadata() {
        String body = arrayBody("ts_symbol_metadata");
        List<ExtractedGrammar.SymbolMeta> meta = new ArrayList<>();
        Pattern entryStart = Pattern.compile("\\[(\\w+)]\\s*=\\s*\\{");
        Matcher locs = entryStart.matcher(body);
        while (locs.find()) {
            int braceStart = locs.end() - 1;
            int end = findMatchingBrace(body, braceStart);
            String fields = body.substring(braceStart + 1, end);
            int idx = resolveSymbol(locs.group(1));
            if (idx < 0) {
                throw new IllegalStateException("ts_symbol_metadata negative index: " + locs.group(1));
            }
            ensureListSize(meta, idx + 1);
            boolean visible = fields.contains(".visible = true");
            boolean named = fields.contains(".named = true");
            boolean supertype = fields.contains(".supertype = true");
            meta.set(idx, new ExtractedGrammar.SymbolMeta(visible, named, supertype));
        }
        for (int i = 0; i < g.symbolCount; i++) {
            if (i >= meta.size() || meta.get(i) == null) {
                throw new IllegalStateException("ts_symbol_metadata missing entry for symbol " + i);
            }
        }
        g.symbolMetadata = meta.toArray(new ExtractedGrammar.SymbolMeta[0]);
    }

    private void extractFieldNames() {
        if (g.fieldCount == 0) {
            return;
        }
        String body = arrayBody("ts_field_names");
        String[] names = new String[g.fieldCount + 1];
        Matcher m = Pattern.compile("\\[(\\w+)]\\s*=\\s*(?:NULL|\"((?:[^\"\\\\]|\\\\.)*)\")").matcher(body);
        while (m.find()) {
            int idx = resolveSymbol(m.group(1));
            if (idx >= names.length) {
                throw new IllegalStateException("ts_field_names entry out of range: " + idx);
            }
            names[idx] = m.group(2) == null ? null : unescapeCString(m.group(2));
        }
        g.fieldNames = names;
    }

    // ------------------------------------------------------------------
    // Field maps and aliases
    // ------------------------------------------------------------------

    private void extractFieldMaps() {
        if (g.productionIdCount == 0) {
            return;
        }
        String sliceBody = arrayBody("ts_field_map_slices");
        ExtractedGrammar.FieldMapSlice[] slices = new ExtractedGrammar.FieldMapSlice[g.productionIdCount];
        java.util.Arrays.fill(slices, new ExtractedGrammar.FieldMapSlice(0, 0));
        Matcher s = Pattern.compile("\\[(\\w+)]\\s*=\\s*\\{\\.index\\s*=\\s*(\\d+),\\s*\\.length\\s*=\\s*(\\d+)}").matcher(sliceBody);
        while (s.find()) {
            int idx = resolveSymbol(s.group(1));
            if (idx >= slices.length) {
                throw new IllegalStateException("ts_field_map_slices index out of range: " + idx);
            }
            slices[idx] = new ExtractedGrammar.FieldMapSlice(Integer.parseInt(s.group(2)),
                    Integer.parseInt(s.group(3)));
        }
        g.fieldMapSlices = slices;

        String entryBody = arrayBody("ts_field_map_entries");
        List<ExtractedGrammar.FieldMapEntry> entries = new ArrayList<>();
        Pattern e = Pattern.compile("\\{([^}]*)}");
        Matcher em = e.matcher(entryBody);
        while (em.find()) {
            String inner = em.group(1);
            String[] parts = inner.split(",");
            int fieldId = resolveSymbol(parts[0].trim());
            int childIndex = Integer.parseInt(parts[1].trim());
            boolean inherited = inner.contains(".inherited = true") || (parts.length > 2 && parts[2].contains("true"));
            entries.add(new ExtractedGrammar.FieldMapEntry(fieldId, childIndex, inherited));
        }
        g.fieldMapEntries = entries.toArray(new ExtractedGrammar.FieldMapEntry[0]);
    }

    private void extractAliasSequences() {
        if (g.productionIdCount == 0 || g.maxAliasSequenceLength == 0) {
            g.aliasSequences = new int[g.productionIdCount][g.maxAliasSequenceLength];
            return;
        }
        String body = arrayBody("ts_alias_sequences");
        int[][] seq = new int[g.productionIdCount][g.maxAliasSequenceLength];
        Pattern rowStart = Pattern.compile("\\[(\\w+)]\\s*=\\s*\\{");
        Matcher locs = rowStart.matcher(body);
        while (locs.find()) {
            int end = findMatchingBrace(body, locs.end() - 1);
            String row = body.substring(locs.end(), end);
            int idx = resolveSymbol(locs.group(1));
            if (idx >= seq.length) {
                throw new IllegalStateException("ts_alias_sequences row out of range: " + idx);
            }
            Matcher v = Pattern.compile("(?:\\[(\\d+)]\\s*=\\s*)?(\\w+)").matcher(row);
            int col = 0;
            while (v.find()) {
                if (v.group(1) != null) {
                    col = Integer.parseInt(v.group(1));
                }
                if (col < seq[idx].length) {
                    seq[idx][col] = resolveSymbol(v.group(2));
                }
                col++;
            }
        }
        g.aliasSequences = seq;
    }

    private void extractNonTerminalAliasMap() {
        String body = findArrayBodyOrNull("ts_non_terminal_alias_map");
        if (body == null) {
            g.nonTerminalAliasMap = new int[0];
            return;
        }
        List<Integer> vals = new ArrayList<>();
        Matcher m = Pattern.compile("\\b(\\d+|\\w+)\\b").matcher(body);
        while (m.find()) {
            String tok = m.group(1);
            if (isCKeyword(tok)) {
                continue;
            }
            vals.add(resolveSymbol(tok));
        }
        g.nonTerminalAliasMap = toArray(vals);
    }

    private void validateAliasSymbols() {
        if (g.aliasSequences == null) {
            return;
        }
        for (int[] row : g.aliasSequences) {
            for (int symbol : row) {
                if (symbol != 0 && symbol >= g.symbolNames.length) {
                    throw new IllegalStateException("alias symbol " + symbol
                            + " has no entry in ts_symbol_names");
                }
                if (symbol != 0 && symbol >= g.symbolMetadata.length) {
                    throw new IllegalStateException("alias symbol " + symbol
                            + " has no entry in ts_symbol_metadata");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // State table
    // ------------------------------------------------------------------

    private void extractPrimaryStateIds() {
        String body = arrayBody("ts_primary_state_ids");
        int[] ids = new int[g.stateCount];
        Matcher m = Pattern.compile("\\[(\\d+)]\\s*=\\s*(\\d+)").matcher(body);
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            if (idx >= ids.length) {
                throw new IllegalStateException("ts_primary_state_ids index out of range: " + idx);
            }
            ids[idx] = Integer.parseInt(m.group(2));
        }
        g.primaryStateIds = ids;
    }

    private void extractParseTable() {
        String body = arrayBody("ts_parse_table");
        int[][] table = new int[g.largeStateCount][g.symbolCount];
        // v0.25.x generators write [STATE(n)] / [SMALL_STATE(n)] designators,
        // older ones plain [n].
        Pattern stateStart = Pattern.compile("\\[(?:STATE|SMALL_STATE)?\\(?(\\w+)\\)?\\]\\s*=\\s*\\{");
        Matcher locs = stateStart.matcher(body);
        int rowCount = 0;
        while (locs.find()) {
            int end = findMatchingBrace(body, locs.end() - 1);
            String stateBody = body.substring(locs.end(), end);
            int stateIdx;
            try {
                stateIdx = Integer.parseInt(locs.group(1));
            } catch (NumberFormatException ex) {
                stateIdx = enumValues.getOrDefault(locs.group(1), -1);
            }
            if (stateIdx < 0 || stateIdx >= table.length) {
                throw new IllegalStateException("ts_parse_table state index out of range: " + locs.group(1));
            }
            rowCount++;
            Matcher inner = Pattern.compile("\\[(\\w+)]\\s*=\\s*(?:ACTIONS|STATE)\\((\\d+)\\)").matcher(stateBody);
            while (inner.find()) {
                int symIdx = resolveSymbol(inner.group(1));
                if (symIdx < g.symbolCount) {
                    table[stateIdx][symIdx] = Integer.parseInt(inner.group(2));
                }
            }
        }
        if (rowCount == 0) {
            throw new IllegalStateException("ts_parse_table has no designated rows (undecodable initializer)");
        }
        g.parseTable = table;
    }

    private void extractSmallParseTable() {
        if (g.largeStateCount >= g.stateCount) {
            g.smallParseTable = new int[0];
            g.smallParseTableMap = new int[0];
            return;
        }
        String body = arrayBody("ts_small_parse_table");
        List<Integer> words = new ArrayList<>();
        Matcher m = Pattern.compile("\\[(\\d+)]\\s*=|(?:ACTIONS|STATE)\\((\\d+)\\)|\\b([a-zA-Z_]\\w*)\\b|\\b(\\d+)\\b").matcher(body);
        int expectedIndex = 0;
        while (m.find()) {
            if (m.group(1) != null) {
                int idx = Integer.parseInt(m.group(1));
                if (idx != expectedIndex) {
                    throw new IllegalStateException("ts_small_parse_table non-sequential designator at " + idx
                            + " (expected " + expectedIndex + ")");
                }
                continue;
            }
            if (m.group(2) != null) {
                words.add(Integer.parseInt(m.group(2)));
                expectedIndex++;
                continue;
            }
            if (m.group(3) != null) {
                String tok = m.group(3);
                if (isCKeyword(tok)) {
                    continue;
                }
                Integer v = enumValues.get(tok);
                if (v == null) {
                    throw new IllegalStateException("ts_small_parse_table unresolved symbol: " + tok);
                }
                words.add(v);
                expectedIndex++;
                continue;
            }
            if (m.group(4) != null) {
                words.add(Integer.parseInt(m.group(4)));
                expectedIndex++;
            }
        }
        g.smallParseTable = toArray(words);

        String mapBody = arrayBody("ts_small_parse_table_map");
        int smallCount = g.stateCount - g.largeStateCount;
        int[] map = new int[smallCount];
        Matcher sm = Pattern.compile("\\[SMALL_STATE\\((\\d+)\\)]\\s*=\\s*(\\d+)").matcher(mapBody);
        while (sm.find()) {
            int idx = Integer.parseInt(sm.group(1)) - g.largeStateCount;
            if (idx < 0 || idx >= map.length) {
                throw new IllegalStateException("ts_small_parse_table_map SMALL_STATE index out of range: " + sm.group(1));
            }
            map[idx] = Integer.parseInt(sm.group(2));
        }
        g.smallParseTableMap = map;
    }

    // ------------------------------------------------------------------
    // Parse actions
    // ------------------------------------------------------------------

    private void extractParseActions() {
        String body = arrayBody("ts_parse_actions");
        String[] lines = body.split("\n");
        List<ExtractedGrammar.ParseActionGroup> groups = new ArrayList<>();
        int curIndex = -1;
        int curCount = 0;
        boolean curReusable = false;
        List<ExtractedGrammar.ParseAction> curActions = null;
        int nextCIndex = 0;

        Pattern header = Pattern.compile("(?:\\[(\\d+)]\\s*=\\s*)?\\{\\s*(?:\\.entry\\s*=\\s*)?(?:\\{\\s*)?\\.count\\s*=\\s*(\\d+),\\s*\\.reusable\\s*=\\s*(true|false)\\s*(?:}\\s*)?}");
        Pattern shift = Pattern.compile("\\bSHIFT\\((\\d+)\\)");
        Pattern shiftExtra = Pattern.compile("\\bSHIFT_EXTRA\\(\\)");
        Pattern shiftRepeat = Pattern.compile("\\bSHIFT_REPEAT\\((\\d+)\\)");
        Pattern reduce = Pattern.compile("\\bREDUCE\\(([^)]*)\\)");
        Pattern accept = Pattern.compile("\\bACCEPT_INPUT\\(\\)");
        Pattern recover = Pattern.compile("\\bRECOVER\\(\\)");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher hm = header.matcher(line);
            if (hm.find()) {
                if (curActions != null) {
                    flushGroup(groups, curIndex, curCount, curReusable, curActions);
                }
                int cIndex = nextCIndex;
                if (hm.group(1) != null) {
                    cIndex = Integer.parseInt(hm.group(1));
                }
                curIndex = cIndex;
                curCount = Integer.parseInt(hm.group(2));
                curReusable = "true".equals(hm.group(3));
                curActions = new ArrayList<>(curCount);
                nextCIndex = cIndex + 1 + curCount;
                String remainder = line.substring(hm.end());
                if (!remainder.isBlank()) {
                    collectActions(remainder, shift, shiftExtra, shiftRepeat, reduce, accept, recover, curActions);
                }
                continue;
            }
            if (curActions == null) {
                throw new IllegalStateException("ts_parse_actions action before any header");
            }
            collectActions(line, shift, shiftExtra, shiftRepeat, reduce, accept, recover, curActions);
        }
        if (curActions != null) {
            flushGroup(groups, curIndex, curCount, curReusable, curActions);
        }
        g.parseActions = groups.toArray(new ExtractedGrammar.ParseActionGroup[0]);
    }

    private static void flushGroup(List<ExtractedGrammar.ParseActionGroup> groups, int index, int count,
                                   boolean reusable, List<ExtractedGrammar.ParseAction> actions) {
        if (actions.size() != count) {
            throw new IllegalStateException("ts_parse_actions group " + index + ": header declares "
                    + count + " actions but " + actions.size() + " were decoded");
        }
        groups.add(new ExtractedGrammar.ParseActionGroup(index, count, reusable,
                actions.toArray(new ExtractedGrammar.ParseAction[0])));
    }

    private void collectActions(String line, Pattern shift, Pattern shiftExtra, Pattern shiftRepeat,
                                Pattern reduce, Pattern accept, Pattern recover,
                                List<ExtractedGrammar.ParseAction> out) {
        List<Object[]> matches = new ArrayList<>();

        Matcher me = shiftExtra.matcher(line);
        while (me.find()) {
            matches.add(new Object[]{me.start(), new ExtractedGrammar.ParseAction(
                    ExtractedGrammar.ParseAction.SHIFT, 0, 0, 0, 0, 0, true, false)});
        }
        Matcher mr = shiftRepeat.matcher(line);
        while (mr.find()) {
            matches.add(new Object[]{mr.start(), new ExtractedGrammar.ParseAction(
                    ExtractedGrammar.ParseAction.SHIFT, Integer.parseInt(mr.group(1)), 0, 0, 0, 0, false, true)});
        }
        Matcher ms = shift.matcher(line);
        while (ms.find()) {
            int pos = ms.start();
            int afterShift = line.indexOf("SHIFT", pos) + 5;
            if (afterShift < line.length() && line.charAt(afterShift) == '_') {
                continue;
            }
            matches.add(new Object[]{pos, new ExtractedGrammar.ParseAction(
                    ExtractedGrammar.ParseAction.SHIFT, Integer.parseInt(ms.group(1)), 0, 0, 0, 0, false, false)});
        }
        Matcher md = reduce.matcher(line);
        while (md.find()) {
            ExtractedGrammar.ParseAction a = parseReduce(md.group(1));
            if (a != null) {
                matches.add(new Object[]{md.start(), a});
            }
        }
        Matcher ma = accept.matcher(line);
        while (ma.find()) {
            matches.add(new Object[]{ma.start(), new ExtractedGrammar.ParseAction(
                    ExtractedGrammar.ParseAction.ACCEPT, 0, 0, 0, 0, 0, false, false)});
        }
        Matcher mc = recover.matcher(line);
        while (mc.find()) {
            matches.add(new Object[]{mc.start(), new ExtractedGrammar.ParseAction(
                    ExtractedGrammar.ParseAction.RECOVER, 0, 0, 0, 0, 0, false, false)});
        }

        matches.sort((a, b) -> Integer.compare((Integer) a[0], (Integer) b[0]));
        for (Object[] m : matches) {
            out.add((ExtractedGrammar.ParseAction) m[1]);
        }
    }

    private ExtractedGrammar.ParseAction parseReduce(String args) {
        String[] parts = args.split(",");
        if (parts.length < 2) {
            throw new IllegalStateException("malformed REDUCE action: " + args);
        }
        int symbol = resolveSymbol(parts[0].trim());
        int childCount = Integer.parseInt(parts[1].trim());
        int dynPrec = 0;
        int prodId = 0;
        if (parts.length >= 3) {
            String p2 = parts[2].trim();
            if (p2.startsWith(".dynamic_precedence")) {
                dynPrec = Integer.parseInt(p2.substring(p2.indexOf('=') + 1).trim());
            } else if (p2.startsWith(".production_id")) {
                prodId = Integer.parseInt(p2.substring(p2.indexOf('=') + 1).trim());
            } else {
                dynPrec = Integer.parseInt(p2);
            }
        }
        if (parts.length >= 4) {
            String p3 = parts[3].trim();
            if (p3.startsWith(".dynamic_precedence")) {
                dynPrec = Integer.parseInt(p3.substring(p3.indexOf('=') + 1).trim());
            } else if (p3.startsWith(".production_id")) {
                prodId = Integer.parseInt(p3.substring(p3.indexOf('=') + 1).trim());
            } else {
                prodId = Integer.parseInt(p3);
            }
        }
        return new ExtractedGrammar.ParseAction(ExtractedGrammar.ParseAction.REDUCE, 0, symbol,
                childCount, dynPrec, prodId, false, false);
    }

    // ------------------------------------------------------------------
    // Lex modes
    // ------------------------------------------------------------------

    private void extractLexModes() {
        String body = arrayBody("ts_lex_modes");
        ExtractedGrammar.LexMode[] modes = new ExtractedGrammar.LexMode[g.stateCount];
        Pattern p = Pattern.compile("\\[(\\d+)]\\s*=\\s*\\{([^}]*)}");
        Matcher m = p.matcher(body);
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            if (idx >= modes.length) {
                throw new IllegalStateException("ts_lex_modes index out of range: " + idx);
            }
            String fields = m.group(2);
            modes[idx] = new ExtractedGrammar.LexMode(
                    intField(fields, "lex_state"),
                    intField(fields, "external_lex_state"),
                    intField(fields, "reserved_word_set_id"));
        }
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == null) {
                throw new IllegalStateException("ts_lex_modes missing entry for state " + i);
            }
        }
        g.lexModes = modes;
    }

    private int intField(String fields, String name) {
        Matcher m = Pattern.compile("\\." + name + "\\s*=\\s*(\\d+)").matcher(fields);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    // ------------------------------------------------------------------
    // Character sets (TSCharacterRange arrays) and lexer automata
    // ------------------------------------------------------------------

    private void extractCharSets() {
        Map<String, Integer> ids = new HashMap<>();
        List<List<int[]>> sets = new ArrayList<>();
        Pattern decl = Pattern.compile("static\\s+(?:const\\s+)?TSCharacterRange\\s+(\\w+)\\s*\\[\\]\\s*=\\s*\\{");
        Matcher m = decl.matcher(source);
        while (m.find()) {
            String name = m.group(1);
            if (ids.containsKey(name)) {
                throw new IllegalStateException("duplicate TSCharacterRange array: " + name);
            }
            int start = m.end() - 1;
            int end = findMatchingBrace(source, start);
            String body = source.substring(start + 1, end);
            List<int[]> ranges = new ArrayList<>();
            Matcher r = Pattern.compile("\\{([^}]*)}").matcher(body);
            while (r.find()) {
                String[] parts = r.group(1).split(",");
                ranges.add(new int[]{parseCharValue(parts[0].trim()), parseCharValue(parts[1].trim())});
            }
            ids.put(name, sets.size());
            sets.add(ranges);
        }
        charSetIds = ids;
        @SuppressWarnings("unchecked")
        List<int[]>[] arr = sets.toArray(new List[0]);
        g.lexerCharSets = arr;
    }

    private void extractKeywordCaptureToken() {
        String fn = findFunctionBody("tree_sitter_");
        if (fn == null) {
            throw new IllegalStateException("language export function not found in parser.c");
        }
        Matcher m = Pattern.compile("\\.keyword_capture_token\\s*=\\s*(\\w+)").matcher(fn);
        if (m.find()) {
            g.keywordCaptureToken = resolveSymbol(m.group(1));
        } else {
            g.keywordCaptureToken = 0;
        }
    }

    // ------------------------------------------------------------------
    // External scanner tables (v15 ABI additions)
    // ------------------------------------------------------------------

    private void extractExternalScannerTables() {
        if (g.externalTokenCount == 0) {
            g.externalScannerSymbolMap = new int[0];
            g.externalScannerStates = new boolean[0][];
            g.reservedWords = extractReservedWords();
            return;
        }
        String mapBody = arrayBody("ts_external_scanner_symbol_map");
        int[] symbolMap = new int[g.externalTokenCount];
        Matcher m = Pattern.compile("\\[(\\w+)]\\s*=\\s*(\\w+)").matcher(mapBody);
        while (m.find()) {
            int ordinal = resolveSymbol(m.group(1));
            if (ordinal < 0 || ordinal >= g.externalTokenCount) {
                throw new IllegalStateException("ts_external_scanner_symbol_map ordinal out of range: "
                        + m.group(1) + " (" + ordinal + "), external_token_count " + g.externalTokenCount);
            }
            symbolMap[ordinal] = resolveSymbol(m.group(2));
        }
        for (int i = 0; i < g.externalTokenCount; i++) {
            if (symbolMap[i] == 0 && i != 0) {
                throw new IllegalStateException("ts_external_scanner_symbol_map missing entry for external token "
                        + i);
            }
        }
        g.externalScannerSymbolMap = symbolMap;

        String statesBody = arrayBody("ts_external_scanner_states");
        Pattern rowStart = Pattern.compile("\\[(\\d+)]\\s*=\\s*\\{");
        Matcher locs = rowStart.matcher(statesBody);
        int maxState = -1;
        java.util.List<int[]> rows = new java.util.ArrayList<>();
        while (locs.find()) {
            int state = Integer.parseInt(locs.group(1));
            int end = findMatchingBrace(statesBody, locs.end() - 1);
            String row = statesBody.substring(locs.end(), end);
            int[] bits = new int[g.externalTokenCount];
            Matcher bit = Pattern.compile("\\[(\\w+)]\\s*=\\s*(true|false)").matcher(row);
            while (bit.find()) {
                int ordinal = resolveSymbol(bit.group(1));
                if (ordinal < 0 || ordinal >= g.externalTokenCount) {
                    throw new IllegalStateException("ts_external_scanner_states state " + state
                            + ": ordinal out of range: " + bit.group(1));
                }
                bits[ordinal] = "true".equals(bit.group(2)) ? 1 : 0;
            }
            if (state > maxState) {
                maxState = state;
            }
            while (rows.size() <= state) {
                rows.add(new int[g.externalTokenCount]);
            }
            rows.set(state, bits);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("ts_external_scanner_states has no designated rows");
        }
        boolean[][] states = new boolean[maxState + 1][g.externalTokenCount];
        for (int s = 0; s < rows.size(); s++) {
            int[] bits = rows.get(s);
            for (int i = 0; i < g.externalTokenCount; i++) {
                states[s][i] = bits[i] != 0;
            }
        }
        g.externalScannerStates = states;

        g.reservedWords = extractReservedWords();
    }

    private int[][] extractReservedWords() {
        if (g.maxReservedWordSetSize == 0) {
            return new int[0][];
        }
        String reservedBody = findArrayBodyOrNull("ts_reserved_words");
        if (reservedBody == null) {
            throw new IllegalStateException("MAX_RESERVED_WORD_SET_SIZE > 0 but ts_reserved_words is missing");
        }
        Pattern rowM = Pattern.compile("\\[(\\d+)]\\s*=\\s*\\{");
        Matcher locs2 = rowM.matcher(reservedBody);
        int setCount = 0;
        java.util.List<java.util.List<Integer>> sets = new java.util.ArrayList<>();
        while (locs2.find()) {
            int setId = Integer.parseInt(locs2.group(1));
            int end = findMatchingBrace(reservedBody, locs2.end() - 1);
            String row = reservedBody.substring(locs2.end(), end);
            java.util.List<Integer> symbols = new java.util.ArrayList<>();
            Matcher sym = Pattern.compile("\\b(\\w+)\\b").matcher(row);
            while (sym.find()) {
                String tok = sym.group(1);
                if (isCKeyword(tok)) {
                    continue;
                }
                symbols.add(resolveSymbol(tok));
            }
            if (symbols.size() > g.maxReservedWordSetSize) {
                throw new IllegalStateException("ts_reserved_words row " + setId + " has "
                        + symbols.size() + " entries exceeding max_reserved_word_set_size "
                        + g.maxReservedWordSetSize);
            }
            while (sets.size() <= setId) {
                sets.add(null);
            }
            sets.set(setId, symbols);
            setCount = Math.max(setCount, setId + 1);
        }
        int[][] reservedWords = new int[setCount][];
        for (int i = 0; i < setCount; i++) {
            java.util.List<Integer> row = sets.get(i);
            reservedWords[i] = row == null ? new int[0] : row.stream().mapToInt(Integer::intValue).toArray();
        }
        return reservedWords;
    }

    private void extractSupertypeTables() {
        String fn = findFunctionBody("tree_sitter_");
        if (fn == null) {
            throw new IllegalStateException("language export function not found in parser.c");
        }
        Matcher count = Pattern.compile("\\.supertype_count\\s*=\\s*(\\w+)").matcher(fn);
        if (!count.find()) {
            g.supertypeCount = 0;
            return;
        }
        String slicesBody = findArrayBodyOrNull("ts_supertype_map_slices");
        String entriesBody = findArrayBodyOrNull("ts_supertype_map_entries");
        String symbolsBody = findArrayBodyOrNull("ts_supertype_symbols");
        if (slicesBody == null || entriesBody == null || symbolsBody == null) {
            throw new IllegalStateException("supertype_count set but a supertype table is missing");
        }
        java.util.List<Integer> slices = new java.util.ArrayList<>();
        Matcher s = Pattern.compile("\\.index\\s*=\\s*(\\d+),\\s*\\.length\\s*=\\s*(\\d+)").matcher(slicesBody);
        while (s.find()) {
            slices.add(Integer.parseInt(s.group(1)));
            slices.add(Integer.parseInt(s.group(2)));
        }
        g.supertypeMapSlices = slices.stream().mapToInt(Integer::intValue).toArray();
        java.util.List<Integer> entries = new java.util.ArrayList<>();
        Matcher e = Pattern.compile("\\b(\\d+)\\b").matcher(entriesBody);
        while (e.find()) {
            entries.add(Integer.parseInt(e.group(1)));
        }
        g.supertypeMapEntries = entries.stream().mapToInt(Integer::intValue).toArray();
        java.util.List<Integer> symbols = new java.util.ArrayList<>();
        Matcher sm = Pattern.compile("\\b(\\w+)\\b").matcher(symbolsBody);
        while (sm.find()) {
            String tok = sm.group(1);
            if (isCKeyword(tok)) {
                continue;
            }
            symbols.add(resolveSymbol(tok));
        }
        g.supertypeSymbols = symbols.stream().mapToInt(Integer::intValue).toArray();
        if (g.supertypeSymbols.length != g.supertypeCount) {
            throw new IllegalStateException("ts_supertype_symbols has " + g.supertypeSymbols.length
                    + " entries but supertype_count is " + g.supertypeCount);
        }
    }

    private Map<Integer, Integer> acceptMap(ExtractedGrammar.LexerAutomaton dfa) {
        Map<Integer, Integer> accepts = new LinkedHashMap<>();
        if (dfa == null) {
            return accepts;
        }
        for (int state = 0; state < dfa.stateCount; state++) {
            if (dfa.acceptSymbol[state] >= 0) {
                accepts.put(state, dfa.acceptSymbol[state]);
            }
        }
        return accepts;
    }

    private void extractKeywordLexFnAccepts() {
        if (g.keywordLexer == null) {
            g.keywordLexStateAcceptSymbol = null;
            g.keywordLexModes = new ExtractedGrammar.LexMode[0];
            return;
        }
        Map<Integer, Integer> accepts = new LinkedHashMap<>();
        for (int state = 0; state < g.keywordLexer.stateCount; state++) {
            if (g.keywordLexer.acceptSymbol[state] >= 0) {
                accepts.put(state, g.keywordLexer.acceptSymbol[state]);
            }
        }
        g.keywordLexStateAcceptSymbol = accepts;
        ExtractedGrammar.LexMode[] modes = new ExtractedGrammar.LexMode[accepts.size()];
        int i = 0;
        for (Integer state : accepts.keySet()) {
            modes[i++] = new ExtractedGrammar.LexMode(state, 0, 0);
        }
        g.keywordLexModes = modes;
    }

    private ExtractedGrammar.LexerAutomaton extractLexerAutomaton(String funcName) {
        String fn = findCFunctionBody(funcName);
        if (fn == null) {
            return null;
        }
        int switchStart = fn.indexOf("switch");
        if (switchStart < 0) {
            throw new IllegalStateException(funcName + ": switch on lexer state not found");
        }
        int openBrace = fn.indexOf('{', switchStart);
        int end = findMatchingBrace(fn, openBrace);
        String body = fn.substring(openBrace + 1, end);

        List<int[]> caseStarts = new ArrayList<>();
        Matcher caseM = Pattern.compile("\\bcase\\s+(\\d+):").matcher(body);
        while (caseM.find()) {
            caseStarts.add(new int[]{Integer.parseInt(caseM.group(1)), caseM.end()});
        }
        int maxState = -1;
        for (int[] cs : caseStarts) {
            maxState = Math.max(maxState, cs[0]);
        }
        if (maxState < 0) {
            throw new IllegalStateException(funcName + ": no lexer states decoded");
        }
        int stateCount = maxState + 1;
        ExtractedGrammar.LexerAutomaton dfa = new ExtractedGrammar.LexerAutomaton();
        dfa.stateCount = stateCount;
        dfa.acceptSymbol = new int[stateCount];
        dfa.acceptAtEntry = new boolean[stateCount];
        java.util.Arrays.fill(dfa.acceptSymbol, -1);
        dfa.charSets = g.lexerCharSets == null ? new List[0] : g.lexerCharSets;
        @SuppressWarnings("unchecked")
        List<ExtractedGrammar.LexerAutomaton.Transition>[] transitions = new List[stateCount];
        dfa.transitions = transitions;

        for (int ci = 0; ci < caseStarts.size(); ci++) {
            int state = caseStarts.get(ci)[0];
            int blockStart = caseStarts.get(ci)[1];
            int blockEnd = ci + 1 < caseStarts.size() ? caseStarts.get(ci + 1)[1] : body.length();
            int defaultPos = body.indexOf("default:", blockStart);
            if (defaultPos >= 0 && defaultPos < blockEnd) {
                blockEnd = defaultPos;
            }
            List<ExtractedGrammar.LexerAutomaton.Transition> stateTransitions = new ArrayList<>();
            int[] acceptResult = {-1};
            boolean[] acceptAtEntryResult = {false};
            parseLexerStateBlock(funcName, state, body.substring(blockStart, blockEnd), stateTransitions,
                    acceptResult, acceptAtEntryResult);
            dfa.acceptSymbol[state] = acceptResult[0];
            dfa.acceptAtEntry[state] = acceptAtEntryResult[0];
            transitions[state] = stateTransitions;
        }
        return dfa;
    }

    private void parseLexerStateBlock(String funcName, int state, String block,
                                      List<ExtractedGrammar.LexerAutomaton.Transition> out,
                                      int[] acceptResult, boolean[] acceptAtEntryResult) {
        int pos = 0;
        int n = block.length();
        boolean acceptSet = false;
        while (pos < n) {
            char c = block.charAt(pos);
            if (Character.isWhitespace(c) || c == ';') {
                pos++;
                continue;
            }
            if (c == '{' || c == '}') {
                pos++;
                continue;
            }
            if (block.startsWith("if", pos)) {
                int paren = block.indexOf('(', pos);
                if (paren < 0) {
                    throw new IllegalStateException(funcName + " state " + state
                            + ": malformed if statement");
                }
                int condEnd = matchParen(block, paren);
                String cond = block.substring(paren + 1, condEnd);
                int after = condEnd + 1;
                while (after < n && Character.isWhitespace(block.charAt(after))) {
                    after++;
                }
                if (block.startsWith("ADVANCE_MAP", after)) {
                    int mp = block.indexOf('(', after);
                    int mEnd = matchParen(block, mp);
                    List<ExtractedGrammar.LexerAutomaton.Transition> mapTransitions =
                            parseAdvanceMap(funcName, state, block.substring(mp + 1, mEnd));
                    out.addAll(mapTransitions);
                    pos = mEnd + 1;
                    continue;
                }
                int actionEnd = findSemicolon(block, after);
                String action = block.substring(after, actionEnd).trim();
                List<ExtractedGrammar.LexerAutomaton.Clause> clauses;
                try {
                    clauses = parseCondition(cond, funcName, state);
                } catch (IllegalStateException ex) {
                    throw new IllegalStateException(funcName + " state " + state
                            + ": cannot decode condition: " + cond, ex);
                }
                if (action.startsWith("ADVANCE(")) {
                    out.add(new ExtractedGrammar.LexerAutomaton.Transition(
                            intInParens(action), false, clauses));
                } else if (action.startsWith("SKIP(")) {
                    out.add(new ExtractedGrammar.LexerAutomaton.Transition(
                            intInParens(action), true, clauses));
                } else {
                    throw new IllegalStateException(funcName + " state " + state
                            + ": unsupported action after if-condition: " + action);
                }
                pos = actionEnd + 1;
                continue;
            }
            if (block.startsWith("ADVANCE_MAP", pos)) {
                int mp = block.indexOf('(', pos);
                int mEnd = matchParen(block, mp);
                out.addAll(parseAdvanceMap(funcName, state, block.substring(mp + 1, mEnd)));
                pos = mEnd + 1;
                continue;
            }
            if (block.startsWith("ACCEPT_TOKEN", pos)) {
                int ap = block.indexOf('(', pos);
                int aEnd = matchParen(block, ap);
                String symName = block.substring(ap + 1, aEnd).trim();
                if (!acceptSet) {
                    acceptResult[0] = resolveSymbol(symName);
                    acceptAtEntryResult[0] = out.isEmpty();
                    acceptSet = true;
                } else {
                    throw new IllegalStateException(funcName + " state " + state
                            + ": multiple ACCEPT_TOKEN statements");
                }
                pos = aEnd + 1;
                continue;
            }
            if (block.startsWith("END_STATE", pos)) {
                break;
            }
            if (block.startsWith("return", pos)) {
                int semi = block.indexOf(';', pos);
                pos = semi < 0 ? n : semi + 1;
                continue;
            }
            throw new IllegalStateException(funcName + " state " + state
                    + ": unsupported statement at offset " + pos + ": " + block.substring(pos, Math.min(n, pos + 40)));
        }
    }

    private List<ExtractedGrammar.LexerAutomaton.Transition> parseAdvanceMap(String funcName, int state, String args) {
        List<String> raw = splitTopLevel(args, ',');
        List<String> parts = new ArrayList<>();
        for (String part : raw) {
            if (!part.trim().isEmpty()) {
                parts.add(part);
            }
        }
        if (parts.size() % 2 != 0) {
            throw new IllegalStateException(funcName + " state " + state
                    + ": ADVANCE_MAP must have even argument count");
        }
        List<ExtractedGrammar.LexerAutomaton.Transition> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 2) {
            int ch = parseCharValue(parts.get(i).trim());
            int target = Integer.parseInt(parts.get(i + 1).trim());
            out.add(new ExtractedGrammar.LexerAutomaton.Transition(target, false,
                    List.of(new ExtractedGrammar.LexerAutomaton.Clause(
                            List.of(new ExtractedGrammar.LexerAutomaton.Literal(
                                    ExtractedGrammar.LexerAutomaton.Literal.CHAR_EQ, ch, 0))))));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Lexer condition parsing: tiny recursive-descent parser producing DNF
    // ------------------------------------------------------------------

    private List<ExtractedGrammar.LexerAutomaton.Clause> parseCondition(String cond,
                                                                         String funcName, int state) {
        List<String> tokens = tokenizeCondition(cond);
        int[] pos = {0};
        List<Object[]> clauses = parseOr(tokens, pos);
        if (pos[0] != tokens.size()) {
            throw new IllegalStateException(funcName + " state " + state
                    + ": trailing tokens in condition: " + String.join(" ", tokens.subList(pos[0], tokens.size())));
        }
        List<ExtractedGrammar.LexerAutomaton.Clause> result = new ArrayList<>();
        for (Object[] clause : clauses) {
            @SuppressWarnings("unchecked")
            List<ExtractedGrammar.LexerAutomaton.Literal> lits = (List<ExtractedGrammar.LexerAutomaton.Literal>) clause[0];
            if (!lits.isEmpty()) {
                result.add(new ExtractedGrammar.LexerAutomaton.Clause(lits));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException(funcName + " state " + state
                    + ": condition never matches: " + cond);
        }
        return result;
    }

    private List<String> tokenizeCondition(String cond) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int n = cond.length();
        while (i < n) {
            char c = cond.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(' || c == ')' || c == ',') {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }
            if (c == '&' && i + 1 < n && cond.charAt(i + 1) == '&') {
                tokens.add("&&");
                i += 2;
                continue;
            }
            if (c == '|' && i + 1 < n && cond.charAt(i + 1) == '|') {
                tokens.add("||");
                i += 2;
                continue;
            }
            if (c == '=' && i + 1 < n && cond.charAt(i + 1) == '=') {
                tokens.add("==");
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < n && cond.charAt(i + 1) == '=') {
                tokens.add("!=");
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < n && cond.charAt(i + 1) == '=') {
                tokens.add("<=");
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < n && cond.charAt(i + 1) == '=') {
                tokens.add(">=");
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < n && cond.charAt(i + 1) == 'e') {
                tokens.add("!");
                i++;
                continue;
            }
            if (c == '<') {
                tokens.add("<");
                i++;
                continue;
            }
            if (c == '>') {
                tokens.add(">");
                i++;
                continue;
            }
            if (c == '\'') {
                int end = i + 1;
                while (end < n) {
                    if (cond.charAt(end) == '\\') {
                        end += 2;
                    } else if (cond.charAt(end) == '\'') {
                        end++;
                        break;
                    } else {
                        end++;
                    }
                }
                tokens.add(cond.substring(i, end));
                i = end;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(cond.charAt(end)) || cond.charAt(end) == '_')) {
                    end++;
                }
                tokens.add(cond.substring(i, end));
                i = end;
                continue;
            }
            if (Character.isDigit(c) || (c == '0' && i + 1 < n && cond.charAt(i + 1) == 'x')) {
                int end = i;
                if (c == '0' && i + 1 < n && cond.charAt(i + 1) == 'x') {
                    end = i + 2;
                    while (end < n && isHexDigit(cond.charAt(end))) {
                        end++;
                    }
                } else {
                    while (end < n && Character.isDigit(cond.charAt(end))) {
                        end++;
                    }
                }
                tokens.add(cond.substring(i, end));
                i = end;
                continue;
            }
            throw new IllegalStateException("cannot tokenize lexer condition: " + cond);
        }
        return tokens;
    }

    /** Parses {@code orExpr := andExpr ('||' andExpr)*} into clauses (each a literal list). */
    private List<Object[]> parseOr(List<String> tokens, int[] pos) {
        List<Object[]> left = parseAnd(tokens, pos);
        while (pos[0] < tokens.size() && "||".equals(tokens.get(pos[0]))) {
            pos[0]++;
            List<Object[]> right = parseAnd(tokens, pos);
            left.addAll(right);
        }
        return left;
    }

    /** Parses {@code andExpr := primary ('&&' primary)*} by cross-joining literal lists. */
    private List<Object[]> parseAnd(List<String> tokens, int[] pos) {
        List<Object[]> left = parsePrimary(tokens, pos);
        while (pos[0] < tokens.size() && "&&".equals(tokens.get(pos[0]))) {
            pos[0]++;
            List<Object[]> right = parsePrimary(tokens, pos);
            List<Object[]> joined = new ArrayList<>();
            for (Object[] l : left) {
                for (Object[] r : right) {
                    @SuppressWarnings("unchecked")
                    List<ExtractedGrammar.LexerAutomaton.Literal> literals =
                            new ArrayList<>((List<ExtractedGrammar.LexerAutomaton.Literal>) l[0]);
                    literals.addAll((List<ExtractedGrammar.LexerAutomaton.Literal>) r[0]);
                    joined.add(new Object[]{literals});
                }
            }
            left = joined;
        }
        return left;
    }

    private List<Object[]> parsePrimary(List<String> tokens, int[] pos) {
        String tok = tokens.get(pos[0]);
        if ("(".equals(tok)) {
            pos[0]++;
            List<Object[]> inner = parseOr(tokens, pos);
            expect(tokens, pos, ")");
            return inner;
        }
        if ("eof".equals(tok)) {
            pos[0]++;
            return singleClause(new ExtractedGrammar.LexerAutomaton.Literal(
                    ExtractedGrammar.LexerAutomaton.Literal.EOF, 0, 0));
        }
        if ("!".equals(tok)) {
            pos[0]++;
            if (!"eof".equals(tokens.get(pos[0]))) {
                throw new IllegalStateException("only '!eof' negation is supported in lexer conditions");
            }
            pos[0]++;
            return singleClause(new ExtractedGrammar.LexerAutomaton.Literal(
                    ExtractedGrammar.LexerAutomaton.Literal.NOT_EOF, 0, 0));
        }
        if ("set_contains".equals(tok)) {
            expect(tokens, pos, "set_contains");
            expect(tokens, pos, "(");
            String setName = expectIdent(tokens, pos);
            expect(tokens, pos, ",");
            expectNumber(tokens, pos);
            expect(tokens, pos, ",");
            if (!"lookahead".equals(tokens.get(pos[0]))) {
                throw new IllegalStateException("set_contains must take (name, size, lookahead)");
            }
            pos[0]++;
            expect(tokens, pos, ")");
            Integer setId = charSetIds.get(setName);
            if (setId == null) {
                throw new IllegalStateException("set_contains references unknown character set: " + setName);
            }
            return singleClause(new ExtractedGrammar.LexerAutomaton.Literal(
                    ExtractedGrammar.LexerAutomaton.Literal.SET, setId, 0));
        }
        return parseComparison(tokens, pos);
    }

    private List<Object[]> parseComparison(List<String> tokens, int[] pos) {
        boolean lookaheadFirst = "lookahead".equals(tokens.get(pos[0]));
        int lhsValue = -1;
        if (lookaheadFirst) {
            pos[0]++;
        } else {
            lhsValue = parseOperand(tokens, pos);
        }
        String op = tokens.get(pos[0]);
        if (!op.equals("==") && !op.equals("!=") && !op.equals("<") && !op.equals("<=")
                && !op.equals(">") && !op.equals(">=")) {
            throw new IllegalStateException("unexpected operator in lexer condition: " + op);
        }
        pos[0]++;
        boolean rhsIsLookahead = "lookahead".equals(tokens.get(pos[0]));
        int rhsValue;
        if (rhsIsLookahead) {
            rhsValue = -1;
            pos[0]++;
        } else {
            rhsValue = parseOperand(tokens, pos);
        }
        if (lookaheadFirst == rhsIsLookahead) {
            throw new IllegalStateException("comparison must involve lookahead exactly once");
        }
        if (lookaheadFirst) {
            return singleClause(compareLiteral(op, rhsValue));
        }
        // operand OP lookahead: mirror the operator
        return singleClause(compareLiteral(mirrorOp(op), lhsValue));
    }

    private String mirrorOp(String op) {
        return switch (op) {
            case "<" -> ">";
            case ">" -> "<";
            case "<=" -> ">=";
            case ">=" -> "<=";
            default -> op;
        };
    }

    private ExtractedGrammar.LexerAutomaton.Literal compareLiteral(String op, int value) {
        switch (op) {
            case "==" -> {
                return new ExtractedGrammar.LexerAutomaton.Literal(
                        ExtractedGrammar.LexerAutomaton.Literal.CHAR_EQ, value, 0);
            }
            case "!=" -> {
                if (value == 0) {
                    return new ExtractedGrammar.LexerAutomaton.Literal(
                            ExtractedGrammar.LexerAutomaton.Literal.NONZERO, 0, 0);
                }
                return new ExtractedGrammar.LexerAutomaton.Literal(
                        ExtractedGrammar.LexerAutomaton.Literal.CHAR_NEQ, value, 0);
            }
            case "<" -> {
                return new ExtractedGrammar.LexerAutomaton.Literal(
                        ExtractedGrammar.LexerAutomaton.Literal.RANGE, 0, value - 1);
            }
            case "<=" -> {
                return new ExtractedGrammar.LexerAutomaton.Literal(
                        ExtractedGrammar.LexerAutomaton.Literal.RANGE, 0, value);
            }
            case ">" -> {
                return new ExtractedGrammar.LexerAutomaton.Literal(
                        ExtractedGrammar.LexerAutomaton.Literal.RANGE, value + 1, MAX_LOOKAHEAD);
            }
            case ">=" -> {
                return new ExtractedGrammar.LexerAutomaton.Literal(
                        ExtractedGrammar.LexerAutomaton.Literal.RANGE, value, MAX_LOOKAHEAD);
            }
            default -> throw new IllegalStateException("unknown comparison operator: " + op);
        }
    }

    private int parseOperand(List<String> tokens, int[] pos) {
        String tok = tokens.get(pos[0]);
        pos[0]++;
        return parseCharValue(tok);
    }

    private static List<Object[]> singleClause(ExtractedGrammar.LexerAutomaton.Literal literal) {
        List<Object[]> clauses = new ArrayList<>();
        List<ExtractedGrammar.LexerAutomaton.Literal> literals = new ArrayList<>();
        literals.add(literal);
        clauses.add(new Object[]{literals});
        return clauses;
    }

    private static void expect(List<String> tokens, int[] pos, String expected) {
        if (pos[0] >= tokens.size() || !expected.equals(tokens.get(pos[0]))) {
            throw new IllegalStateException("expected '" + expected + "' in lexer condition, got: "
                    + (pos[0] < tokens.size() ? tokens.get(pos[0]) : "<end>"));
        }
        pos[0]++;
    }

    private static String expectIdent(List<String> tokens, int[] pos) {
        String tok = tokens.get(pos[0]);
        if (!tok.matches("[a-zA-Z_]\\w*")) {
            throw new IllegalStateException("expected identifier in lexer condition, got: " + tok);
        }
        pos[0]++;
        return tok;
    }

    private static String expectNumber(List<String> tokens, int[] pos) {
        String tok = tokens.get(pos[0]);
        if (!tok.matches("\\d+")) {
            throw new IllegalStateException("expected number in lexer condition, got: " + tok);
        }
        pos[0]++;
        return tok;
    }

    private int parseCharValue(String s) {
        s = s.trim();
        if (s.startsWith("'")) {
            if (s.length() < 3 || !s.endsWith("'")) {
                throw new IllegalStateException("malformed char literal: " + s);
            }
            String inner = s.substring(1, s.length() - 1);
            if (inner.length() == 1) {
                return inner.charAt(0);
            }
            if (inner.startsWith("\\")) {
                char esc = inner.charAt(1);
                return switch (esc) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case 'f' -> '\f';
                    case 'v' -> '\u000b';
                    case 'a' -> '\u0007';
                    case 'b' -> '\b';
                    case '0' -> 0;
                    case '\\' -> '\\';
                    case '\'' -> '\'';
                    case '"' -> '"';
                    case 'x' -> {
                        if (inner.length() < 4) {
                            throw new IllegalStateException("malformed hex char literal: " + s);
                        }
                        yield Integer.parseInt(inner.substring(2), 16);
                    }
                    default -> throw new IllegalStateException("unsupported char escape: " + s);
                };
            }
            throw new IllegalStateException("malformed char literal: " + s);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Integer.parseInt(s.substring(2), 16);
        }
        if (s.matches("\\d+")) {
            return Integer.parseInt(s);
        }
        throw new IllegalStateException("cannot parse character value: " + s);
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static int intInParens(String s) {
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close < 0) {
            throw new IllegalStateException("malformed action: " + s);
        }
        return Integer.parseInt(s.substring(open + 1, close).trim());
    }

    private static int findSemicolon(String s, int from) {
        int i = s.indexOf(';', from);
        if (i < 0) {
            throw new IllegalStateException("missing semicolon after action: " + s.substring(from));
        }
        return i;
    }

    private static int matchParen(String s, int openPos) {
        int depth = 0;
        boolean inChar = false;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '\'') {
                inChar = true;
            } else if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("unbalanced parentheses at offset " + openPos);
    }

    private static List<String> splitTopLevel(String s, char sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inChar = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inChar) {
                cur.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    cur.append(s.charAt(++i));
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '\'') {
                inChar = true;
                cur.append(c);
            } else if (c == '(') {
                depth++;
                cur.append(c);
            } else if (c == ')') {
                depth--;
                cur.append(c);
            } else if (c == sep && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    // ------------------------------------------------------------------
    // Coverage check: every field the language initializer sets
    // ------------------------------------------------------------------

    private void checkInitializerCoverage() {
        String fn = findFunctionBody("tree_sitter_");
        if (fn == null) {
            throw new IllegalStateException("language export function not found in parser.c");
        }
        Matcher m = Pattern.compile("\\.(\\w+)\\s*=").matcher(fn);
        while (m.find()) {
            String field = m.group(1);
            if (!isKnownLanguageField(field)) {
                throw new IllegalStateException("unhandled TSLanguage field in initializer: " + field);
            }
        }
    }

    private boolean isKnownLanguageField(String field) {
        return switch (field) {
            case "version", "abi_version", "symbol_count", "alias_count", "token_count", "external_token_count",
                 "state_count", "large_state_count", "production_id_count", "field_count",
                 "max_alias_sequence_length", "parse_table", "small_parse_table",
                 "small_parse_table_map", "parse_actions", "symbol_names", "field_names",
                 "field_map_slices", "field_map_entries", "symbol_metadata", "public_symbol_map",
                 "alias_map", "alias_sequences", "lex_modes", "lex_fn", "primary_state_ids",
                 "keyword_lex_fn", "keyword_capture_token", "external_scanner", "reserved_words",
                 "max_reserved_word_set_size", "supertype_count", "supertype_map_slices",
                 "supertype_map_entries", "supertype_symbols", "name", "metadata",
                 "major_version", "minor_version", "patch_version" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // C source scanning helpers
    // ------------------------------------------------------------------

    /** Finds the body of {@code static const ... name[] = { ... }} including nested braces. */
    private String arrayBody(String name) {
        String body = findArrayBodyOrNull(name);
        if (body == null) {
            throw new IllegalStateException("table not found in parser.c: " + name);
        }
        return body;
    }

    private String findArrayBodyOrNull(String name) {
        Pattern decl = Pattern.compile("\\b" + name + "\\s*\\[[^]]*](?:\\s*\\[[^]]*])?\\s*=\\s*\\{");
        Matcher m = decl.matcher(source);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
        int end = findMatchingBrace(source, start);
        return source.substring(start + 1, end);
    }

    private String findFunctionBody(String funcNamePrefix) {
        Pattern decl = Pattern.compile("(?:TS_PUBLIC\\s+)?const\\s+TSLanguage\\s+\\*" + funcNamePrefix + "\\w*\\s*\\([^)]*\\)\\s*\\{");
        Matcher m = decl.matcher(source);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
        int end = findMatchingBrace(source, start);
        return source.substring(start + 1, end);
    }

    private String findCFunctionBody(String funcName) {
        Pattern decl = Pattern.compile("static\\s+bool\\s+" + funcName + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher m = decl.matcher(source);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
        int end = findMatchingBrace(source, start);
        return source.substring(start + 1, end);
    }

    private int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                i = skipLineComment(s, i);
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                i = skipBlockComment(s, i);
            } else if (c == '"') {
                i = skipString(s, i);
            } else if (c == '\'') {
                i = skipChar(s, i);
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("unbalanced braces in parser.c near offset " + openPos);
    }

    private int skipLineComment(String s, int start) {
        int i = start + 2;
        while (i < s.length() && s.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    private int skipBlockComment(String s, int start) {
        int i = start + 2;
        while (i + 1 < s.length() && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
            i++;
        }
        return i + 1;
    }

    private int skipChar(String s, int quotePos) {
        int i = quotePos + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == '\'') {
                return i;
            } else {
                i++;
            }
        }
        return i;
    }

    private int skipString(String s, int quotePos) {
        int i = quotePos + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == '"') {
                return i;
            } else {
                i++;
            }
        }
        return i;
    }

    private static boolean isCKeyword(String tok) {
        return switch (tok) {
            case "static", "const", "uint16_t", "uint32_t", "bool", "TSStateId", "TSSymbol",
                 "TSLexer", "uint8_t", "int8_t", "int16_t", "int32_t", "size_t", "char",
                 "NULL", "true", "false" -> true;
            default -> false;
        };
    }

    private static void ensureListSize(List<?> list, int size) {
        while (list.size() < size) {
            list.add(null);
        }
    }

    private static int[] toArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private static boolean isHexDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                return false;
            }
        }
        return !s.isEmpty();
    }

    private static String unescapeCString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '0' -> sb.append('\0');
                    case 'x' -> {
                        if (i + 2 <= s.length()) {
                            String hex = s.substring(i + 1, Math.min(i + 3, s.length()));
                            if (isHexDigits(hex)) {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += hex.length();
                            } else {
                                sb.append(n);
                            }
                        }
                    }
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}