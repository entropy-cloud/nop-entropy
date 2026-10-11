package io.nop.lint.core.testing;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the {@link TreeEquivalenceOracle} itself (plan Phase 2
 * decision): the oracle must accept re-parses of the same source, and must
 * be sensitive to both node type and byte range differences — an oracle that
 * always returns true would silently void the whole incremental gate.
 */
public class TestTreeEquivalenceOracle {

    private static final Language JAVA =
            Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");

    private static TSTree parse(String source) {
        return TSParser.parse(JAVA, source.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void profileIsNonEmptyAndCoversTheRoot() {
        TSTree tree = parse("class A {}");
        var profile = TreeEquivalenceOracle.profile(tree);
        assertFalse(profile.isEmpty());
        assertTrue(profile.get(0).startsWith("program@"), "root entry first: " + profile.get(0));
        assertTrue(profile.get(0).endsWith(".." + sourceBytes("class A {}")));
    }

    @Test
    public void sameSourceParsesAreEquivalent() {
        TSTree left = parse("class A { void m() { foo.bar(); } }");
        TSTree right = parse("class A { void m() { foo.bar(); } }");
        assertTrue(TreeEquivalenceOracle.equivalent(left, right));
        TreeEquivalenceOracle.assertEquivalent(left, right, "same source");
    }

    @Test
    public void differentSourcesAreNotEquivalent() {
        // The added argument changes both the node set (argument_list) and
        // every later byte range.
        TSTree left = parse("class A { void m() { foo.bar(); } }");
        TSTree right = parse("class A { void m() { foo.bar(1); } }");
        assertFalse(TreeEquivalenceOracle.equivalent(left, right));
    }

    @Test
    public void textOnlyDifferencesWithIdenticalProfilesAreEquivalentByContract() {
        // The oracle judges type/startByte/endByte profiles (plan contract):
        // renaming an identifier keeps every node type and byte range, so the
        // profiles match. For the incremental ≡ full gate this is sound — the
        // two compared trees always span the same source bytes.
        TSTree left = parse("class A { void m() { foo.bar(); } }");
        TSTree right = parse("class A { void m() { foo.baz(); } }");
        assertTrue(TreeEquivalenceOracle.equivalent(left, right));
    }

    @Test
    public void oracleIsSensitiveToByteRanges() {
        // Same node kinds, different spans: the identifier length differs.
        TSTree left = parse("class A {}");
        TSTree right = parse("class AA {}");
        assertFalse(TreeEquivalenceOracle.equivalent(left, right),
                "equal type sequence with shifted ranges must not pass");
    }

    @Test
    public void oracleIsSensitiveToNodeTypes() {
        // Same byte length, different root node type.
        TSTree left = parse("class A {}");
        TSTree right = parse("interface A {}");
        assertNotEquals(TreeEquivalenceOracle.profile(left).get(0),
                TreeEquivalenceOracle.profile(right).get(0));
        assertFalse(TreeEquivalenceOracle.equivalent(left, right),
                "equal byte layout with a different node type must not pass");
    }

    @Test
    public void assertEquivalentNamesTheDivergence() {
        TSTree left = parse("class A {}");
        TSTree right = parse("class AA {}");
        AssertionError error = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
                () -> TreeEquivalenceOracle.assertEquivalent(left, right, "diverging trees"));
        assertTrue(error.getMessage().contains("first difference at entry"),
                "message must localize the divergence: " + error.getMessage());
        // The identifier 'A' vs 'AA' diverges at the identifier entry:
        // same kind, different byte span (6..7 vs 6..8).
        assertTrue(error.getMessage().contains("identifier@6..7"), error.getMessage());
        assertEquals(10, sourceBytes("class A {}"));
    }

    private static int sourceBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
