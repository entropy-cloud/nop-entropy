package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4: upstream JavaScript corpus runner on the external-token path. A
 * section is <b>in scope</b> iff its expected s-expression references one of
 * the grammar's 8 external token symbols ({@code _automatic_semicolon},
 * {@code string_fragment}, {@code ?}, {@code html_comment}, {@code ||},
 * {@code escape_sequence}, {@code regex_pattern}, {@code jsx_text}) — every
 * in-scope section must pass 100% via the same {@code TSParser.parse} path as
 * the unit tests (Language → table-driven Lexer with the external-scan VM
 * integration → GLR parser → TSTree). Out-of-scope sections are skipped with
 * the recorded list; the full JS/TS corpus is roadmap item 10's acceptance.
 */
public class JsCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/test/corpus");

    private static final List<String> EXTERNAL_TOKEN_NAMES = List.of(
            "_automatic_semicolon", "string_fragment", "(?)", "html_comment",
            "(||)", "escape_sequence", "regex_pattern", "jsx_text");

    /**
     * Adjudicated in-scope deviation (recorded with upstream evidence, not a
     * silent skip): the expected tree contains {@code (ERROR ...)} and
     * {@code (MISSING ";")} nodes, which only the error-recovery machinery can
     * produce — roadmap item 11's scope, a Non-Goal of this plan.
     */
    private static final List<String> ERROR_RECOVERY_SECTIONS = List.of(
            "destructuring.txt: 'Extra complex literals in expressions'");

    private record Section(String file, String title, String input, String expected, boolean hasFields) {
    }

    private static List<Section> readSections() throws IOException {
        List<Section> sections = new ArrayList<>();
        for (String corpusFile : List.of("destructuring.txt", "expressions.txt", "injectables.txt",
                "literals.txt", "semicolon_insertion.txt", "statements.txt")) {
            Path corpus = CORPUS_DIR.resolve(corpusFile);
            List<String> lines = Files.readAllLines(corpus, StandardCharsets.UTF_8);
            int i = 0;
            int n = lines.size();
            while (i < n) {
                if (!lines.get(i).startsWith("=")) {
                    i++;
                    continue;
                }
                String title = lines.get(i + 1);
                i += 3;
                while (i < n && lines.get(i).isEmpty()) {
                    i++;
                }
                List<String> input = new ArrayList<>();
                while (i < n && !lines.get(i).matches("-{3,}")) {
                    input.add(lines.get(i));
                    i++;
                }
                i++;
                while (i < n && lines.get(i).isEmpty()) {
                    i++;
                }
                List<String> expected = new ArrayList<>();
                while (i < n && !lines.get(i).startsWith("=")) {
                    expected.add(lines.get(i));
                    i++;
                }
                String expectedText = String.join("\n", expected);
                String normalized = JavaCorpusTest.normalizeSexpOutput(expectedText);
                sections.add(new Section(corpusFile, title,
                        String.join("\n", input), normalized, normalized.contains(": (")));
            }
        }
        return sections;
    }

    private static boolean inScope(Section section) {
        for (String marker : EXTERNAL_TOKEN_NAMES) {
            if (section.expected().contains(marker)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyInScopeJsCorpusSectionParsesToTheExpectedTree() throws IOException {
        Language language = Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin");
        List<Section> sections = readSections();
        assertFalse(sections.isEmpty(), "vendored JS corpus must not be empty");

        List<Section> inScope = sections.stream().filter(JsCorpusTest::inScope).toList();
        List<Section> skipped = sections.stream().filter(s -> !inScope(s)).toList();
        assertTrue(inScope.size() >= 20, "expected ~34 in-scope sections, got " + inScope.size());

        List<String> failures = new ArrayList<>();
        List<Section> adjudicated = new ArrayList<>();
        for (Section section : inScope) {
            if (ERROR_RECOVERY_SECTIONS.contains(section.file() + ": '" + section.title() + "'")) {
                adjudicated.add(section);
                continue;
            }
            TSTree tree = TSParser.parse(language, section.input());
            String actual = tree.toSexpString(section.hasFields());
            if (!section.expected().equals(actual)) {
                failures.add(section.file() + ": '" + section.title() + "'");
            }
        }
        System.out.println("JS corpus: " + (inScope.size() - failures.size() - adjudicated.size()) + "/"
                + (inScope.size() - adjudicated.size()) + " in-scope sections pass");
        if (!adjudicated.isEmpty()) {
            System.out.println("Adjudicated in-scope sections (error recovery = roadmap item 11):");
            for (Section s : adjudicated) {
                System.out.println("  - " + s.file() + ": '" + s.title() + "'");
            }
        }
        if (!failures.isEmpty()) {
            System.out.println("Failing in-scope sections:");
            for (String f : failures) {
                System.out.println("  - " + f);
            }
        }
        if (!skipped.isEmpty()) {
            System.out.println("Skipped out-of-scope sections (" + skipped.size()
                    + "; full corpus is roadmap item 10):");
            for (Section s : skipped) {
                System.out.println("  - " + s.file() + ": '" + s.title() + "'");
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " in-scope sections fail");
    }
}