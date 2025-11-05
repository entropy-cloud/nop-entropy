package io.nop.cli.jdk11;

import io.nop.boot.NopApplication;
import io.nop.cli.commands.MainCommand;
import picocli.CommandLine;

public class NopCliMainForJdk11 {
    public static void main(String[] args) {
        new NopApplication().run(args);

        int exitCode = new CommandLine(new MainCommand()).execute(args);
        System.exit(exitCode);
    }
}
