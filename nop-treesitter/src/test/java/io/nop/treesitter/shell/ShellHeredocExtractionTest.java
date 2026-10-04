package io.nop.treesitter.shell;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capability 3: heredoc extraction.
 *
 * <p>The negatives are the substance of this capability. "Find the first
 * {@code heredoc_redirect} and return it" satisfies every positive case and is
 * wrong: a guardrail that validates the wrong text is worse than one that
 * declines. So the negative set is deliberately systematic — every way the
 * heredoc can stop being the only thing in the script.</p>
 */
class ShellHeredocExtractionTest {

    private final ShellAnalysis analysis = ShellTestSupport.analysis();

    private static final Set<String> APPLY_PATCH = Set.of("apply_patch");

    // ---- accepted shapes ----------------------------------------------------

    @Test
    void directForm() {
        HeredocScript.Lookup lookup =
                analysis.extractHeredoc("apply_patch <<'PATCH'\nbody\nPATCH\n", Set.of());
        assertTrue(lookup.isFound(), String.valueOf(lookup.reason()));
        HeredocScript.Found found = lookup.found();
        assertEquals("'PATCH'", found.delimiter());
        assertEquals("body\n", found.body());
        assertEquals("apply_patch", found.commandName());
    }

    /** A multi-byte body must be sliced by byte offset, not run past its end. */
    @Test
    void aNonAsciiBodyIsSlicedByByteOffset() {
        HeredocScript.Lookup lookup =
                analysis.extractHeredoc("cat <<EOF\ncafé\nEOF\n", Set.of());
        assertTrue(lookup.isFound(), String.valueOf(lookup.reason()));
        assertEquals("café\n", lookup.found().body(),
                "the body must not overrun into the delimiter");
    }

    @Test
    void unquotedDelimiterIsNotLiteral() {
        HeredocScript.Found found = analysis
                .extractHeredoc("cat <<EOF\n$USER\nEOF\n", Set.of()).found();
        assertTrue(found != null);
        assertFalse(found.quotedDelimiter());
        assertFalse(found.bodyIsLiteral(),
                "an unquoted delimiter lets the body expand, so it is not a pure literal");
        assertTrue(found.body().contains("$USER"));
    }

    @Test
    void quotedDelimiterMakesTheBodyLiteral() {
        for (String script : new String[]{
                "cat <<'EOF'\n$USER\nEOF\n",
                "cat <<\"EOF\"\n$USER\nEOF\n",
                "cat <<\\EOF\n$USER\nEOF\n"}) {
            HeredocScript.Lookup lookup = analysis.extractHeredoc(script, Set.of());
            assertTrue(lookup.isFound(), script + " -> " + lookup.reason());
            assertTrue(lookup.found().quotedDelimiter(), script);
            assertTrue(lookup.found().bodyIsLiteral(), script);
        }
    }

    @Test
    void dashDelimiter() {
        HeredocScript.Lookup lookup =
                analysis.extractHeredoc("cat <<-EOF\n\tbody\n\tEOF\n", Set.of());
        assertTrue(lookup.isFound(), String.valueOf(lookup.reason()));
    }

    @Test
    void cdPrefixForm() {
        HeredocScript.Lookup lookup = analysis.extractHeredoc(
                "cd /tmp && apply_patch <<'PATCH'\nbody\nPATCH\n", APPLY_PATCH);
        assertTrue(lookup.isFound(), String.valueOf(lookup.reason()));
        assertEquals("apply_patch", lookup.found().commandName());
    }

    // ---- negatives: the reference implementation's anchor negatives ---------

    @Test
    void refusesNonAndSeparators() {
        for (String script : new String[]{
                "cd /tmp; apply_patch <<'PATCH'\nbody\nPATCH\n",
                "cd /tmp || apply_patch <<'PATCH'\nbody\nPATCH\n",
                "cd /tmp | apply_patch <<'PATCH'\nbody\nPATCH\n"}) {
            assertFalse(analysis.extractHeredoc(script, APPLY_PATCH).isFound(),
                    "<<" + script.split("\n")[0] + ">> must be declined");
        }
    }

    @Test
    void refusesExtraCommandsAroundTheHeredoc() {
        for (String script : new String[]{
                "echo foo && apply_patch <<'PATCH'\nbody\nPATCH\n",
                "cd foo && cd bar && apply_patch <<'PATCH'\nbody\nPATCH\n",
                "echo foo; cd bar && apply_patch <<'PATCH'\nbody\nPATCH\n"}) {
            assertFalse(analysis.extractHeredoc(script, APPLY_PATCH).isFound(),
                    "<<" + script.split("\n")[0] + ">> must be declined");
        }
    }

    @Test
    void refusesCommandsAfterTheHeredoc() {
        assertFalse(analysis.extractHeredoc(
                "apply_patch <<'PATCH'\nbody\nPATCH\necho done\n", Set.of()).isFound());
    }

    /**
     * A heredoc command that takes arguments has the same tree shape as one that
     * does not — {@code apply_patch foo <<'PATCH'} and {@code apply_patch
     * <<'PATCH'} differ only by a word. The reference implementation's anchor
     * excludes the first, and so must this: the plan's own negative set names it.
     */
    @Test
    void refusesAHeredocCommandThatTakesArguments() {
        assertFalse(analysis.extractHeredoc(
                "apply_patch foo <<'PATCH'\nbody\nPATCH\n", Set.of()).isFound());
        assertFalse(analysis.extractHeredoc(
                "cd /tmp && apply_patch foo <<'PATCH'\nbody\nPATCH\n", APPLY_PATCH).isFound());
    }

    @Test
    void refusesCdWithTheWrongArity() {
        assertFalse(analysis.extractHeredoc(
                "cd foo bar && apply_patch <<'PATCH'\nbody\nPATCH\n", APPLY_PATCH).isFound());
    }

    @Test
    void refusesACommandOutsideTheAllowedSet() {
        assertFalse(analysis.extractHeredoc(
                "cd /tmp && rm <<'PATCH'\nbody\nPATCH\n", APPLY_PATCH).isFound());
    }

    /** With no allow-set supplied, the {@code cd} prefix form cannot be accepted. */
    @Test
    void refusesCdPrefixWhenNoAllowedSetIsGiven() {
        assertFalse(analysis.extractHeredoc(
                "cd /tmp && apply_patch <<'PATCH'\nbody\nPATCH\n", Set.of()).isFound());
    }

    /** A heredoc that is not the only top-level statement. */
    @Test
    void refusesHeredocThatIsNotTheOnlyStatement() {
        assertFalse(analysis.extractHeredoc(
                "echo x && cat <<EOF\nbody\nEOF\n", Set.of()).isFound());
    }

    @Test
    void refusesAMalformedScript() {
        assertFalse(analysis.extractHeredoc("cat <<EOF\nbody\n", Set.of()).isFound());
    }

    @Test
    void refusesAnEmptyScript() {
        assertFalse(analysis.extractHeredoc("", Set.of()).isFound());
    }

    /** Herestring is a different node and is not part of this contract. */
    @Test
    void refusesHerestring() {
        HeredocScript.Lookup lookup = analysis.extractHeredoc("cat <<<word\n", Set.of());
        assertFalse(lookup.isFound(), "<<< is a herestring_redirect, not a heredoc_redirect");
    }

    @Test
    void refusalCarriesAReasonRatherThanAnEmptyResult() {
        HeredocScript.Lookup lookup = analysis.extractHeredoc("echo hi\n", Set.of());
        assertFalse(lookup.isFound());
        assertFalse(lookup.reason().isEmpty(),
                "a declined extraction must say why, so 'not found' and 'not analysed' differ");
        assertEquals(null, lookup.found());
    }
}
