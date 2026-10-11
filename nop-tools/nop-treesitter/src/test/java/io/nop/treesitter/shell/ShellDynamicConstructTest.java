package io.nop.treesitter.shell;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capability 4: dynamic construction points.
 *
 * <p>One assertion per enumerated kind, because the enumeration is a closed list
 * and a closed list with untested members is a list that will quietly lose one.
 * Two kinds get their own test: {@code concatenation} (whose omission makes a
 * command look fully literal) and the background operator (which has no named
 * node type, so a named-only walk misses it).</p>
 */
class ShellDynamicConstructTest {

    private final ShellAnalysis analysis = ShellTestSupport.analysis();

    private Set<DynamicConstruct.Kind> kindsOf(String script) {
        return analysis.findDynamicConstructs(script).stream()
                .map(DynamicConstruct::kind)
                .collect(Collectors.toSet());
    }

    private DynamicConstruct only(String script, DynamicConstruct.Kind kind) {
        List<DynamicConstruct> matching = analysis.findDynamicConstructs(script).stream()
                .filter(c -> c.kind() == kind)
                .toList();
        assertEquals(1, matching.size(),
                "expected exactly one " + kind + " in <<" + script + ">>, got "
                        + analysis.findDynamicConstructs(script));
        return matching.get(0);
    }

    @Test
    void commandSubstitutionDollarForm() {
        DynamicConstruct construct = only("echo $(date)\n", DynamicConstruct.Kind.COMMAND_SUBSTITUTION);
        assertEquals("$(date)", construct.text());
    }

    /** Backticks are the same node as {@code $( )}, and must be reported the same way. */
    @Test
    void commandSubstitutionBacktickFormIsTheSameKind() {
        DynamicConstruct construct = only("echo `date`\n", DynamicConstruct.Kind.COMMAND_SUBSTITUTION);
        assertEquals("`date`", construct.text());
    }

    @Test
    void simpleExpansion() {
        DynamicConstruct construct = only("echo $VAR\n", DynamicConstruct.Kind.SIMPLE_EXPANSION);
        assertEquals("$VAR", construct.text());
    }

    @Test
    void expansion() {
        assertEquals("${VAR}", only("echo ${VAR}\n", DynamicConstruct.Kind.EXPANSION).text());
    }

    @Test
    void arithmeticExpansion() {
        assertEquals("$((1 + 2))",
                only("echo $((1 + 2))\n", DynamicConstruct.Kind.ARITHMETIC_EXPANSION).text());
    }

    @Test
    void processSubstitution() {
        assertTrue(kindsOf("diff <(ls) <(ls)\n")
                .contains(DynamicConstruct.Kind.PROCESS_SUBSTITUTION));
    }

    /**
     * The concatenation case. {@code a"$X"b} is one {@code concatenation} node;
     * without this finding the command reports no dynamic construction at all and
     * reads as fully literal — a safety hole, not an incompleteness.
     */
    @Test
    void literalGluedToAnExpansionIsMarked() {
        DynamicConstruct construct = only("echo a\"$X\"b\n", DynamicConstruct.Kind.CONCATENATION);
        assertEquals("a\"$X\"b", construct.text());
    }

    @Test
    void aConcatenationOfTwoLiteralsIsNotMarked() {
        // a"b" is a word written in two pieces. Reporting it would dilute the
        // finding list until real findings get ignored.
        // Both quoting containers hold only literal parts.
        assertFalse(kindsOf("echo a\"b\"\n").contains(DynamicConstruct.Kind.CONCATENATION));
        assertFalse(kindsOf("echo a'b'\n").contains(DynamicConstruct.Kind.CONCATENATION));
    }

    @Test
    void variableAssignment() {
        assertTrue(kindsOf("FOO=bar\n").contains(DynamicConstruct.Kind.VARIABLE_ASSIGNMENT));
        assertTrue(kindsOf("FOO=bar ls\n").contains(DynamicConstruct.Kind.VARIABLE_ASSIGNMENT));
    }

    @Test
    void redirects() {
        Set<DynamicConstruct.Kind> kinds = kindsOf("echo hi > out.txt\n");
        assertTrue(kinds.contains(DynamicConstruct.Kind.REDIRECTED_STATEMENT));
        assertTrue(kinds.contains(DynamicConstruct.Kind.FILE_REDIRECT));
    }

    @Test
    void heredocNodes() {
        Set<DynamicConstruct.Kind> kinds = kindsOf("cat <<EOF\nbody\nEOF\n");
        assertTrue(kinds.contains(DynamicConstruct.Kind.HEREDOC_REDIRECT));
        assertTrue(kinds.contains(DynamicConstruct.Kind.HEREDOC_START));
        assertTrue(kinds.contains(DynamicConstruct.Kind.HEREDOC_BODY));
        assertTrue(kinds.contains(DynamicConstruct.Kind.HEREDOC_END));
    }

    @Test
    void herestringRedirect() {
        assertTrue(kindsOf("cat <<<word\n")
                .contains(DynamicConstruct.Kind.HERESTRING_REDIRECT));
    }

    /**
     * The background operator has no <b>named</b> node type in the bash grammar —
     * it is an anonymous token. A walk over named nodes only would report nothing
     * and the script would read as if nothing were left running.
     */
    @Test
    void backgroundOperatorIsMarked() {
        DynamicConstruct construct = only("echo hi &\n", DynamicConstruct.Kind.BACKGROUND_OPERATOR);
        assertEquals("&", construct.text());
        assertEquals("echo hi &\n".indexOf('&'), construct.startByte());
        assertEquals(construct.startByte() + 1, construct.endByte());
    }

    @Test
    void logicalAndIsNotABackgroundOperator() {
        assertFalse(kindsOf("echo a && echo b\n")
                .contains(DynamicConstruct.Kind.BACKGROUND_OPERATOR));
    }

    @Test
    void findingsCarryByteRanges() {
        String script = "echo $A ${B} $((1))\n";
        for (DynamicConstruct construct : analysis.findDynamicConstructs(script)) {
            assertTrue(construct.startByte() >= 0);
            assertTrue(construct.endByte() > construct.startByte());
            assertTrue(construct.endByte() <= script.length());
            assertEquals(script.substring(construct.startByte(), construct.endByte()),
                    construct.text(), "the range must select exactly the reported text");
        }
    }

    @Test
    void findingsAreSortedByPosition() {
        List<DynamicConstruct> found = analysis.findDynamicConstructs(
                "a=1\necho $A ${B} $((1)) `c`\n");
        for (int i = 1; i < found.size(); i++) {
            assertTrue(found.get(i - 1).startByte() <= found.get(i).startByte(),
                    "findings must be in source order: " + found);
        }
    }

    /** Nothing is evaluated: the text of a substitution is its source, not its result. */
    @Test
    void nothingIsEvaluated() {
        DynamicConstruct construct = only("echo $(rm -rf /)\n", DynamicConstruct.Kind.COMMAND_SUBSTITUTION);
        assertEquals("$(rm -rf /)", construct.text());
    }

    @Test
    void aFullyLiteralScriptHasNoFindings() {
        assertTrue(analysis.findDynamicConstructs("cat /etc/passwd\n").isEmpty());
        assertTrue(analysis.findDynamicConstructs("echo \"a b\"\n").isEmpty());
        assertTrue(analysis.findDynamicConstructs("ls -la | grep x\n").isEmpty());
    }

    @Test
    void anEmptyScriptHasNoFindings() {
        assertTrue(analysis.findDynamicConstructs("").isEmpty());
    }
}
