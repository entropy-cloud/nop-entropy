package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.AbstractShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.toolkit.fs.FileInfo;

import java.util.List;

public class LsCommand extends AbstractShellCommand {

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String description() {
        return "List directory contents";
    }

    @Override
    public String usage() {
        return "ls [OPTIONS] [DIRECTORY]...";
    }

    @Override
    public String getHelp() {
        return "ls [OPTIONS] [DIRECTORY]...\n" +
                "List directory contents\n\n" +
                "Options:\n" +
                "    -l    Use long listing format\n";
    }

    @Override
    public int execute(IShellCommandExecutionContext context) throws Exception {
        boolean longFormat = context.hasFlag("l");
        String[] args = context.positionalArguments();

        String dirPath = args.length > 0 ? args[0] : context.workingDirectory();

        if (!context.fileSystem().isDirectory(dirPath)) {
            context.stderr().println("ls: cannot access '" + dirPath + "': No such directory");
            return 1;
        }

        List<FileInfo> entries = context.fileSystem().listDirectory(dirPath, 1, 1000);

        StringBuilder output = new StringBuilder();
        for (FileInfo entry : entries) {
            String name = entry.getName();
            if (entry.isDirectory()) {
                name += "/";
            }

            if (longFormat) {
                String type = entry.isDirectory() ? "d" : "-";
                String perms = "rwxr-xr-x";
                long size = entry.getSize();
                output.append(String.format("%s%s  1 user  group %6d Jan 1 00:00 %s\n",
                        type, perms, size, name));
            } else {
                output.append(name).append(" ");
            }
        }

        if (!longFormat && output.length() > 0) {
            output.setLength(output.length() - 1);
        }

        context.stdout().println(output.toString().trim());
        return 0;
    }
}
