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

/**
 * Phase 4: upstream corpus runner. Every section of the vendored
 * {@code main.txt} is parsed via the same {@code TSParser.parse} path as the
 * unit tests and compared byte-for-byte against the expected tree text — no
 * second parser implementation, no skipped sections, no synthesized fixtures.
 */
class JsonCorpusTest {

    private static final Path CORPUS = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-json/test/corpus/main.txt");

    private record Section(String title, String input, String expected) {
    }

    private static List<Section> readSections() throws IOException {
        List<String> lines = Files.readAllLines(CORPUS, StandardCharsets.UTF_8);
        List<Section> sections = new ArrayList<>();
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
            sections.add(new Section(title, String.join("\n", input), String.join("\n", expected).trim()));
        }
        return sections;
    }

    @Test
    void everyCorpusSectionParsesToTheExpectedTree() throws IOException {
        Language language = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        List<Section> sections = readSections();
        assertFalse(sections.isEmpty(), "vendored corpus must not be empty");

        List<String> titles = sections.stream().map(Section::title).toList();
        assertEquals(List.of("Arrays", "String content", "Top-level numbers", "Exponents",
                "Top-level null", "Comments", "Multiple top-level objects"), titles);

        for (Section section : sections) {
            TSTree tree = TSParser.parse(language, section.input());
            String actual = tree.toSExpression().trim();
            assertEquals(section.expected(), actual,
                    "corpus section '" + section.title() + "' does not match the expected tree");
        }
    }
}