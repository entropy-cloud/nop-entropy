package io.nop.treesitter.codegen;

import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap item 10 Phase 2: the shipped TypeScript/TSX grammar blobs decode
 * with the scanner tables matching the vendored parser.c statics, and each
 * shipped blob is byte-identical to a fresh extraction + DSL compile.
 */
class TsBlobTest {

    private static final Path TS_PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-typescript/typescript/src/parser.c");
    private static final Path TSX_PARSER_C = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-typescript/tsx/src/parser.c");
    private static final Path SCANNER_DSL = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-typescript/common/scanner.dsl");

    @Test
    void typescriptBlobLoaderCounts() {
        Language lang = Language.fromClasspath("/grammars/typescript/tree-sitter-typescript-blob.bin");
        assertEquals(14, lang.abiVersion(), "upstream tree-sitter-typescript is ABI 14");
        assertEquals(383, lang.symbolCount() + lang.aliasCount());
        assertEquals(5870, lang.stateCount());
        assertEquals(166, lang.tokenCount());
        assertEquals(10, lang.externalTokenCount());
        assertEquals(1, lang.keywordCaptureToken(), "keyword capture is sym_identifier");
        assertNotNull(lang.keywordLexerAutomaton());
        assertTrue(lang.scannerProgram().length > 0, "TS blob ships the compiled scanner program");
        io.nop.treesitter.scanner.ScannerProgram.validate(lang.scannerProgram());
    }

    @Test
    void tsxBlobLoaderCounts() {
        Language lang = Language.fromClasspath("/grammars/tsx/tree-sitter-tsx-blob.bin");
        assertEquals(14, lang.abiVersion());
        assertEquals(400, lang.symbolCount() + lang.aliasCount());
        assertEquals(5986, lang.stateCount());
        assertEquals(172, lang.tokenCount());
        assertEquals(10, lang.externalTokenCount());
        assertTrue(lang.scannerProgram().length > 0);
        io.nop.treesitter.scanner.ScannerProgram.validate(lang.scannerProgram());
    }

    @Test
    void shippedTypescriptBlobIsByteIdenticalToFreshBuild() throws IOException {
        ExtractedGrammar g = ParserCExtractor.extract(
                Files.readString(TS_PARSER_C, StandardCharsets.UTF_8));
        g.scannerProgram = io.nop.treesitter.scanner.ScannerCompiler.compile(
                Files.readString(SCANNER_DSL, StandardCharsets.UTF_8), g.externalScannerSymbolMap);
        byte[] fresh = BlobWriter.write(g);
        byte[] shipped = Files.readAllBytes(Path.of(
                "src/main/resources/grammars/typescript/tree-sitter-typescript-blob.bin"));
        assertArrayEquals(fresh, shipped, "shipped TS blob must equal a fresh extraction+compile");
    }

    @Test
    void shippedTsxBlobIsByteIdenticalToFreshBuild() throws IOException {
        ExtractedGrammar g = ParserCExtractor.extract(
                Files.readString(TSX_PARSER_C, StandardCharsets.UTF_8));
        g.scannerProgram = io.nop.treesitter.scanner.ScannerCompiler.compile(
                Files.readString(SCANNER_DSL, StandardCharsets.UTF_8), g.externalScannerSymbolMap);
        byte[] fresh = BlobWriter.write(g);
        byte[] shipped = Files.readAllBytes(Path.of(
                "src/main/resources/grammars/tsx/tree-sitter-tsx-blob.bin"));
        assertArrayEquals(fresh, shipped, "shipped TSX blob must equal a fresh extraction+compile");
    }

    @Test
    void scannerDslIsSharedVerbatimByBothDialects() throws IOException {
        String shared = Files.readString(SCANNER_DSL, StandardCharsets.UTF_8);
        assertEquals(shared, Files.readString(TS_PARSER_C.resolveSibling("scanner.dsl"),
                StandardCharsets.UTF_8), "typescript dialect uses the shared dsl verbatim");
        assertEquals(shared, Files.readString(TSX_PARSER_C.resolveSibling("scanner.dsl"),
                StandardCharsets.UTF_8), "tsx dialect uses the shared dsl verbatim");
    }
}
