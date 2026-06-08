package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileShellInput extends AbstractShellInput {

    private final Path filePath;
    private final String content;
    private int position = 0;

    public FileShellInput(String filePath) {
        this(Path.of(filePath));
    }

    public FileShellInput(Path filePath) {
        this.filePath = filePath;
        try {
            if (Files.exists(filePath)) {
                this.content = Files.readString(filePath, StandardCharsets.UTF_8);
            } else {
                this.content = "";
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public ShellChunk read() {
        if (isClosed()) return null;
        if (position >= content.length()) return null;
        String remaining = content.substring(position);
        position = content.length();
        return ShellChunk.text(remaining);
    }

    public Path getFilePath() {
        return filePath;
    }
}
