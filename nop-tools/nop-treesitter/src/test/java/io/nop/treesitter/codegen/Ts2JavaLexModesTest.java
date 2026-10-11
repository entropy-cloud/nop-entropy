package io.nop.treesitter.codegen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2: lex modes, keyword lex modes, lex action decoding and the
 * language-initializer coverage check, all verified against the vendored
 * upstream tree-sitter-json {@code parser.c}.
 */
class Ts2JavaLexModesTest {

    private static final Path PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-json/src/parser.c");

    private static ExtractedGrammar extractVendored() throws IOException {
        String source = Files.readString(PARSER_C, StandardCharsets.UTF_8);
        return ParserCExtractor.extract(source);
    }

    @Test
    void lexModeCountEqualsParseStateCount() throws IOException {
        ExtractedGrammar g = extractVendored();
        assertEquals(g.stateCount, g.lexModes.length);
    }

    @Test
    void lexModeValuesMatchParserC() throws IOException {
        ExtractedGrammar g = extractVendored();
        assertEquals(0, g.lexModes[0].lexState());
        assertEquals(0, g.lexModes[16].lexState());
        assertEquals(1, g.lexModes[17].lexState());
        assertEquals(1, g.lexModes[19].lexState());
        assertEquals(0, g.lexModes[31].lexState());
    }

    @Test
    void jsonGrammarHasNoKeywordLexer() throws IOException {
        ExtractedGrammar g = extractVendored();
        // The vendored JSON grammar has no ts_lex_keywords function and no
        // keyword lex modes table: the extractor must report the absence
        // explicitly rather than fabricating or silently skipping it.
        assertEquals(0, g.keywordLexModes.length);
        assertNull(g.keywordLexStateAcceptSymbol);
    }

    @Test
    void keywordLexModesExtractedWhenPresent() {
        String source = """
                #define LANGUAGE_VERSION 14
                #define STATE_COUNT 1
                #define LARGE_STATE_COUNT 1
                #define SYMBOL_COUNT 4
                #define ALIAS_COUNT 0
                #define TOKEN_COUNT 4
                #define EXTERNAL_TOKEN_COUNT 0
                #define FIELD_COUNT 0
                #define MAX_ALIAS_SEQUENCE_LENGTH 1
                #define PRODUCTION_ID_COUNT 0

                enum ts_symbol_identifiers {
                  sym_if = 1,
                  sym_ident = 2,
                  anon_sym_eq = 3,
                };

                static const char * const ts_symbol_names[] = {
                  [ts_builtin_sym_end] = "end",
                  [sym_if] = "if",
                  [sym_ident] = "ident",
                  [anon_sym_eq] = "=",
                };

                static const TSSymbolMetadata ts_symbol_metadata[] = {
                  [ts_builtin_sym_end] = {.visible = false, .named = true},
                  [sym_if] = {.visible = true, .named = true},
                  [sym_ident] = {.visible = true, .named = true},
                  [anon_sym_eq] = {.visible = true, .named = false},
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
                    [sym_if] = ACTIONS(1),
                    [sym_ident] = ACTIONS(1),
                    [anon_sym_eq] = ACTIONS(1),
                  },
                };

                static const TSParseActionEntry ts_parse_actions[] = {
                  [0] = {.entry = {.count = 0, .reusable = false}},
                  [1] = {.entry = {.count = 1, .reusable = true}}, REDUCE(sym_ident, 1, 0, 0),
                };

                static bool ts_lex_keywords(TSLexer *lexer, TSStateId state) {
                  START_LEXER();
                  switch (state) {
                    case 0:
                      if (lookahead == 'i') ADVANCE(1);
                      END_STATE();
                    case 1:
                      ACCEPT_TOKEN(sym_if);
                      END_STATE();
                    default:
                      return false;
                  }
                }

                TS_PUBLIC const TSLanguage *tree_sitter_keywords(void) {
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
                    .primary_state_ids = ts_primary_state_ids,
                    .keyword_lex_fn = ts_lex_keywords,
                  };
                  return &language;
                }
                """;
        ExtractedGrammar g = ParserCExtractor.extract(source);
        assertEquals(1, g.keywordLexModes.length);
        assertEquals(1, g.keywordLexStateAcceptSymbol.size());
        assertEquals(1, g.keywordLexStateAcceptSymbol.get(1));
    }

    @Test
    void lexActionDecodeMatchesParserC() throws IOException {
        ExtractedGrammar g = extractVendored();
        // ts_lex: case 21 accepts ts_builtin_sym_end(0), case 22 accepts
        // anon_sym_LBRACE(1), case 43 accepts sym_comment(14).
        assertEquals(0, g.lexStateAcceptSymbol.get(21));
        assertEquals(1, g.lexStateAcceptSymbol.get(22));
        assertEquals(14, g.lexStateAcceptSymbol.get(43));
        assertEquals(23, g.lexStateAcceptSymbol.size());
        assertNull(g.lexStateAcceptSymbol.get(0), "state 0 has no ACCEPT_TOKEN");
    }

    @Test
    void everyInitializedLanguageFieldIsCovered() throws IOException {
        // Extraction itself runs the coverage check; reaching this point with
        // the vendored parser.c proves every field its TSLanguage initializer
        // sets is either extracted or diagnosed.
        extractVendored();
    }

    @Test
    void unknownLanguageFieldRaisesTypedException() {
        String source = """
                #define LANGUAGE_VERSION 14
                #define STATE_COUNT 1
                #define LARGE_STATE_COUNT 1
                #define SYMBOL_COUNT 1
                #define ALIAS_COUNT 0
                #define TOKEN_COUNT 1
                #define EXTERNAL_TOKEN_COUNT 0
                #define FIELD_COUNT 0
                #define MAX_ALIAS_SEQUENCE_LENGTH 1
                #define PRODUCTION_ID_COUNT 0

                static const char * const ts_symbol_names[] = {
                  [ts_builtin_sym_end] = "end",
                };

                static const TSSymbolMetadata ts_symbol_metadata[] = {
                  [ts_builtin_sym_end] = {.visible = false, .named = true},
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
                  },
                };

                static const TSParseActionEntry ts_parse_actions[] = {
                  [0] = {.entry = {.count = 0, .reusable = false}},
                  [1] = {.entry = {.count = 1, .reusable = true}}, RECOVER(),
                };

                TS_PUBLIC const TSLanguage *tree_sitter_unknown(void) {
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
                    .primary_state_ids = ts_primary_state_ids,
                    .futuristic_field = ts_symbol_names,
                  };
                  return &language;
                }
                """;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ParserCExtractor.extract(source));
        assertTrue(ex.getMessage().contains("futuristic_field"), ex.getMessage());
    }
}