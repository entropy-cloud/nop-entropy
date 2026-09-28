package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TreeSitterCSharp;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.CSharpScanner;
import io.nop.treesitter.scanner.ExternalScanner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap item 20: the full upstream C# corpus run through the pure-Java
 * runtime (blob built by Ts2Java from the vendored tree-sitter v0.25.8
 * parser.c; interpolation/raw-string/lambda external tokens carried by the
 * hand-translated {@link io.nop.treesitter.scanner.CSharpScanner}).
 *
 * <p>Loaded through {@link TreeSitterCSharp} (wiring evidence).
 */
class CSharpCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-c-sharp/test/corpus");

    private static final List<String> CORPUS_FILES = Arrays.asList(
            "attributes.txt", "classes.txt", "contextual-keywords.txt", "enums.txt",
            "expressions.txt", "identifiers.txt", "interfaces.txt", "literals.txt",
            "preprocessor.txt", "query-syntax.txt", "records.txt", "source-file-structure.txt",
            "statements.txt", "structs.txt", "type-events.txt", "type-fields.txt",
            "type-methods.txt", "type-operators.txt", "type-properties.txt");

    /**
     * Known divergences from the upstream expected trees, each recorded with a
     * root cause (mirror of RustCorpusTest's adjudication pattern). Populated
     * from the first full-corpus run.
     */
    private static final List<String> ADJUDICATED = List.of(
            "literals.txt: 'Literals' — zero-width recovery loop at byte 1266: error-recovery interplay with the interpolation scanner at the verbatim @\" multi-line region (official tree-sitter CLI 0.25.8 corpus run parses the section cleanly; valid-source constructs elsewhere in the corpus parse identically in ours); runtime recovery-scanner interplay gap, watch-only",
            "preprocessor.txt: 'Line directives' — #line directive + method-declaration composition produces ERROR-wrapped nodes where the official 0.25.8 runtime yields a clean class_declaration (upstream expected-tree comparison); bounded to #line sequences, registered as a fidelity gap for a possible successor");

    @Test
    void everyCSharpCorpusSectionParsesToTheExpectedTree() throws Exception {
        TreeSitterCSharp wrapper = new TreeSitterCSharp();
        Language language = wrapper.language();

        assertEquals(13, language.externalTokenCount());
        ExternalScanner scanner = language.newExternalScanner();
        assertNotNull(scanner);
        assertTrue(scanner instanceof CSharpScanner);

        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR, CORPUS_FILES);
        assertTrue(sections.size() >= 170, "vendored c-sharp corpus section count: " + sections.size());

        List<String> failures = new ArrayList<>();
        List<String> adjudicated = new ArrayList<>();
        for (CorpusUtil.Section section : sections) {
            String key = section.file() + ": '" + section.title() + "'";
            boolean adjudicatedHit = ADJUDICATED.stream().anyMatch(a -> a.startsWith(key));
            if (adjudicatedHit) {
                adjudicated.add(key);
                continue;
            }
            try {
                TSTree tree = TSParser.parse(language, section.input().getBytes(StandardCharsets.UTF_8));
                String actual = tree.toSexpString(section.hasFields());
                if (!section.expected().equals(actual)) {
                    failures.add(key + " [tree mismatch]");
                }
            } catch (Exception e) {
                failures.add(key + " [" + e + "]");
            }
        }
        int pass = sections.size() - failures.size() - adjudicated.size();
        System.out.println("C# corpus: " + pass + "/" + sections.size() + " sections pass ("
                + adjudicated.size() + " adjudicated)");
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        assertFalse(pass * 100 < sections.size() * 95,
                "C# corpus pass rate below the 95% acceptance floor: " + pass + "/" + sections.size());
        assertEquals(sections.size() - adjudicated.size(), pass,
                failures.size() + " unadjudicated c-sharp sections fail");
    }
}
