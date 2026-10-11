package io.nop.treesitter.cursor;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: upstream cross-check — for Java corpus fixtures the cursor-reachable
 * <em>named</em> node sequence must equal the named nodes of the expected
 * s-expression tree (byte-order equivalence on the visible spine), driven by
 * the exact same {@code TSParser.parse → TSTreeCursor} path as Phases 1–2.
 * Also records per-node cursor step allocation counts for roadmap item 13
 * (no tuning in this plan).
 */
public class UpstreamCursorCrossCheckTest {

    private static Language JAVA;

    private record Section(String file, String title, String input, String expected) {
    }

    @BeforeAll
    static void loadJava() {
        JAVA = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
    }

    private static List<Section> readSections() throws IOException {
        List<Section> sections = new ArrayList<>();
        for (String corpusFile : List.of("comments.txt", "declarations.txt", "expressions.txt",
                "literals.txt", "precedence.txt", "types.txt")) {
            Path corpus = Path.of("src/test/resources/upstream/grammars/tree-sitter-java/test/corpus")
                    .resolve(corpusFile);
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
                sections.add(new Section(corpusFile, title,
                        String.join("\n", input), String.join("\n", expected)));
            }
        }
        return sections;
    }

    /**
     * Named node type names in an s-expression (including field-prefixed lines),
     * in document order.
     */
    private static List<String> sexpNamedTypes(String sexp) {
        List<String> out = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\(([A-Za-z_][A-Za-z0-9_]*)").matcher(sexp);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }

    /**
     * Preorder named node sequence reachable purely through the cursor.
     */
    private static List<String> cursorNamedSequence(TSTree tree) {
        List<String> out = new ArrayList<>();
        TSTreeCursor cursor = tree.cursor();
        out.add(cursor.currentNode().type());
        descendNamed(cursor, out);
        return out;
    }

    private static void descendNamed(TSTreeCursor cursor, List<String> out) {
        if (!cursor.gotoFirstNamedChild()) {
            return;
        }
        while (true) {
            out.add(cursor.currentNode().type());
            descendNamed(cursor, out);
            if (!cursor.gotoNextNamedChild()) {
                break;
            }
        }
        cursor.gotoParent();
    }

    @Test
    void cursorNamedSequenceEqualsExpectedSExpressionOnAtLeastFiveCorpusFixtures() throws IOException {
        List<Section> sections = readSections();
        assertTrue(sections.size() >= 100, "vendored Java corpus must not be empty");

        int checked = 0;
        List<String> failures = new ArrayList<>();
        for (Section section : sections) {
            TSTree tree = TSParser.parse(JAVA, section.input());
            List<String> cursorSeq = cursorNamedSequence(tree);
            List<String> expected = sexpNamedTypes(section.expected());
            if (!cursorSeq.equals(expected)) {
                failures.add(section.file() + ": '" + section.title() + "'\n  cursor=" + cursorSeq
                        + "\n  expected=" + expected);
            }
            checked++;
        }
        System.out.println("Java corpus cursor cross-check: " + (checked - failures.size()) + "/" + checked
                + " fixtures match");
        assertTrue(checked >= 5, "cross-check must cover at least 5 upstream fixtures");
        assertTrue(failures.isEmpty(), "cursor named sequence mismatch on " + failures.size()
                + " fixtures:\n" + String.join("\n", failures));
    }

    @Test
    void cursorStepAllocationCountsAreRecordedForRoadmapItem13() {
        String source = """
                package demo;

                import java.util.List;

                public class Sample {
                    private final List<String> names = new ArrayList<>();

                    public int sum(int a, int b) {
                        int total = a + b;
                        return total;
                    }

                    // a comment
                    public void print(List<String> items) {
                        for (String item : items) {
                            System.out.println(item);
                        }
                    }
                }
                """;
        TSTree tree = TSParser.parse(JAVA, source);
        TSTreeCursor cursor = tree.cursor();
        int nodes = countNodes(cursor);
        int maxDepth = maxDepth(cursor);
        System.out.println("Java fixture cursor profile: nodes=" + nodes + " maxDepth=" + maxDepth
                + " cursorSteps=" + nodes + " (per-node step allocation, no tuning in this plan)");
        assertTrue(nodes > 50, "the fixture must exercise a meaningful cursor walk");
    }

    private static int countNodes(TSTreeCursor cursor) {
        int nodes = 1;
        if (cursor.gotoFirstChild()) {
            while (true) {
                nodes += countNodes(cursor);
                if (!cursor.gotoNextSibling()) {
                    break;
                }
            }
            cursor.gotoParent();
        }
        return nodes;
    }

    private static int maxDepth(TSTreeCursor cursor) {
        int depth = cursor.depth();
        if (cursor.gotoFirstChild()) {
            while (true) {
                depth = Math.max(depth, maxDepth(cursor));
                if (!cursor.gotoNextSibling()) {
                    break;
                }
            }
            cursor.gotoParent();
        }
        return depth;
    }
}