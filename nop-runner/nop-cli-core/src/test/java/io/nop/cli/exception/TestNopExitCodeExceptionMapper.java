package io.nop.cli.exception;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exit-code contract for {@link NopExitCodeExceptionMapper}.
 *
 * <p>Regression guard for the defect where picocli usage errors surfaced as exit 0,
 * making a malformed invocation indistinguishable from success to any calling script.
 */
public class TestNopExitCodeExceptionMapper extends JunitBaseTestCase {

    @Command(name = "probe", mixinStandardHelpOptions = true)
    static class ProbeCommand implements Callable<Integer> {
        @Option(names = {"--required"}, required = true)
        String required;

        @Override
        public Integer call() {
            return 0;
        }
    }

    private int execute(String... args) {
        CommandLine commandLine = new CommandLine(new ProbeCommand());
        commandLine.setExitCodeExceptionMapper(new NopExitCodeExceptionMapper());
        return commandLine.execute(args);
    }

    @Test
    void testUsageErrorReturnsNonZero() {
        assertNotEquals(0, execute(),
                "a missing required option must not report success");
    }

    @Test
    void testMissingRequiredOptionReturnsUsageCode() {
        assertEquals(CommandLine.ExitCode.USAGE, execute(),
                "missing --required must map to picocli's USAGE exit code");
    }

    @Test
    void testUnknownOptionReturnsUsageCode() {
        assertEquals(CommandLine.ExitCode.USAGE, execute("--required", "v", "--bogus"),
                "an unknown option must map to picocli's USAGE exit code");
    }

    @Test
    void testValidInvocationStillSucceeds() {
        assertEquals(0, execute("--required", "v"),
                "a valid invocation must still exit 0");
    }

    @Test
    void testMapperUnwrapsExecutionException() {
        NopExitCodeExceptionMapper mapper = new NopExitCodeExceptionMapper();
        CommandLine commandLine = new CommandLine(new ProbeCommand());
        CommandLine.ParameterException cause =
                new CommandLine.ParameterException(commandLine, "boom");
        assertEquals(CommandLine.ExitCode.USAGE,
                mapper.getExitCode(new CommandLine.ExecutionException(commandLine, "wrapped", cause)),
                "a wrapped usage error must still map to USAGE, not fall through");
    }
}