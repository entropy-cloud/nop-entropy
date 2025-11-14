package io.nop.kernel.cli;

import io.nop.core.initialize.CoreInitialization;
import io.nop.kernel.cli.commands.KernelMainCommand;
import picocli.CommandLine;

public class KernelCliApplication {
    public static int run(String[] args) {
        int exitCode = new CommandLine(new KernelMainCommand()).execute(args);
        return exitCode;
    }

    public static void main(String[] args) {
        CoreInitialization.initialize();
        int exitCode = run(args);
        CoreInitialization.destroy();
        System.exit(exitCode);
    }
}