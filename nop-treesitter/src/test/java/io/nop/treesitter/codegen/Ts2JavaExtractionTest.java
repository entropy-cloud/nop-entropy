package io.nop.treesitter.codegen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1: structural extraction of {@code parser.c} static tables, verified
 * against the vendored upstream tree-sitter-json {@code parser.c}.
 */
class Ts2JavaExtractionTest {

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-json/src/parser.c");

    private static ExtractedGrammar extractVendored() throws IOException {
        String source = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        return ParserCExtractor.extract(source);
    }

    @Test
    void vendoredParserExists() {
        assertTrue(Files.exists(PARSER_C), "vendored parser.c missing");
    }

    @Test
    void extractsConstants() throws IOException {
        ExtractedGrammar g = extractVendored();
        assertEquals(14, g.languageVersion);
        assertEquals(32, g.stateCount);
        assertEquals(7, g.largeStateCount);
        assertEquals(25, g.symbolCount);
        assertEquals(15, g.tokenCount);
        assertEquals(0, g.externalTokenCount);
        assertEquals(2, g.fieldCount);
        assertEquals(2, g.productionIdCount);
        assertEquals(4, g.maxAliasSequenceLength);
    }

    @Test
    void symbolCountAndNamesMatchParserC() throws IOException {
        ExtractedGrammar g = extractVendored();
        assertEquals(25, g.symbolNames.length);
        assertEquals("end", g.symbolNames[0]);
        assertEquals("{", g.symbolNames[1]);
        assertEquals("\"", g.symbolNames[7]);
        assertEquals("string_content", g.symbolNames[8]);
        assertEquals("document", g.symbolNames[15]);
        assertEquals("_value", g.symbolNames[16]);
        assertEquals("array_repeat1", g.symbolNames[24]);
        for (String name : g.symbolNames) {
            assertTrue(name != null && !name.isEmpty(), "symbol name must not be empty");
        }
    }

    @Test
    void symbolMetadataMatchesParserC() throws IOException {
        ExtractedGrammar g = extractVendored();
        ExtractedGrammar.SymbolMeta end = g.symbolMetadata[0];
        assertEquals(false, end.visible());
        assertEquals(true, end.named());

        ExtractedGrammar.SymbolMeta brace = g.symbolMetadata[1];
        assertEquals(true, brace.visible());
        assertEquals(false, brace.named());

        ExtractedGrammar.SymbolMeta value = g.symbolMetadata[16];
        assertEquals(false, value.visible());
        assertEquals(true, value.named());
        assertEquals(true, value.supertype());

        ExtractedGrammar.SymbolMeta repeat = g.symbolMetadata[22];
        assertEquals(false, repeat.visible());
        assertEquals(false, repeat.named());
    }

    @Test
    void primaryStateIdsLengthEqualsStateCount() throws IOException {
        ExtractedGrammar g = extractVendored();
        assertEquals(32, g.primaryStateIds.length);
        for (int i = 0; i < g.primaryStateIds.length; i++) {
            assertEquals(i, g.primaryStateIds[i]);
        }
    }

    @Test
    void parseTableDimensionsMatchConstants() throws IOException {
        ExtractedGrammar g = extractVendored();
        assertEquals(7, g.parseTable.length, "large state count");
        for (int[] row : g.parseTable) {
            assertEquals(g.symbolCount, row.length);
        }
        assertEquals(1, g.parseTable[0][0], "state 0 / end-of-input maps to ACTIONS(1)=RECOVER");
        assertEquals(3, g.parseTable[0][14], "state 0 / sym_comment maps to ACTIONS(3)=SHIFT_EXTRA");
        assertEquals(30, g.parseTable[1][15], "state 1 / sym_document is a goto to state 30");
    }

    @Test
    void smallParseTableAndMapAreExtracted() throws IOException {
        ExtractedGrammar g = extractVendored();
        assertEquals(360, g.smallParseTable.length);
        assertEquals(25, g.smallParseTableMap.length);
        assertEquals(0, g.smallParseTableMap[0]);
        assertEquals(353, g.smallParseTableMap[24]);
    }

    @Test
    void parseActionsDecodeRoundTrip() throws IOException {
        ExtractedGrammar g = extractVendored();
        // group at index 0 is the empty placeholder
        ExtractedGrammar.ParseActionGroup zero = g.parseActions[0];
        assertEquals(0, zero.index());
        assertEquals(0, zero.count());
        assertEquals(0, zero.actions().length);

        // group 1: RECOVER
        ExtractedGrammar.ParseActionGroup one = groupByIndex(g, 1);
        assertEquals(1, one.count());
        assertEquals(ExtractedGrammar.ParseAction.RECOVER, one.actions()[0].type());

        // group 5: REDUCE(sym_document, 0, 0, 0)
        ExtractedGrammar.ParseActionGroup five = groupByIndex(g, 5);
        assertEquals(ExtractedGrammar.ParseAction.REDUCE, five.actions()[0].type());
        assertEquals(15, five.actions()[0].symbol());
        assertEquals(0, five.actions()[0].childCount());

        // group 7: SHIFT(16)
        ExtractedGrammar.ParseActionGroup seven = groupByIndex(g, 7);
        assertEquals(ExtractedGrammar.ParseAction.SHIFT, seven.actions()[0].type());
        assertEquals(16, seven.actions()[0].state());

        // group 3: SHIFT_EXTRA
        ExtractedGrammar.ParseActionGroup three = groupByIndex(g, 3);
        assertTrue(three.actions()[0].extra());

        // group 19: REDUCE + SHIFT_REPEAT
        ExtractedGrammar.ParseActionGroup nineteen = groupByIndex(g, 19);
        assertEquals(2, nineteen.actions().length);
        assertEquals(ExtractedGrammar.ParseAction.REDUCE, nineteen.actions()[0].type());
        assertEquals(ExtractedGrammar.ParseAction.SHIFT, nineteen.actions()[1].type());
        assertTrue(nineteen.actions()[1].repetition());
        assertEquals(16, nineteen.actions()[1].state());

        // group 92: ACCEPT_INPUT
        ExtractedGrammar.ParseActionGroup ninetyTwo = groupByIndex(g, 92);
        assertEquals(ExtractedGrammar.ParseAction.ACCEPT, ninetyTwo.actions()[0].type());

        // group 90: REDUCE(sym_pair, 3, 0, 1) -> production id 1
        ExtractedGrammar.ParseActionGroup ninety = groupByIndex(g, 90);
        assertEquals(18, ninety.actions()[0].symbol());
        assertEquals(3, ninety.actions()[0].childCount());
        assertEquals(1, ninety.actions()[0].productionId());
    }

    @Test
    void mainRunsWithParserCAndOutArgs() throws IOException {
        Path out = Files.createTempFile("ts2java-test", ".bin");
        Ts2Java.run(PARSER_C, out);
        byte[] blob = Files.readAllBytes(out);
        assertTrue(blob.length > 64, "blob must contain header");
        Files.deleteIfExists(out);
    }

    @Test
    void malformedTableRaisesTypedException() {
        String malformed = """
                #define LANGUAGE_VERSION 14
                #define STATE_COUNT 1
                #define LARGE_STATE_COUNT 1
                #define SYMBOL_COUNT 2
                #define ALIAS_COUNT 0
                #define TOKEN_COUNT 2
                #define EXTERNAL_TOKEN_COUNT 0
                #define FIELD_COUNT 0
                #define MAX_ALIAS_SEQUENCE_LENGTH 1
                #define PRODUCTION_ID_COUNT 0

                enum ts_symbol_identifiers {
                  anon_sym_A = 1,
                };

                static const char * const ts_symbol_names[] = {
                  [ts_builtin_sym_end] = "end",
                  [anon_sym_A] = "a",
                };

                static const TSSymbolMetadata ts_symbol_metadata[] = {
                  [ts_builtin_sym_end] = {.visible = false, .named = true},
                  [anon_sym_A] = {.visible = true, .named = false},
                };

                static const TSStateId ts_primary_state_ids[STATE_COUNT] = {
                  [0] = 0,
                };

                static const TSLexMode ts_lex_modes[STATE_COUNT] = {
                  [0] = {.lex_state = 0},
                };

                static const uint16_t ts_parse_table[LARGE_STATE_COUNT][SYMBOL_COUNT] = {
                  [0] = {
                    [ts_builtin_sym_end] = ACTIONS(1),
                    [anon_sym_A] = ACTIONS(1),
                  },
                };

                static const TSParseActionEntry ts_parse_actions[] = {
                  [0] = {.entry = {.count = 0, .reusable = false}},
                  [1] = {.entry = {.count = 1, .reusable = true}}, REDUCE(no_such_symbol, 0, 0, 0),
                };
                """;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ParserCExtractor.extract(malformed));
        assertTrue(ex.getMessage().contains("no_such_symbol"), ex.getMessage());
    }

    @Test
    void undecodableLexerStatementRaisesTypedExceptionWithStateContext() {
        String source = """
                #define LANGUAGE_VERSION 14
                #define STATE_COUNT 1
                #define LARGE_STATE_COUNT 1
                #define SYMBOL_COUNT 2
                #define ALIAS_COUNT 0
                #define TOKEN_COUNT 2
                #define EXTERNAL_TOKEN_COUNT 0
                #define FIELD_COUNT 0
                #define MAX_ALIAS_SEQUENCE_LENGTH 1
                #define PRODUCTION_ID_COUNT 0

                enum ts_symbol_identifiers {
                  anon_sym_A = 1,
                };

                static const char * const ts_symbol_names[] = {
                  [ts_builtin_sym_end] = "end",
                  [anon_sym_A] = "a",
                };

                static const TSSymbolMetadata ts_symbol_metadata[] = {
                  [ts_builtin_sym_end] = {.visible = false, .named = true},
                  [anon_sym_A] = {.visible = true, .named = false},
                };

                static const TSStateId ts_primary_state_ids[STATE_COUNT] = {
                  [0] = 0,
                };

                static const TSLexMode ts_lex_modes[STATE_COUNT] = {
                  [0] = {.lex_state = 0},
                };

                static const uint16_t ts_parse_table[LARGE_STATE_COUNT][SYMBOL_COUNT] = {
                  [0] = {
                    [ts_builtin_sym_end] = ACTIONS(1),
                    [anon_sym_A] = ACTIONS(1),
                  },
                };

                static const TSParseActionEntry ts_parse_actions[] = {
                  [0] = {.entry = {.count = 0, .reusable = false}},
                  [1] = {.entry = {.count = 1, .reusable = true}}, REDUCE(anon_sym_A, 1, 0, 0),
                };

                static bool ts_lex(TSLexer *lexer, TSStateId state) {
                  START_LEXER();
                  switch (state) {
                    case 0:
                      if (FANCY_MACRO(lookahead)) ADVANCE(1);
                      END_STATE();
                    case 1:
                      ACCEPT_TOKEN(anon_sym_A);
                      END_STATE();
                    default:
                      return false;
                  }
                }

                TS_PUBLIC const TSLanguage *tree_sitter_lexerr(void) {
                  static const TSLanguage language = {
                    .version = LANGUAGE_VERSION,
                    .symbol_count = SYMBOL_COUNT,
                    .token_count = TOKEN_COUNT,
                    .state_count = STATE_COUNT,
                    .large_state_count = LARGE_STATE_COUNT,
                    .parse_table = &ts_parse_table[0][0],
                    .parse_actions = ts_parse_actions,
                    .symbol_names = ts_symbol_names,
                    .symbol_metadata = ts_symbol_metadata,
                    .lex_modes = ts_lex_modes,
                    .lex_fn = ts_lex,
                    .primary_state_ids = ts_primary_state_ids,
                  };
                  return &language;
                }
                """;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ParserCExtractor.extract(source));
        assertTrue(ex.getMessage().contains("ts_lex state 0"), ex.getMessage());
    }

    private static ExtractedGrammar.ParseActionGroup groupByIndex(ExtractedGrammar g, int index) {
        for (ExtractedGrammar.ParseActionGroup group : g.parseActions) {
            if (group.index() == index) {
                return group;
            }
        }
        throw new AssertionError("no parse action group at index " + index);
    }
}