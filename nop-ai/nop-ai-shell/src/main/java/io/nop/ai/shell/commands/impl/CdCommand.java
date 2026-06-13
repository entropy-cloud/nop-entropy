package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.AbstractShellCommand;
import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;

public class CdCommand extends AbstractShellCommand {

    @Override
    public String name() {
        return "cd";
    }

    @Override
    public String description() {
        return "Change current working directory";
    }

    @Override
    public String usage() {
        return "cd [DIRECTORY]";
    }

    @Override
    public int execute(IShellCommandExecutionContext context) throws Exception {
        String[] positionalArgs = context.positionalArguments();

        if (positionalArgs.length == 0) {
            if (context instanceof DefaultShellExecutionContext) {
                ((DefaultShellExecutionContext) context).setWorkingDirectory("/");
            }
            return 0;
        }

        if (positionalArgs.length > 1) {
            context.stderr().println("cd: too many arguments");
            return 1;
        }

        String targetDir = positionalArgs[0];

        if (targetDir == null || targetDir.trim().isEmpty()) {
            targetDir = "/";
        }

        String currentDir = context.workingDirectory();
        String resolvedDir = resolvePath(currentDir, targetDir, context.fileSystem().normalizePath("/"));

        if (resolvedDir == null || !resolvedDir.startsWith("/")) {
            context.stderr().println("cd: " + targetDir + ": No such file or directory");
            return 1;
        }

        if (context instanceof DefaultShellExecutionContext) {
            ((DefaultShellExecutionContext) context).setWorkingDirectory(resolvedDir);
        }

        return 0;
    }

    static String resolvePath(String currentDir, String targetPath, String normalizedRoot) {
        if (targetPath.startsWith("/")) {
            return normalize(targetPath);
        }

        String[] currentParts = currentDir.split("/");
        String[] targetParts = targetPath.split("/");

        java.util.List<String> resultParts = new java.util.ArrayList<>();

        for (String part : currentParts) {
            if (!part.isEmpty()) {
                resultParts.add(part);
            }
        }

        for (String part : targetParts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            } else if (part.equals("..")) {
                if (!resultParts.isEmpty()) {
                    resultParts.remove(resultParts.size() - 1);
                }
            } else {
                resultParts.add(part);
            }
        }

        StringBuilder result = new StringBuilder("/");
        for (int i = 0; i < resultParts.size(); i++) {
            if (i > 0) {
                result.append("/");
            }
            result.append(resultParts.get(i));
        }

        return normalize(result.toString());
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String result = path;
        while (result.contains("//")) {
            result = result.replace("//", "/");
        }
        return result.isEmpty() ? "/" : result;
    }
}
