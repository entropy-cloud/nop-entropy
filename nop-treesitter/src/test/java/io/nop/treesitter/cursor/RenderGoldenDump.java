package io.nop.treesitter.cursor;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dumps the pre-change render output of the six shared fixtures to
 * src/test/resources/render-golden/ so the equivalence tests can assert
 * byte-identical renderings after the allocation-reduction changes. Run on the
 * PRE-CHANGE classpath only.
 */
public final class RenderGoldenDump {

    public static void main(String[] args) throws IOException {
        dump("json", "/grammars/json/tree-sitter-json-blob.bin");
        dump("java", "/grammars/java/tree-sitter-java-blob.bin");
        dump("javascript", "/grammars/javascript/tree-sitter-javascript-blob.bin");
        dump("typescript", "/grammars/typescript/tree-sitter-typescript-blob.bin");
        dump("tsx", "/grammars/tsx/tree-sitter-tsx-blob.bin");
        dump("python", "/grammars/python/tree-sitter-python-blob.bin");
    }

    private static void dump(String name, String blob) throws IOException {
        Language language = Language.fromClasspath(blob);
        TSTree tree = TSParser.parse(language, sourceOf(name));
        Path out = Path.of("src/test/resources/render-golden/" + name + ".sexp");
        Files.createDirectories(out.getParent());
        String golden = tree.toSexpString(true) + "\n---\n" + tree.toSExpression() + "\n";
        Files.writeString(out, golden, StandardCharsets.UTF_8);
        System.out.println(name + ": " + golden.length() + " bytes");
    }

    private static String sourceOf(String name) {
        return switch (name) {
            case "json" -> PerfFixtureSources.json();
            case "java" -> PerfFixtureSources.java();
            case "javascript" -> PerfFixtureSources.javascript();
            case "typescript" -> PerfFixtureSources.typescript();
            case "tsx" -> PerfFixtureSources.tsx();
            case "python" -> PerfFixtureSources.python();
            default -> throw new IllegalArgumentException(name);
        };
    }
}
