package io.nop.treesitter.codegen;

import io.nop.treesitter.corpus.CorpusUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap item 10 Phase 1: the vendored tree-sitter-typescript sources extract
 * with loader-level invariants intact and the shared corpus reader accounts
 * for every vendored section. No blob is built here — blob writing happens in
 * Phase 2 once the scanner DSL exists (a blob with external tokens and no
 * scanner program is never produced).
 */
class TsGrammarVendorTest {

    private static final Path VENDOR_ROOT =
            Path.of("src/test/resources/upstream/grammars/tree-sitter-typescript");
    private static final Path TS_PARSER_C = VENDOR_ROOT.resolve("typescript/src/parser.c");
    private static final Path TSX_PARSER_C = VENDOR_ROOT.resolve("tsx/src/parser.c");
    private static final Path CORPUS_DIR = VENDOR_ROOT.resolve("test/corpus");

    private static ExtractedGrammar extract(Path parserC) throws IOException {
        return ParserCExtractor.extract(Files.readString(parserC, StandardCharsets.UTF_8));
    }

    @Test
    void typescriptParserCExtractsWithLoaderInvariants() throws IOException {
        ExtractedGrammar g = extract(TS_PARSER_C);
        assertEquals(383, g.symbolNames.length);
        assertEquals(5870, g.stateCount);
        assertEquals(1193, g.largeStateCount);
        assertEquals(166, g.tokenCount);
        assertEquals(10, g.externalScannerSymbolMap.length);
        assertEquals(11, g.externalScannerStates.length);
        assertEquals(1, g.keywordCaptureToken, "keyword capture is sym_identifier");
        assertTrue(g.stateCount <= 0xFFFF, "states must fit the blob's u16 encoding");
    }

    @Test
    void tsxParserCExtractsWithLoaderInvariants() throws IOException {
        ExtractedGrammar g = extract(TSX_PARSER_C);
        assertEquals(400, g.symbolNames.length);
        assertEquals(5986, g.stateCount);
        assertEquals(1167, g.largeStateCount);
        assertEquals(172, g.tokenCount);
        assertEquals(10, g.externalScannerSymbolMap.length);
        assertTrue(g.stateCount <= 0xFFFF, "states must fit the blob's u16 encoding");
    }

    @Test
    void scannerHeadersAreVendoredForBothDialects() {
        assertTrue(Files.exists(VENDOR_ROOT.resolve("common/scanner.h")));
        assertTrue(Files.exists(VENDOR_ROOT.resolve("typescript/src/scanner.c")));
        assertTrue(Files.exists(VENDOR_ROOT.resolve("tsx/src/scanner.c")));
    }

    @Test
    void sharedCorpusHas112SectionsWithTwoDialectTags() throws IOException {
        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR,
                List.of("declarations.txt", "expressions.txt", "functions.txt", "types.txt"));
        assertEquals(112, sections.size(), "vendored TS corpus section count drifted");

        List<CorpusUtil.Section> typescript = sections.stream()
                .filter(s -> s.language() == null || "typescript".equals(s.language())).toList();
        List<CorpusUtil.Section> tsx = sections.stream()
                .filter(s -> s.language() == null || "tsx".equals(s.language())).toList();
        assertEquals(111, typescript.size(), "typescript-runnable sections");
        assertEquals(111, tsx.size(), "tsx-runnable sections");

        List<String> tagged = sections.stream()
                .filter(s -> s.language() != null)
                .map(s -> s.file() + ": '" + s.title() + "' -> " + s.language())
                .toList();
        assertEquals(2, tagged.size());
        assertEquals("expressions.txt: 'Type assertions' -> typescript", tagged.get(0));
        assertEquals("expressions.txt: 'Type arguments in JSX' -> tsx", tagged.get(1));
    }

    @Test
    void jsCorpusCounts116SectionsThroughTheSameReader() throws IOException {
        List<CorpusUtil.Section> sections = CorpusUtil.read(
                Path.of("src/test/resources/upstream/grammars/tree-sitter-javascript/test/corpus"),
                List.of("destructuring.txt", "expressions.txt", "injectables.txt",
                        "literals.txt", "semicolon_insertion.txt", "statements.txt"));
        assertEquals(116, sections.size(), "vendored JS corpus section count drifted");
    }
}
