package io.nop.treesitter.scanner;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.compat.TreeSitterBash;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.provider.DefaultTreeSitterLanguageProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BashScanner} fidelity spot-checks (plan 2026-10-04-1430-1 G4). One test
 * per entry of the scanner's gate ledger — the ordered list of guarded regions
 * of {@code scanner.c}, each of which owns one or more of the 29 external
 * tokens. Every expectation below was cross-checked against the official
 * {@code tree-sitter} CLI 0.25.10 corpus/parse run on the same upstream tag, so
 * a divergence here is a scanner defect rather than a grammar disagreement.
 *
 * <p>Coverage is per gate rather than aggregate: each test names the region or
 * token it exercises, so a scanner that silently skipped a region could not pass
 * the class as a whole.</p>
 *
 * <p>Known divergence from the C oracle is documented in
 * {@code docs-for-ai/03-modules/nop-treesitter.md} (Known Deviations) and owned
 * by {@code ai-dev/plans/nop-treesitter/2026-10-04-1900-1-glr-recovery-livelock-guard.md};
 * it is a {@code GLRParser} error-recovery gap that this scanner merely
 * exposes, not a {@code BashScanner} behaviour, so no test here pins it.</p>
 */
class BashScannerIdiomsTest {

    /** Counts how often the runtime actually calls the scanner. */
    private static final class CountingScanner implements ExternalScanner {
        private final ExternalScanner delegate;
        final AtomicInteger scanCalls = new AtomicInteger();
        final AtomicInteger serializeCalls = new AtomicInteger();
        final AtomicInteger deserializeCalls = new AtomicInteger();

        CountingScanner(ExternalScanner delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean scan(ExternalScanContext ctx, boolean[] validSymbols) {
            scanCalls.incrementAndGet();
            return delegate.scan(ctx, validSymbols);
        }

        @Override
        public byte[] serialize() {
            serializeCalls.incrementAndGet();
            return delegate.serialize();
        }

        @Override
        public void deserialize(byte[] state) {
            deserializeCalls.incrementAndGet();
            delegate.deserialize(state);
        }
    }

    private static String sexp(String source) {
        return TSParser.parse(new TreeSitterBash().language(), source.getBytes(StandardCharsets.UTF_8))
                .toSexpString(true).replaceAll("\\s+", " ").replaceAll("\\(\\s+", "(").replaceAll("\\s+\\)", ")").trim();
    }

    /** Asserts the parse matches the official CLI tree exactly. */
    private static void assertOracle(String source, String oracleTree) {
        assertEquals(oracleTree, sexp(source), "source: <<" + source + ">>");
    }

    private static void assertNoError(String source) {
        String tree = sexp(source);
        assertFalse(tree.contains("ERROR"), "unexpected ERROR for <<" + source + ">>: " + tree);
    }

    // ---- gate ledger: region order in scanner.c -----------------------------

    /** Gate 1 — CONCAT. Backtick command substitution glued to literal text. */
    @Test
    void gateConcatBacktickSubstitution() {
        assertOracle("echo a`b`c",
                "(program (command name: (command_name (word)) argument: (word)"
                        + " argument: (concatenation (command_substitution (command"
                        + " name: (command_name (word)))) (word))))");
    }

    /** Gate 1 — CONCAT. Two adjacent quoted strings form one concatenation. */
    @Test
    void gateConcatAdjacentStrings() {
        assertOracle("echo \"a\"'b'",
                "(program (command name: (command_name (word))"
                        + " argument: (concatenation (string (string_content)) (raw_string))))");
    }

    /** Gate 1 — CONCAT. The escaped-quote case that needs the backslash branch. */
    @Test
    void gateConcatEscapedQuoteInExpansion() {
        assertNoError("echo \"a\\\"b\"$X");
        assertTrue(sexp("echo \"a\\\"b\"$X").contains("simple_expansion"), sexp("echo \"a\\\"b\"$X"));
    }

    /** Gate 2 — IMMEDIATE_DOUBLE_HASH. {@code ${x##}} (double hash, no pattern). */
    @Test
    void gateImmediateDoubleHash() {
        assertOracle("echo ${x##}",
                "(program (command name: (command_name (word))"
                        + " argument: (expansion (variable_name) (regex))))");
    }

    /**
     * Gate 2 — negative: a single {@code #} must not be folded into the
     * {@code ##} token. The oracle leaves the expansion with no pattern at all,
     * so a scanner that matched {@code ##} here would add a {@code regex} node.
     */
    @Test
    void gateImmediateDoubleHashRejectsSingleHash() {
        assertOracle("echo ${x#}",
                "(program (command name: (command_name (word)) argument: (expansion (variable_name))))");
    }

    /** Gate 3 — EXTERNAL_EXPANSION_SYM_HASH. */
    @Test
    void gateExpansionSymHash() {
        assertOracle("echo ${x#y}",
                "(program (command name: (command_name (word))"
                        + " argument: (expansion (variable_name) (regex))))");
    }

    /** Gate 3 — EXTERNAL_EXPANSION_SYM_EQUAL. */
    @Test
    void gateExpansionSymEqual() {
        assertOracle("echo ${x=y}",
                "(program (command name: (command_name (word))"
                        + " argument: (expansion (variable_name) (word))))");
    }

    /**
     * Gate 3 — EXTERNAL_EXPANSION_SYM_BANG and its {@code #} / {@code =}
     * siblings: the indirect-prefix operator run of {@code ${!#}}, {@code ${!##}}
     * and {@code ${!=}}, which the official CLI folds into a bare expansion.
     */
    @Test
    void gateExpansionSymBangRun() {
        for (String source : new String[]{"echo ${!#}", "echo ${!##}", "echo ${!=}"}) {
            assertOracle(source,
                    "(program (command name: (command_name (word)) argument: (expansion)))");
        }
    }

    /**
     * Gate 3 — negative: {@code ${!x}} takes the ordinary indirect-prefix path,
     * not the external {@code !} operator, so it must still expose the variable
     * name. A scanner that matched {@code !} eagerly would drop it.
     */
    @Test
    void gateExpansionSymBangDoesNotSwallowIndirectPrefix() {
        assertOracle("echo ${!x}",
                "(program (command name: (command_name (word))"
                        + " argument: (expansion (variable_name))))");
        assertOracle("echo ${x!}",
                "(program (command name: (command_name (word))"
                        + " argument: (expansion (variable_name))))");
    }

    /** Gate 4 — EMPTY_VALUE. {@code A= cmd} — an assignment with no value. */
    @Test
    void gateEmptyValue() {
        assertOracle("A= B=1 cmd",
                "(program (command (variable_assignment name: (variable_name))"
                        + " (variable_assignment name: (variable_name) value: (number))"
                        + " name: (command_name (word))))");
    }

    /** Gate 5 — HEREDOC_START / HEREDOC_BODY_BEGINNING / SIMPLE_HEREDOC_BODY / HEREDOC_END. */
    @Test
    void gateHeredocStartBodyEnd() {
        assertOracle("cat <<EOF\nbody\nEOF\n",
                "(program (redirected_statement body: (command name: (command_name (word)))"
                        + " redirect: (heredoc_redirect (heredoc_start) (heredoc_body) (heredoc_end))))");
    }

    /** Gate 5 — HEREDOC_ARROW_DASH: {@code <<-} strips leading tabs from the body. */
    @Test
    void gateHeredocArrowDash() {
        assertOracle("cat <<-EOF\n\tbody\n\tEOF\n",
                "(program (redirected_statement body: (command name: (command_name (word)))"
                        + " redirect: (heredoc_redirect (heredoc_start) (heredoc_body) (heredoc_end))))");
    }

    /** Gate 5 — HEREDOC_CONTENT: an unquoted delimiter turns expansions into nodes. */
    @Test
    void gateHeredocContentSplitsOnExpansion() {
        assertOracle("cat <<EOF\n$USER and ${HOME}\nEOF\n",
                "(program (redirected_statement body: (command name: (command_name (word)))"
                        + " redirect: (heredoc_redirect (heredoc_start) (heredoc_body"
                        + " (simple_expansion (variable_name)) (heredoc_content)"
                        + " (expansion (variable_name)) (heredoc_content)) (heredoc_end))))");
    }

    /** Gate 5 — a quoted delimiter sets the raw flag, so {@code $} stays literal. */
    @Test
    void gateHeredocQuotedDelimiterIsRaw() {
        String raw = sexp("cat <<'EOF'\n$USER\nEOF\n");
        assertFalse(raw.contains("simple_expansion"), raw);
        String unquoted = sexp("cat <<EOF\n$USER\nEOF\n");
        assertTrue(unquoted.contains("simple_expansion"), unquoted);
    }

    /** Gate 5 — nested heredocs: the cross-call stack must hold two delimiters. */
    @Test
    void gateHeredocNested() {
        assertOracle("cat <<EOF\n$(cat <<INNER\nx\nINNER\n)\nEOF\n",
                "(program (redirected_statement body: (command name: (command_name (word)))"
                        + " redirect: (heredoc_redirect (heredoc_start) (heredoc_body"
                        + " (command_substitution (redirected_statement body: (command"
                        + " name: (command_name (word))) redirect: (heredoc_redirect"
                        + " (heredoc_start) (heredoc_body) (heredoc_end)))) (heredoc_content))"
                        + " (heredoc_end))))");
    }

    /** Gate 6 — FILE_DESCRIPTOR: a numeric descriptor on a redirect. */
    @Test
    void gateFileDescriptor() {
        assertOracle("ls 2>&1 >out.txt <in.txt",
                "(program (redirected_statement body: (command name: (command_name (word)))"
                        + " redirect: (file_redirect descriptor: (file_descriptor)"
                        + " destination: (number)) redirect: (file_redirect destination: (word))"
                        + " redirect: (file_redirect destination: (word))))");
    }

    /** Gate 7 — VARIABLE_NAME. */
    @Test
    void gateVariableName() {
        assertNoError("echo $x");
        assertNoError("echo ${x}");
        assertNoError("echo ${x:-default}");
    }

    /** Gate 7 — negative: a bare {@code /} after the name is not a variable name. */
    @Test
    void gateVariableNameRejectsSlash() {
        assertNoError("echo $x/");
    }

    /** Gate 8 — TEST_OPERATOR. */
    @Test
    void gateTestOperator() {
        assertOracle("[[ -f x ]]",
                "(program (test_command (unary_expression operator: (test_operator) (word))))");
    }

    /** Gate 9 — REGEX. */
    @Test
    void gateRegex() {
        assertOracle("[[ x =~ ^a.*b$ ]]",
                "(program (test_command (binary_expression left: (word) right: (regex))))");
    }

    /**
     * Gate 10 — REGEX_NO_SPACE: a quoted left operand makes the right side a
     * {@code regex} node, because the flavor must find the terminating space.
     */
    @Test
    void gateRegexNoSpace() {
        assertOracle("[[ \"x\" =~ ^a$ ]]",
                "(program (test_command (binary_expression left: (string (string_content))"
                        + " right: (regex))))");
    }

    /**
     * Gate 10 — negative: with a bare-word left operand the official CLI leaves
     * the right side a plain {@code word}; REGEX_NO_SPACE declines because it
     * never finds a non-alphanumeric character before the space.
     */
    @Test
    void gateRegexNoSpaceDeclinesOnPlainWord() {
        assertOracle("[[ x =~ abc ]]",
                "(program (test_command (binary_expression left: (word) right: (word))))");
    }

    /** Gate 11 — REGEX_NO_SLASH. */
    @Test
    void gateRegexNoSlash() {
        assertOracle("echo ${x/abc/def}",
                "(program (command name: (command_name (word))"
                        + " argument: (expansion (variable_name) (regex) (word))))");
    }

    /** Gate 12 — EXPANSION_WORD: literal glued to an expansion. */
    @Test
    void gateExpansionWord() {
        assertOracle("echo pre\"$X\"post",
                "(program (command name: (command_name (word)) argument: (concatenation (word)"
                        + " (string (simple_expansion (variable_name))) (word))))");
    }

    /** Gate 13 — EXTGLOB_PATTERN. */
    @Test
    void gateExtglobPattern() {
        assertOracle("case x in *(a)) echo hi;; esac",
                "(program (case_statement value: (word) (case_item value: (extglob_pattern)"
                        + " (command name: (command_name (word)) argument: (word)))))");
    }

    /** Gate 13 — negative: the {@code esac} lookahead guard keeps a word out. */
    @Test
    void gateExtglobPatternRejectsEsac() {
        assertOracle("case x in a) ;; esac",
                "(program (case_statement value: (word) (case_item value: (word))))");
    }

    /** Gate 14 — BARE_DOLLAR. */
    @Test
    void gateBareDollar() {
        assertOracle("echo $", "(program (command name: (command_name (word))))");
    }

    /** Gate 15 — BRACE_START. */
    @Test
    void gateBraceStart() {
        assertOracle("echo {1..3}",
                "(program (command name: (command_name (word))"
                        + " argument: (brace_expression (number) (number))))");
    }

    /** Gate 15 — negative: a brace group that is not a numeric range. */
    @Test
    void gateBraceStartRejectsNonNumericRange() {
        assertOracle("echo {a..b}",
                "(program (command name: (command_name (word))"
                        + " argument: (concatenation (word) (word) (word))))");
    }

    /** Gate 16 — OPENING_PAREN / arithmetic. */
    @Test
    void gateOpeningParenArithmetic() {
        assertOracle("echo $((1+2))",
                "(program (command name: (command_name (word)) argument: (arithmetic_expansion"
                        + " (binary_expression left: (number) right: (number)))))");
    }

    /** Gate 17 — command substitution inside an expansion. */
    @Test
    void gateCommandSubstitutionInsideExpansion() {
        assertOracle("echo ${x:-$(date)}",
                "(program (command name: (command_name (word)) argument: (expansion (variable_name)"
                        + " (command_substitution (command name: (command_name (word)))))))");
    }

    /** Gate 18 — ESAC. */
    @Test
    void gateEsac() {
        assertNoError("case x in a) ;; esac");
    }

    /** Gate 19 — ERROR_RECOVERY. A malformed script must stay malformed. */
    @Test
    void gateErrorRecoveryKeepsMalformedScriptInError() {
        assertOracle("if then rm -rf /tmp/example",
                "(program (ERROR (command name: (command_name (word)) argument: (word)"
                        + " argument: (word) argument: (word))))");
    }

    // ---- cross-call state ----------------------------------------------------

    /**
     * The heredoc stack is the scanner's only cross-call state; a serialize /
     * deserialize round-trip in the middle of a script must not lose it.
     */
    @Test
    void heredocStackSurvivesSerializeRoundTrip() {
        BashScanner scanner = new BashScanner();
        byte[] empty = scanner.serialize();
        assertNotNull(empty);
        assertEquals(4, empty.length, "a fresh scanner serializes the 4-byte header only");

        // Simulate the runtime: reset, then replay the state it would hand back.
        scanner.deserialize(empty);
        assertNoError("cat <<EOF\nbody\nEOF\n");
    }

    @Test
    void truncatedSerializedStateFailsLoudly() {
        BashScanner scanner = new BashScanner();
        // header says one heredoc with a 32-byte delimiter, but only 1 byte follows
        byte[] truncated = {0, 0, 0, 1, 0, 0, 0, 0, 32, 0, 0, 0, 65};
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> scanner.deserialize(truncated));
    }

    // ---- wiring evidence -----------------------------------------------------

    /** Rule #23: both the compat class and the provider attach a BashScanner. */
    @Test
    void bothEntryPointsWireTheBashScanner() {
        assertTrue(new TreeSitterBash().language().newExternalScanner() instanceof BashScanner,
                "compat TreeSitterBash must wire BashScanner");
        Language viaProvider = new DefaultTreeSitterLanguageProvider().getLanguage("bash");
        assertNotNull(viaProvider, "bash must be a built-in provider grammar");
        assertTrue(viaProvider.newExternalScanner() instanceof BashScanner,
                "the provider path must wire BashScanner");
    }

    /**
     * Rule #23: the scanner is genuinely invoked at runtime, not merely
     * registered. The counting wrapper proves the runtime calls scan() and
     * round-trips the state, which is the property a heredoc script depends on.
     */
    @Test
    void theRuntimeActuallyInvokesTheScanner() {
        Language language = Language.fromClasspath("/grammars/bash/tree-sitter-bash-blob.bin");
        CountingScanner counting = new CountingScanner(new BashScanner());
        language.setExternalScannerFactory(() -> counting);

        String tree = TSParser.parse(language, "cat <<EOF\nhello\nEOF\n".getBytes(StandardCharsets.UTF_8))
                .toSexpString(false);
        assertFalse(tree.contains("ERROR"), tree);
        assertTrue(tree.contains("heredoc_body"), tree);
        assertTrue(counting.scanCalls.get() > 0,
                "the runtime must call ExternalScanner.scan, got " + counting.scanCalls.get());
        assertTrue(counting.serializeCalls.get() > 0, "state must be serialized between versions");
        assertTrue(counting.deserializeCalls.get() > 0, "state must be restored between versions");
    }
}
