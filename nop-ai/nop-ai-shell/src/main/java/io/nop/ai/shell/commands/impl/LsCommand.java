package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.AbstractShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;

public class LsCommand extends AbstractShellCommand {

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String description() {
        return "List directory contents (mock implementation)";
    }

    @Override
    public String usage() {
        return "ls [OPTIONS] [DIRECTORY]...";
    }

    @Override
    public String getHelp() {
        return "ls [OPTIONS] [DIRECTORY]...\n" +
                "List directory contents (mock implementation)\n\n" +
                "Options:\n" +
                "    -l    Use long listing format\n";
    }

    @Override
    public int execute(IShellCommandExecutionContext context) throws Exception {
        boolean longFormat = context.hasFlag("l");
        String[] args = context.positionalArguments();

        StringBuilder output = new StringBuilder();

        if (longFormat) {
            output.append("mock: -rw-r--r--  1 user  group 1234 Jan 1 00:00 file1.txt\n");
            output.append("mock: -rw-r--r--  1 user  group  5678 Jan 1 00:00 file2.txt\n");
            output.append("mock: drwxr-xr-x  2 user  group  4096 Jan 1 00:00 directory/\n");
        } else {
            output.append("mock: file1.txt file2.txt directory/\n");
        }

        context.stdout().println(output.toString().trim());
        return 0;
    }
}
