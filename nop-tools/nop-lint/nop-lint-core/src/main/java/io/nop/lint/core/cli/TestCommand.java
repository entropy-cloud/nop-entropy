package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.testing.RuleTestRunner;
import io.nop.lint.core.testing.SuiteResult;

import java.io.PrintStream;
import java.util.List;

/**
 * The {@code nop-lint test} subcommand (roadmap item 39, design 03 §2.4,
 * plan Decision 3): runs the RuleTester suites under one path through the
 * real {@link RuleTestRunner} (the default-profile constructor — deep
 * suites run by pointing the path argument at them with the provider
 * wiring they need, which stays the test-harness face). Green exits 0, any
 * red suite exits 1, a suite-loading failure exits 2 — the CLI's three
 * faces, unchanged.
 */
public final class TestCommand {

    private TestCommand() {
    }

    /**
     * Runs the suites under {@code args} (everything after the subcommand
     * token: exactly one suites path) and returns the exit code.
     */
    public static int run(String[] args, LanguageRegistry registry, PrintStream out,
                          PrintStream err) {
        List<String> positional = new java.util.ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                throw new NopLintException("usage: nop-lint test <suites-path>"
                        + " (unknown option '" + arg + "')");
            }
            if (!arg.isBlank()) {
                positional.add(arg);
            }
        }
        if (positional.size() != 1) {
            throw new NopLintException("usage: nop-lint test <suites-path>"
                    + " (exactly one suites path is required)");
        }

        RuleTestRunner runner = new RuleTestRunner();
        List<SuiteResult> results = runner.runSuites(positional.get(0));
        int failures = 0;
        for (SuiteResult result : results) {
            if (result.isGreen()) {
                out.println(result.suitePath() + ": green");
            } else {
                failures++;
                out.println(result.suitePath() + ": RED");
                err.print(result.renderFailures());
                err.println();
            }
        }
        out.println(results.size() + " suite(s), " + failures + " red");
        return failures == 0 ? NopLintCli.EXIT_OK : NopLintCli.EXIT_VIOLATIONS;
    }
}
