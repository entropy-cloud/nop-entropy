package io.nop.kernel.cli.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "nop-kernel-cli", description = "Nop command line tool", version = "nop-cli 2.0",
        mixinStandardHelpOptions = true,
        subcommands = {
                KernelCliGenCommand.class,
                KernelCliConvertCommand.class,
                KernelCliValidateCommand.class
        })
public class KernelMainCommand {
}
