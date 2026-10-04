package io.nop.treesitter.shell;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Capability 1: the proven command sequence.
 *
 * <p>Two groups of assertions. The first checks the <b>accept</b> side: what a
 * provable script looks like. The second, larger group checks the <b>refuse</b>
 * side, one case per construct the whitelist excludes — because the whitelist
 * exists to fail closed, and a whitelist with untested exclusions is a whitelist
 * that has only been tested half way.</p>
 */
class ShellProvenSplitTest {

    private final ShellAnalysis analysis = ShellTestSupport.analysis();

    // ---- accepted shapes ----------------------------------------------------

    @Test
    void singleCommand() {
        ProvenCommandSequence sequence = ShellTestSupport.proven(analysis, "echo hi\n");
        assertEquals(1, sequence.size());
        assertEquals("echo", sequence.commands().get(0).name());
        assertEquals(List.of("hi"), sequence.commands().get(0).arguments());
        assertTrue(sequence.isProvenLiteral());
    }

    @Test
    void sequentialCommandsKeepTheirOrderAndSeparators() {
        ProvenCommandSequence sequence =
                ShellTestSupport.proven(analysis, "echo hi; ls -la | grep x\n");
        assertEquals(List.of("echo", "ls", "grep"), sequence.commandNames());
        assertEquals("", sequence.commands().get(0).separator());
        assertEquals(";", sequence.commands().get(1).separator());
        assertEquals("|", sequence.commands().get(2).separator());
    }

    @Test
    void everyConnectorIsAccepted() {
        assertEquals(List.of("a", "b"), ShellTestSupport.proven(analysis, "a && b\n").commandNames());
        assertEquals(List.of("a", "b"), ShellTestSupport.proven(analysis, "a || b\n").commandNames());
        assertEquals(List.of("a", "b"), ShellTestSupport.proven(analysis, "a | b\n").commandNames());
        assertEquals(List.of("a", "b"), ShellTestSupport.proven(analysis, "a; b\n").commandNames());
    }

    @Test
    void doubleQuotedAndSingleQuotedArguments() {
        assertEquals(List.of("\"a b\""), ShellTestSupport.proven(analysis, "echo \"a b\"\n")
                .commands().get(0).arguments());
        assertEquals(List.of("'a b'"), ShellTestSupport.proven(analysis, "echo 'a b'\n")
                .commands().get(0).arguments());
    }

    @Test
    void numericArgument() {
        assertEquals(List.of("42"), ShellTestSupport.proven(analysis, "sleep 42\n")
                .commands().get(0).arguments());
    }

    @Test
    void concatenationOfTwoLiteralsIsLiteral() {
        // a"b" is one concatenation node holding only literals: it is a word
        // written in two pieces, and refusing it would lose provable scripts.
        assertEquals(List.of("a\"b\"c"),
                ShellTestSupport.proven(analysis, "echo a\"b\"c\n").commands().get(0).arguments());
    }

    // ---- refusals: one per excluded construct --------------------------------

    @Test
    void refusesSubshellParentheses() {
        assertRefused("(cd /tmp)", UnprovenScript.Reason.CONSTRUCT_OUTSIDE_WHITELIST);
    }

    @Test
    void refusesBraceGroup() {
        assertRefused("{ echo hi; }", UnprovenScript.Reason.CONSTRUCT_OUTSIDE_WHITELIST);
    }

    @Test
    void refusesAnyRedirection() {
        assertRefused("echo hi > out.txt", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
        assertRefused("echo hi < in.txt", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
        assertRefused("echo hi >> out.txt", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
    }

    @Test
    void refusesBackgroundOperator() {
        assertRefused("echo hi &", UnprovenScript.Reason.CONSTRUCT_OUTSIDE_WHITELIST);
    }

    @Test
    void refusesCommandSubstitutionBothSpellings() {
        assertRefused("echo $(date)", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
        assertRefused("echo `date`", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
    }

    @Test
    void refusesVariableExpansions() {
        assertRefused("echo $VAR", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
        assertRefused("echo ${VAR}", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
    }

    @Test
    void refusesProcessAndArithmeticSubstitution() {
        assertRefused("diff <(ls) <(ls)", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
        assertRefused("echo $((1 + 2))", UnprovenScript.Reason.DYNAMIC_CONSTRUCT);
    }

    @Test
    void refusesEveryControlFlowConstruct() {
        for (String script : new String[]{
                "if true; then echo hi; fi",
                "for i in a; do echo $i; done",
                "while true; do echo hi; done",
                "until true; do echo hi; done",
                "case x in a) ;; esac",
                "function f { echo hi; }",
                "select x in a; do echo $x; done"}) {
            UnprovenScript refusal = ShellTestSupport.unproven(analysis, script);
            assertTrue(refusal.reason() == UnprovenScript.Reason.CONSTRUCT_OUTSIDE_WHITELIST
                            || refusal.reason() == UnprovenScript.Reason.DYNAMIC_CONSTRUCT,
                    "<<" + script + ">> gave " + refusal);
        }
    }

    /**
     * The red line from the design contract: {@code declare -x A=1 rm -rf /data}
     * must never read as a proven command sequence. It is a declaration, not a
     * command, and a guardrail that sees {@code rm -rf /data} as proven is worse
     * than one that sees nothing.
     */
    @Test
    void refusesDeclarationCommandEvenWithADestructiveArgument() {
        UnprovenScript refusal =
                ShellTestSupport.unproven(analysis, "declare -x A=1 rm -rf /data\n");
        assertEquals(UnprovenScript.Reason.CONSTRUCT_OUTSIDE_WHITELIST, refusal.reason());
        assertEquals("declaration_command", refusal.nodeType());
    }

    @Test
    void refusesTestCommand() {
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, "[[ -f x ]]\n");
        assertEquals("test_command", refusal.nodeType());
    }

    @Test
    void refusesNegatedCommand() {
        assertEquals("negated_command",
                ShellTestSupport.unproven(analysis, "! echo hi\n").nodeType());
    }

    @Test
    void refusesVariablePrefixedCommand() {
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, "FOO=bar ls\n");
        assertEquals(UnprovenScript.Reason.DYNAMIC_CONSTRUCT, refusal.reason());
    }

    @Test
    void refusesHeredoc() {
        UnprovenScript refusal =
                ShellTestSupport.unproven(analysis, "cat <<EOF\nbody\nEOF\n");
        assertEquals("redirected_statement", refusal.nodeType());
    }

    @Test
    void refusesBraceExpansion() {
        assertEquals("brace_expression",
                ShellTestSupport.unproven(analysis, "echo {1..3}\n").nodeType());
    }

    @Test
    void refusesComments() {
        assertEquals("comment", ShellTestSupport.unproven(analysis, "# a comment\n").nodeType());
    }

    // ---- lexical rules, one assertion each ----------------------------------

    @Test
    void lexicallyRefusesGlobMetacharacters() {
        for (String argument : new String[]{"*.txt", "a?b", "a[0-9]", "a{b,c}", "a\\\\b", "~", "a^b",
                "a#b", "a$b"}) {
            UnprovenScript refusal =
                    ShellTestSupport.unproven(analysis, "echo " + argument + "\n");
            assertTrue(refusal.reason() == UnprovenScript.Reason.NON_LITERAL_LEXICAL_FORM
                            || refusal.reason() == UnprovenScript.Reason.DYNAMIC_CONSTRUCT,
                    "<<echo " + argument + ">> must not be provable, got " + refusal);
        }
    }

    @Test
    void lexicallyRefusesLeadingEquals() {
        assertEquals(UnprovenScript.Reason.NON_LITERAL_LEXICAL_FORM,
                ShellTestSupport.unproven(analysis, "echo =value\n").reason());
    }

    @Test
    void lexicallyRefusesQuoteAffectingEscapesInDoubleQuotes() {
        for (String argument : new String[]{"\"a\\$b\"", "\"a\\\"b\"", "\"a\\\\b\"", "\"a\\`b\""}) {
            assertEquals(UnprovenScript.Reason.NON_LITERAL_LEXICAL_FORM,
                    ShellTestSupport.unproven(analysis, "echo " + argument + "\n").reason(),
                    "<<" + argument + ">> must not be provable");
        }
    }

    @Test
    void acceptsDoubleQuotedEscapesThatDoubleQuotesPreserve() {
        // \t and \n survive double quotes, so the string's value is its source
        // text and the escape does not make it dynamic.
        assertEquals(List.of("\"a\\tb\""),
                ShellTestSupport.proven(analysis, "echo \"a\\tb\"\n").commands().get(0).arguments());
    }

    /**
     * The red line: a literal glued to an expansion is a single {@code
     * concatenation} node. Treating it as literal would make the command report no
     * dynamic construction at all.
     */
    @Test
    void refusesLiteralGluedToAnExpansion() {
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, "echo a\"$X\"b\n");
        assertEquals(UnprovenScript.Reason.NON_LITERAL_LEXICAL_FORM, refusal.reason());
        assertEquals("concatenation", refusal.nodeType());
    }

    @Test
    void aRefusalCarriesNoCommands() {
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, "if then rm -rf /data\n");
        assertEquals(UnprovenScript.Reason.SYNTAX_ERROR, refusal.reason());
        // There is deliberately no accessor that could yield a command list here;
        // only the reasons and the offset are reachable.
        assertEquals(1, refusal.reasons().size());
        assertTrue(refusal.byteOffset() >= 0);
    }

    @Test
    void aSyntaxErrorIsAlwaysARefusal() {
        // The mature reference implementation gates on "no error in the tree"; a
        // partially-understood tree is never promoted to proven.
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, "if then rm -rf /tmp/example\n");
        assertEquals(UnprovenScript.Reason.SYNTAX_ERROR, refusal.reason());
    }

    @Test
    void anEmptyScriptIsNotAProofOfNothing() {
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, "");
        assertEquals(UnprovenScript.Reason.UNSUPPORTED_LANGUAGE, refusal.reason());
    }

    @Test
    void malformedScriptIsARefusalRatherThanAProof() {
        // Malformed input must not be mistaken for a proven (and therefore
        // acceptable) script.
        assertFalse(analysis.splitProven("if then rm -rf /tmp/example").isProven());
    }

    private void assertRefused(String script, UnprovenScript.Reason expected) {
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, script);
        if (refusal.reason() != expected) {
            fail("<<" + script + ">> expected " + expected + " but got " + refusal);
        }
    }
}
