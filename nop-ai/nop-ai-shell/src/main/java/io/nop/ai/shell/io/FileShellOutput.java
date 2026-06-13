package io.nop.ai.shell.io;

import io.nop.ai.toolkit.fs.IToolFileSystem;

public class FileShellOutput implements IShellOutput {

    private final String filePath;
    private final IToolFileSystem fileSystem;
    private final boolean append;
    private final StringBuilder buffer = new StringBuilder();
    private volatile boolean closed = false;

    public FileShellOutput(String filePath, IToolFileSystem fileSystem) {
        this(filePath, fileSystem, false);
    }

    public FileShellOutput(String filePath, IToolFileSystem fileSystem, boolean append) {
        this.filePath = filePath;
        this.fileSystem = fileSystem;
        this.append = append;
    }

    @Override
    public void write(ShellChunk chunk) {
        if (closed) throw new IllegalStateException("output closed");
        if (chunk.isText()) {
            buffer.append(chunk.asText());
        }
    }

    @Override
    public void flush() {
        if (buffer.length() > 0) {
            String content = buffer.toString();
            buffer.setLength(0);
            fileSystem.writeText(filePath, content, append);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            flush();
        }
    }

    @Override
    public IShellInput asInput() {
        return new FileShellInput(filePath, fileSystem);
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isAppend() {
        return append;
    }
}
