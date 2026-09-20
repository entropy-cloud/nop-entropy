package io.nop.treesitter.scanner;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: the scanner DSL compiler — deterministic output, load-time
 * validation via {@link ScannerProgram}, and fail-loud rejection of every
 * construct the DSL cannot express (unknown mnemonic, unresolved label,
 * undeclared token, token-count mismatch, malformed operand).
 */
class ScannerCompilerTest {

    private static final int[] SYMBOL_MAP = {129, 130, 131, 132, 78, 5, 6, 133};

    private static final String TOKENS = """
            token automatic_semicolon
            token template_chars
            token ternary_qmark
            token html_comment
            token logical_or
            token escape_sequence
            token regex_pattern
            token jsx_text
            """;

    @Test
    void deterministicCompilation() {
        String dsl = TOKENS + """
                start:
                    mark_end
                    jmp_if_eq 'x' not_x
                    emit template_chars
                not_x:
                    emit jsx_text
                """;
        byte[] first = ScannerCompiler.compile(dsl, SYMBOL_MAP);
        byte[] second = ScannerCompiler.compile(dsl, SYMBOL_MAP);
        assertArrayEquals(first, second, "same DSL text must compile byte-identically");
    }

    @Test
    void multipleJumpsToOneLabelAllResolve() {
        // Regression: a label reachable from several branch sites must be
        // patched at every site, not just the last one.
        String dsl = TOKENS + """
                entry:
                    jmp_if_eq 'x' common
                    jmp_if_eq 'y' common
                    jmp common
                common:
                    emit template_chars
                """;
        byte[] program = ScannerCompiler.compile(dsl, SYMBOL_MAP);
        ScannerProgram.validate(program);
        ScannerVM.Result r = ScannerVM.run(program, "x".getBytes(), 0, allValid());
        assertNotNull(r);
        assertEquals(SYMBOL_MAP[1], r.symbol());
    }

    @Test
    void unknownMnemonicFailsLoudly() {
        String dsl = TOKENS + "    frobnicate 1\n";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerCompiler.compile(dsl, SYMBOL_MAP));
        assertTrue(ex.getMessage().contains("frobnicate"), ex.getMessage());
    }

    @Test
    void unresolvedLabelFailsLoudly() {
        String dsl = TOKENS + "    jmp nowhere\n";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerCompiler.compile(dsl, SYMBOL_MAP));
        assertTrue(ex.getMessage().contains("nowhere"), ex.getMessage());
    }

    @Test
    void undeclaredTokenFailsLoudly() {
        String dsl = TOKENS + "    emit ghost\n";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerCompiler.compile(dsl, SYMBOL_MAP));
        assertTrue(ex.getMessage().contains("ghost"), ex.getMessage());
    }

    @Test
    void tokenCountMismatchFailsLoudly() {
        String dsl = "token a\n    fail\n";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerCompiler.compile(dsl, SYMBOL_MAP));
        assertTrue(ex.getMessage().contains("8"), ex.getMessage());
        assertTrue(ex.getMessage().contains("1"), ex.getMessage());
    }

    @Test
    void duplicateTokenDeclarationFailsLoudly() {
        String dsl = "token template_chars\ntoken template_chars\n"
                + "token ternary_qmark\ntoken html_comment\ntoken logical_or\n"
                + "token escape_sequence\ntoken regex_pattern\ntoken jsx_text\n    fail\n";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerCompiler.compile(dsl, SYMBOL_MAP));
        assertTrue(ex.getMessage().contains("duplicate"), ex.getMessage());
    }

    @Test
    void malformedIntegerOperandFailsLoudly() {
        String dsl = TOKENS + "    set_result banana\n";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerCompiler.compile(dsl, SYMBOL_MAP));
        assertTrue(ex.getMessage().contains("banana"), ex.getMessage());
        // the original NumberFormatException must stay reachable via the cause chain
        assertInstanceOf(NumberFormatException.class, ex.getCause());
    }

    @Test
    void missingOperandFailsLoudly() {
        String dsl = TOKENS + "    jmp_if_eq 1\n";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerCompiler.compile(dsl, SYMBOL_MAP));
        assertTrue(ex.getMessage().contains("operand"), ex.getMessage());
    }

    @Test
    void compiledProgramPassesLoadValidation() {
        String dsl = TOKENS + """
                entry:
                    push_bytes "ab"
                    span 2 fail_target
                    mark_end
                    emit template_chars
                fail_target:
                    emit jsx_text
                """;
        byte[] program = ScannerCompiler.compile(dsl, SYMBOL_MAP);
        ScannerProgram.validate(program);
        ScannerVM.Result r = ScannerVM.run(program, "ab".getBytes(), 0, allValid());
        assertNotNull(r);
        assertArrayEquals(new byte[]{'a', 'b'}, r.bytes("ab".getBytes()));
    }

    private static boolean[] allValid() {
        boolean[] v = new boolean[SYMBOL_MAP.length];
        Arrays.fill(v, true);
        return v;
    }
}