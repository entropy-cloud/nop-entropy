package io.nop.ai.shell.executor;

import java.nio.file.Path;
import java.util.List;

public class CommandData {
    private final String command;
    private final List<String> args;
    private final PipeType pipeType;
    private final Path redirectFile;
    private final boolean append;

    public CommandData(String command, List<String> args, PipeType pipeType, Path redirectFile, boolean append) {
        this.command = command;
        this.args = args;
        this.pipeType = pipeType;
        this.redirectFile = redirectFile;
        this.append = append;
    }

    public String command() {
        return command;
    }

    public List<String> args() {
        return args;
    }

    public PipeType pipeType() {
        return pipeType;
    }

    public Path redirectFile() {
        return redirectFile;
    }

    public boolean append() {
        return append;
    }
}
