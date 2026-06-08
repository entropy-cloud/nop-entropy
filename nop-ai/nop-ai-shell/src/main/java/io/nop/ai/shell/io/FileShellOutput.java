package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileShellOutput implements IShellOutput {

    private final Path filePath;
    private final boolean append;
    private BufferedWriter writer;
    private volatile boolean closed = false;

    public FileShellOutput(String filePath) {
        this(filePath, false);
    }

    public FileShellOutput(String filePath, boolean append) {
        this(Paths.get(filePath), append);
    }

    public FileShellOutput(Path filePath) {
        this(filePath, false);
    }

    public FileShellOutput(Path filePath, boolean append) {
        this.filePath = filePath;
        this.append = append;
        openWriter();
    }

    private void openWriter() {
        try {
            if (append) {
                writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void write(ShellChunk chunk) {
        if (closed) throw new IllegalStateException("output closed");
        if (chunk.isText()) {
            try {
                writer.write(chunk.asText());
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void flush() {
        try {
            if (writer != null) writer.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                if (writer != null) {
                    writer.flush();
                    writer.close();
                }
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public IShellInput asInput() {
        return new FileShellInput(filePath);
    }

    public Path getFilePath() {
        return filePath;
    }

    public boolean isAppend() {
        return append;
    }
}
