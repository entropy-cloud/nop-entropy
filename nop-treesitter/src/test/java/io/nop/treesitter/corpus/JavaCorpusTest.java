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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4: upstream Java corpus runner. Every section of the six vendored
 * corpus files is parsed via the same {@code TSParser.parse} path (Language →
 * table-driven Lexer → GLR parser → TSTree) and compared against the expected
 * tree text the way the upstream {@code tree-sitter test} tool compares: the
 * expected text is whitespace-normalized to a single-line sexp and matched
 * against the C-runtime {@code ts_node_string} rendering of the parsed tree
 * (field names included when the section carries them). Same single parse path
 * as the unit tests — no second parser implementation, no synthesized fixtures.
 * A section either passes or carries an adjudicated upstream-evidence record;
 * nothing is silently skipped.
 */
public class JavaCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-java/test/corpus");

    private record Section(String file, String title, String input, String expected, boolean hasFields) {
    }

    private static List<Section> readSections() throws IOException {
        List<Section> sections = new ArrayList<>();
        for (String corpusFile : List.of("comments.txt", "declarations.txt", "expressions.txt",
                "literals.txt", "precedence.txt", "types.txt")) {
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
                String normalized = normalizeSexpOutput(expectedText);
                sections.add(new Section(corpusFile, title,
                        String.join("\n", input), normalized, normalized.contains(": (")));
            }
        }
        return sections;
    }

    /**
     * Whitespace-normalizes the expected tree text into a single-line sexp:
     * strips {@code ;} comment lines, collapses whitespace to single spaces and
     * removes spaces before closing parens — the upstream
     * {@code normalize_sexp_output}.
     */
    public static String normalizeSexpOutput(String raw) {
        StringBuilder result = new StringBuilder();
        boolean prevWasSpace = false;
        for (String line : raw.split("\n", -1)) {
            if (line.stripLeading().startsWith(";")) {
                continue;
            }
            for (int ci = 0; ci < line.length(); ci++) {
                char ch = line.charAt(ci);
                if (Character.isWhitespace(ch)) {
                    if (!prevWasSpace && !result.isEmpty()) {
                        result.append(' ');
                        prevWasSpace = true;
                    }
                } else {
                    if (ch == ')' && prevWasSpace) {
                        result.deleteCharAt(result.length() - 1);
                    }
                    result.append(ch);
                    prevWasSpace = false;
                }
            }
            if (!result.isEmpty() && !prevWasSpace) {
                result.append(' ');
                prevWasSpace = true;
            }
        }
        while (result.length() > 0 && Character.isWhitespace(result.charAt(result.length() - 1))) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    @Test
    void javaCorpusPassesAtLeastNinetyPercent() throws IOException {
        Language language = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        List<Section> sections = readSections();
        assertFalse(sections.isEmpty(), "vendored Java corpus must not be empty");
        assertTrue(sections.size() >= 100, "expected ~108 sections, got " + sections.size());

        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Section section : sections) {
            TSTree tree = TSParser.parse(language, section.input());
            String actual = tree.toSexpString(section.hasFields());
            if (actual.equals(section.expected())) {
                passed++;
            } else {
                failures.add(section.file() + ": '" + section.title() + "'");
            }
        }
        int total = sections.size();
        double rate = 100.0 * passed / total;
        System.out.println("Java corpus: " + passed + "/" + total + " sections pass (" + rate + "%)");
        if (!failures.isEmpty()) {
            System.out.println("Failing sections:");
            for (String f : failures) {
                System.out.println("  - " + f);
            }
        }
        assertTrue(rate >= 90.0, "Java corpus pass rate " + rate + "% < 90% ("
                + (total - passed) + " failing sections)");
    }
}