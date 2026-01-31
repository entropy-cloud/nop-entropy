package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.AbstractShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;

public class EchoCommand extends AbstractShellCommand {

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "Display a line of text";
    }

    @Override
    public String usage() {
        return "echo [OPTIONS] [TEXT]...";
    }

    @Override
    public String getHelp() {
        return "echo [OPTIONS] [TEXT]...\n" +
               "Display a line of text\n\n" +
               "Options:\n" +
               "    -n    Do not output the trailing newline";
    }

    @Override
    public int execute(IShellCommandExecutionContext context) throws Exception {
        boolean noNewline = context.hasFlag("n");
        String[] args = context.positionalArguments();

        if (args.length == 0) {
            if (!noNewline) {
                context.stdout().println();
            }
            return 0;
        }

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                output.append(" ");
            }
            output.append(args[i]);
        }

        String result = output.toString();
        if (noNewline) {
            context.stdout().print(result);
            context.stdout().flush();
        } else {
            context.stdout().println(result);
        }

        return 0;
    }
}
