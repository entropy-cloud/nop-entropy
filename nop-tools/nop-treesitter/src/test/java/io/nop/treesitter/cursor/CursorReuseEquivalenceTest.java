package io.nop.treesitter.cursor;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.bench.BenchSources;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.query.TSQuery;
import io.nop.treesitter.query.TSQueryCursor;
import io.nop.treesitter.query.TSQueryMatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Equivalence suite for the allocation-light hot paths: the reused thread-local
 * cursor and the primitive child location must produce byte-identical walk
 * traces, renders, query results and large-parse trees compared to the
 * fresh-cursor / pre-change behavior.
 */
public class CursorReuseEquivalenceTest {

    private static List<GrammarFixture> fixtures;

    record GrammarFixture(String name, Language language, String source) {
    }

    @BeforeAll
    static void loadFixtures() {
        fixtures = List.of(
                new GrammarFixture("json",
                        Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin"),
                        PerfFixtureSources.json()),
                new GrammarFixture("java",
                        Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
                        PerfFixtureSources.java()),
                new GrammarFixture("javascript",
                        Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin"),
                        PerfFixtureSources.javascript()),
                new GrammarFixture("typescript",
                        Language.fromClasspath("/grammars/typescript/tree-sitter-typescript-blob.bin"),
                        PerfFixtureSources.typescript()),
                new GrammarFixture("tsx",
                        Language.fromClasspath("/grammars/tsx/tree-sitter-tsx-blob.bin"),
                        PerfFixtureSources.tsx()),
                new GrammarFixture("python",
                        Language.fromClasspath("/grammars/python/tree-sitter-python-blob.bin"),
                        PerfFixtureSources.python()));
    }

    /**
     * (a) Three walk modes over every fixture — step cursor, random access
     * through the reused thread-local cursor, and {@code gotoChild(i)} descent
     * — must produce byte-identical traces. Chain containers, hidden nodes,
     * aliases, extras and fields are all on the walked spine.
     */
    @Test
    void walkModesAgreeOnAllFixtures() {
        for (GrammarFixture fixture : fixtures) {
            TSTree tree = TSParser.parse(fixture.language(), fixture.source());

            String stepTrace = stepCursorTrace(tree);
            String accessTrace = randomAccessTrace(tree.rootNode());
            String descentTrace = gotoChildTrace(tree);

            assertEquals(stepTrace, accessTrace,
                    "step vs random-access walk mismatch on " + fixture.name());
            assertEquals(stepTrace, descentTrace,
                    "step vs gotoChild descent walk mismatch on " + fixture.name());
            assertTrue(stepTrace.length() > 200,
                    "fixture " + fixture.name() + " must produce a meaningful tree");
        }
    }

    /**
     * (b) Back-to-back walks on different trees reusing the thread-local cursor
     * must not leak state: A → B → A produces the same trace as A alone.
     */
    @Test
    void interleavedWalksOnDifferentTreesAreStable() {
        TSTree javaTree = TSParser.parse(fixtures.get(1).language(), fixtures.get(1).source());
        TSTree pythonTree = TSParser.parse(fixtures.get(5).language(), fixtures.get(5).source());

        String javaAlone = randomAccessTrace(javaTree.rootNode());
        String pythonAlone = randomAccessTrace(pythonTree.rootNode());

        String first = randomAccessTrace(javaTree.rootNode());
        String second = randomAccessTrace(pythonTree.rootNode());
        String third = randomAccessTrace(javaTree.rootNode());

        assertEquals(javaAlone, first);
        assertEquals(pythonAlone, second);
        assertEquals(javaAlone, third);
    }

    /**
     * (c) Nested child access while a reused-cursor walk is in flight (the
     * depth-guarded fallback to fresh cursors) must not disturb the outer walk.
     */
    @Test
    void nestedChildAccessDoesNotDisturbOuterWalk() {
        for (GrammarFixture fixture : fixtures) {
            TSTree tree = TSParser.parse(fixture.language(), fixture.source());
            String plain = randomAccessTrace(tree.rootNode());
            String nested = nestedAccessTrace(tree.rootNode());
            assertEquals(plain, nested, "nested access changed the walk on " + fixture.name());
        }
    }

    /**
     * (d) Query-engine regression: running captures through a held
     * {@link TSQueryCursor} while interleaving child accessors on the same
     * thread (the overlap hazard) yields the same matches as a clean run.
     */
    @Test
    void queryMatchesUnaffectedByInterleavedChildAccess() throws IOException {
        GrammarFixture js = fixtures.get(2);
        String querySource = """
                (comment) @comment
                (number) @number
                (identifier) @identifier
                """;
        TSQuery query = TSQuery.compile(js.language(), querySource);

        TSTree tree = TSParser.parse(js.language(), js.source());
        List<String> clean = runQuery(query, tree, js.source());
        List<String> interleaved = runQueryInterleaved(query, tree, js.source());
        assertEquals(clean, interleaved);
        assertTrue(clean.size() >= 13, "the fixture must produce a meaningful match count");
    }

    /**
     * (e) Render output is byte-identical to the pre-change golden files for
     * all six grammars (both render entry points).
     */
    @Test
    void renderMatchesPreChangeGolden() throws IOException {
        for (GrammarFixture fixture : fixtures) {
            TSTree tree = TSParser.parse(fixture.language(), fixture.source());
            Path golden = Path.of("src/test/resources/render-golden/" + fixture.name() + ".sexp");
            String expected = Files.readString(golden, StandardCharsets.UTF_8);
            String actual = tree.toSexpString(true) + "\n---\n" + tree.toSExpression() + "\n";
            assertEquals(expected, actual, "render drift on " + fixture.name());
        }
    }

    /**
     * (f) A large parse on a pre-reserved arena (warm language) is
     * byte-identical to the same parse on a cold language (geometric growth).
     */
    @Test
    void largeParseWithReservationMatchesColdParse() {
        Language cold = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        byte[] source = BenchSources.jsonSource(1024 * 1024);

        TSTree coldTree = TSParser.parse(cold, source);
        TSTree warmTree = TSParser.parse(cold, source);
        assertTrue(cold.observedNodesPerByte() > 0, "the first large parse must record the ratio");

        assertEquals(coldTree.toSexpString(false), warmTree.toSexpString(false));
    }

    private static List<String> runQuery(TSQuery query, TSTree tree, String source) {
        TSQueryCursor cursor = new TSQueryCursor(query, tree, source);
        List<String> matches = new ArrayList<>();
        TSQueryMatch match;
        while ((match = cursor.nextMatch()) != null) {
            matches.add(match.patternIndex() + ":"
                    + match.captures().get(0).node().startByte());
        }
        return matches;
    }

    private static List<String> runQueryInterleaved(TSQuery query, TSTree tree, String source) {
        TSQueryCursor cursor = new TSQueryCursor(query, tree, source);
        List<String> matches = new ArrayList<>();
        TSQueryMatch match;
        while ((match = cursor.nextMatch()) != null) {
            matches.add(match.patternIndex() + ":"
                    + match.captures().get(0).node().startByte());
            int noise = tree.rootNode().childCount()
                    + match.captures().get(0).node().childCount();
            if (noise < 0) {
                throw new AssertionError();
            }
        }
        return matches;
    }

    private static String randomAccessTrace(TSNode root) {
        StringBuilder sb = new StringBuilder();
        appendRandomAccessTrace(sb, root, false);
        sb.append('@').append(root.startByte());
        return sb.toString();
    }

    private static String nestedAccessTrace(TSNode root) {
        StringBuilder sb = new StringBuilder();
        appendRandomAccessTrace(sb, root, true);
        sb.append('@').append(root.startByte());
        return sb.toString();
    }

    /**
     * Step-cursor walk driven by depth transitions: the cursor crosses
     * invisible nodes and exhausted levels automatically, so the renderer keeps
     * an explicit open-node stack (startByte per open node) and reconciles it
     * against {@code cursor.depth()} after every move.
     */
    private static String stepCursorTrace(TSTree tree) {
        TSTreeCursor cursor = tree.cursor();
        StringBuilder sb = new StringBuilder();
        List<Integer> open = new ArrayList<>();
        appendHead(sb, cursor.currentNode(), open);
        int depth = cursor.depth();
        while (true) {
            if (cursor.gotoFirstChild()) {
                sb.append(' ');
                depth = cursor.depth();
                appendHead(sb, cursor.currentNode(), open);
                continue;
            }
            closeTop(sb, open);
            while (true) {
                if (cursor.gotoNextSibling()) {
                    depth = cursor.depth();
                    while (open.size() > depth) {
                        closeTop(sb, open);
                    }
                    sb.append(' ');
                    appendHead(sb, cursor.currentNode(), open);
                    break;
                }
                if (!cursor.gotoParent()) {
                    while (!open.isEmpty()) {
                        closeTop(sb, open);
                    }
                    return sb.toString();
                }
                depth = cursor.depth();
                while (open.size() > depth) {
                    closeTop(sb, open);
                }
            }
        }
    }

    private static void appendHead(StringBuilder sb, TSNode node, List<Integer> open) {
        sb.append('(').append(node.type());
        if (node.isExtra()) {
            sb.append('!');
        }
        if (node.named()) {
            sb.append('#');
        }
        open.add(node.startByte());
    }

    private static void closeTop(StringBuilder sb, List<Integer> open) {
        sb.append(')').append('@').append(open.remove(open.size() - 1));
    }

    private static String gotoChildTrace(TSTree tree) {
        TSTreeCursor cursor = tree.cursor();
        StringBuilder sb = new StringBuilder();
        appendGotoChildTrace(sb, cursor);
        sb.append('@').append(cursor.currentNode().startByte());
        return sb.toString();
    }

    private static void appendGotoChildTrace(StringBuilder sb, TSTreeCursor cursor) {
        TSNode node = cursor.currentNode();
        sb.append('(').append(node.type());
        if (node.isExtra()) {
            sb.append('!');
        }
        if (node.named()) {
            sb.append('#');
        }
        int count = cursor.childCount();
        for (int i = 0; i < count; i++) {
            if (cursor.gotoChild(i)) {
                sb.append(' ');
                appendGotoChildTrace(sb, cursor);
                sb.append('@').append(cursor.currentNode().startByte());
                cursor.gotoParent();
            }
        }
        sb.append(')');
    }

    private static long probeSink;

    private static void appendRandomAccessTrace(StringBuilder sb, TSNode node, boolean probe) {
        sb.append('(').append(node.type());
        if (node.isExtra()) {
            sb.append('!');
        }
        if (node.named()) {
            sb.append('#');
        }
        int count = node.childCount();
        for (int i = 0; i < count; i++) {
            TSNode child = node.child(i);
            sb.append(' ');
            if (probe && count > 1) {
                int inner = child.childCount();
                if (inner > 0) {
                    probeSink += child.child(0).type().length() + inner;
                }
            }
            appendRandomAccessTrace(sb, child, probe);
            sb.append('@').append(child.startByte());
        }
        sb.append(')');
    }
}
