package io.nop.treesitter.codegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point: reads an upstream tree-sitter {@code parser.c} and emits a
 * compact binary blob containing the extracted parse tables.
 *
 * <p>Usage: {@code Ts2Java <parser.c> <out-blob>}</p>
 *
 * <p>Exit codes: 0 on success, 2 on usage error; extraction / IO failure propagates
 * as an uncaught {@link IllegalStateException} (JVM exit code 1).</p>
 */
public final class Ts2Java {

    private Ts2Java() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: Ts2Java <parser.c> <out-blob>");
            System.exit(2);
        }
        try {
            run(Path.of(args[0]), Path.of(args[1]));
        } catch (IllegalStateException | IOException e) {
            throw new IllegalStateException("Ts2Java failed on " + args[0] + " -> " + args[1], e);
        }
    }

    static void run(Path parserC, Path outBlob) throws IOException {
        String source = Files.readString(parserC, StandardCharsets.UTF_8);
        ExtractedGrammar g = ParserCExtractor.extract(source);
        compileScannerDsl(parserC, g);
        byte[] blob = BlobWriter.write(g);
        Files.write(outBlob, blob);
        System.out.println("wrote " + outBlob + " (" + blob.length + " bytes, "
                + g.stateCount + " states, " + g.symbolCount + " symbols)");
    }

    /**
     * When a {@code scanner.dsl} file sits next to the {@code parser.c}, the
     * external-scanner DSL (the hand translation of the grammar's
     * {@code scanner.c}) is compiled into the blob's scanner-program section.
     */
    static void compileScannerDsl(Path parserC, ExtractedGrammar g) throws IOException {
        Path dsl = parserC.resolveSibling("scanner.dsl");
        if (!Files.exists(dsl)) {
            return;
        }
        int[] symbolMap = g.externalScannerSymbolMap;
        if (symbolMap == null || symbolMap.length == 0) {
            throw new IllegalStateException("scanner.dsl present at " + dsl
                    + " but the grammar has no external scanner symbol map");
        }
        String dslText = Files.readString(dsl, StandardCharsets.UTF_8);
        g.scannerProgram = io.nop.treesitter.scanner.ScannerCompiler.compile(dslText, symbolMap);
    }
}