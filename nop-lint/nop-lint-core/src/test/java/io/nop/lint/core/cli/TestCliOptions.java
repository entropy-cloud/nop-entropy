package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LintProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CliOptions} parsing matrix (Minimum Rules #25): the v1 surface is
 * exactly {@code check <path>... [--profile fast|standard]}; every other
 * input — unknown subcommand, unknown option (including deferred ones like
 * {@code --max-warnings}), missing values, blank targets — fails closed.
 */
public class TestCliOptions {

    @Test
    public void minimalParseDefaultsToStandardProfile() {
        CliOptions options = CliOptions.parse("check", "src/main/java");

        assertEquals(List.of("src/main/java"), options.targets());
        assertEquals(LintProfile.STANDARD, options.profile());
    }

    @Test
    public void multipleTargetsAndExplicitProfileParse() {
        CliOptions options = CliOptions.parse("check", "a.java", "--profile", "fast", "dir/b.java");

        assertEquals(List.of("a.java", "dir/b.java"), options.targets());
        assertEquals(LintProfile.FAST, options.profile());
    }

    @Test
    public void deepProfileParses() {
        CliOptions options = CliOptions.parse("check", "x", "--profile", "deep");

        assertEquals(LintProfile.DEEP, options.profile());
    }

    @Test
    public void profileValuePositionIndependentOfTargets() {
        CliOptions options = CliOptions.parse("check", "--profile", "standard", "x");

        assertEquals(List.of("x"), options.targets());
        assertEquals(LintProfile.STANDARD, options.profile());
    }

    @Test
    public void emptyArgsFailClosed() {
        NopLintException ex = assertThrows(NopLintException.class, CliOptions::parse);
        assertTrue(ex.getMessage().contains("usage:"), ex.getMessage());
        assertTrue(ex.getMessage().contains("missing subcommand"), ex.getMessage());
    }

    @Test
    public void unknownSubcommandFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CliOptions.parse("lint", "x"));
        assertTrue(ex.getMessage().contains("unknown subcommand 'lint'"), ex.getMessage());
    }

    @Test
    public void missingTargetPathFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class, () -> CliOptions.parse("check"));
        assertTrue(ex.getMessage().contains("missing target path"), ex.getMessage());
    }

    @Test
    public void missingProfileValueFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "x", "--profile"));
        assertTrue(ex.getMessage().contains("--profile requires a value"), ex.getMessage());
    }

    @Test
    public void unknownProfileNameFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "--profile", "turbo", "x"));
        assertTrue(ex.getMessage().contains("unknown profile 'turbo'"), ex.getMessage());
    }

    @Test
    public void unknownOptionFailsClosed() {
        // R1 3.1 behavior migration (roadmap item 39): --max-warnings and
        // --rules were the v1 unknown-option fixtures and are now supported
        // flags with their own fail-closed value parsing (the completion
        // tests cover the positive faces)
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "--bogus", "x"));
        assertTrue(ex.getMessage().contains("unknown option '--bogus'"), ex.getMessage());
    }

    @Test
    public void blankTargetFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "  "));
        assertTrue(ex.getMessage().contains("must not be blank"), ex.getMessage());
    }
}
