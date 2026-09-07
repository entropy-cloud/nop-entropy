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
 * <p>Only the static table subset that the C runtime's {@code TSLanguage}
 * structure exposes is understood. Any table encoding or C construct outside
 * that subset raises {@link IllegalStateException} carrying the offending
 * symbol / state / table context — nothing is silently skipped.</p>
 */
public final class ParserCExtractor {

    private final String source;
    private final ExtractedGrammar g = new ExtractedGrammar();
    private Map<String, Integer> enumValues;

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
        extractLexFnAccepts();
        extractKeywordLexFnAccepts();
        checkInitializerCoverage();
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
        } catch (NumberFormatException ignored) {
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
        String[] names = new String[g.symbolCount];
        Matcher m = Pattern.compile("\\[(\\w+)]\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
        while (m.find()) {
            int idx = resolveSymbol(m.group(1));
            checkSymbolIndex(idx, "ts_symbol_names");
            names[idx] = unescapeCString(m.group(2));
        }
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                throw new IllegalStateException("ts_symbol_names missing entry for symbol " + i);
            }
        }
        g.symbolNames = names;
    }

    private void extractSymbolMap() {
        String body = findArrayBodyOrNull("ts_symbol_map");
        if (body == null) {
            return;
        }
        int[] map = new int[g.symbolCount];
        Matcher m = Pattern.compile("\\[(\\w+)]\\s*=\\s*(\\w+)").matcher(body);
        while (m.find()) {
            int idx = resolveSymbol(m.group(1));
            checkSymbolIndex(idx, "ts_symbol_map");
            map[idx] = resolveSymbol(m.group(2));
        }
        g.symbolMap = map;
    }

    private void extractSymbolMetadata() {
        String body = arrayBody("ts_symbol_metadata");
        ExtractedGrammar.SymbolMeta[] meta = new ExtractedGrammar.SymbolMeta[g.symbolCount];
        Pattern entryStart = Pattern.compile("\\[(\\w+)]\\s*=\\s*\\{");
        Matcher locs = entryStart.matcher(body);
        while (locs.find()) {
            int braceStart = locs.end() - 1;
            int end = findMatchingBrace(body, braceStart);
            String fields = body.substring(braceStart + 1, end);
            int idx = resolveSymbol(locs.group(1));
            checkSymbolIndex(idx, "ts_symbol_metadata");
            boolean visible = fields.contains(".visible = true");
            boolean named = fields.contains(".named = true");
            boolean supertype = fields.contains(".supertype = true");
            meta[idx] = new ExtractedGrammar.SymbolMeta(visible, named, supertype);
        }
        for (int i = 0; i < meta.length; i++) {
            if (meta[i] == null) {
                throw new IllegalStateException("ts_symbol_metadata missing entry for symbol " + i);
            }
        }
        g.symbolMetadata = meta;
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
        int[] map = new int[vals.size()];
        for (int i = 0; i < map.length; i++) {
            map[i] = vals.get(i);
        }
        g.nonTerminalAliasMap = map;
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
        Pattern stateStart = Pattern.compile("\\[(\\w+)]\\s*=\\s*\\{");
        Matcher locs = stateStart.matcher(body);
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
            Matcher inner = Pattern.compile("\\[(\\w+)]\\s*=\\s*(?:ACTIONS|STATE)\\((\\d+)\\)").matcher(stateBody);
            while (inner.find()) {
                int symIdx = resolveSymbol(inner.group(1));
                if (symIdx < g.symbolCount) {
                    table[stateIdx][symIdx] = Integer.parseInt(inner.group(2));
                }
            }
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
        int[] arr = new int[words.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = words.get(i);
        }
        g.smallParseTable = arr;

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
    // Lex function accept decoding
    // ------------------------------------------------------------------

    private void extractLexFnAccepts() {
        g.lexStateAcceptSymbol = extractLexFunctionAccepts("ts_lex");
    }

    private void extractKeywordLexFnAccepts() {
        Map<Integer, Integer> accepts = extractLexFunctionAccepts("ts_lex_keywords");
        g.keywordLexStateAcceptSymbol = accepts;
        int n = accepts == null ? 0 : accepts.size();
        if (n > 0) {
            // A keyword lexer is normally driven as a single start state (0);
            // record a per-state keyword lex mode list derived from the function.
            ExtractedGrammar.LexMode[] modes = new ExtractedGrammar.LexMode[n];
            int i = 0;
            for (Integer state : accepts.keySet()) {
                modes[i++] = new ExtractedGrammar.LexMode(state, 0, 0);
            }
            g.keywordLexModes = modes;
        } else {
            g.keywordLexModes = new ExtractedGrammar.LexMode[0];
        }
    }

    private Map<Integer, Integer> extractLexFunctionAccepts(String funcName) {
        String fn = findCFunctionBody(funcName);
        if (fn == null) {
            return null;
        }
        Map<Integer, Integer> accepts = new LinkedHashMap<>();
        Matcher caseM = Pattern.compile("\\bcase\\s+(\\d+):").matcher(fn);
        while (caseM.find()) {
            int caseStart = caseM.start();
            int state = Integer.parseInt(caseM.group(1));
            int nextCase = nextTokenPos(fn, caseM.end(), "case", "default:");
            int end = nextCase < 0 ? fn.length() : nextCase;
            String block = fn.substring(caseStart, end);
            Matcher acc = Pattern.compile("\\bACCEPT_TOKEN\\((\\w+)\\)").matcher(block);
            if (acc.find()) {
                accepts.put(state, resolveSymbol(acc.group(1)));
            }
        }
        return accepts;
    }

    private int nextTokenPos(String s, int from, String token, String altToken) {
        int p = s.indexOf(token, from);
        int pa = s.indexOf(altToken, from);
        if (p < 0) {
            return pa;
        }
        if (pa < 0) {
            return p;
        }
        return Math.min(p, pa);
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
            case "version", "symbol_count", "alias_count", "token_count", "external_token_count",
                 "state_count", "large_state_count", "production_id_count", "field_count",
                 "max_alias_sequence_length", "parse_table", "small_parse_table",
                 "small_parse_table_map", "parse_actions", "symbol_names", "field_names",
                 "field_map_slices", "field_map_entries", "symbol_metadata", "public_symbol_map",
                 "alias_map", "alias_sequences", "lex_modes", "lex_fn", "primary_state_ids",
                 "keyword_lex_fn", "keyword_capture_token", "external_scanner", "reserved_words",
                 "max_reserved_word_set_size", "name", "metadata" -> true;
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

    private void checkSymbolIndex(int idx, String table) {
        if (idx < 0 || idx >= g.symbolCount) {
            throw new IllegalStateException(table + " symbol index out of range: " + idx
                    + " (symbol_count=" + g.symbolCount + ")");
        }
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
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += hex.length();
                            } catch (NumberFormatException ignored) {
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
