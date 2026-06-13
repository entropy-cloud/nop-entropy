package io.nop.ai.shell.io;

import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.TextResult;

public class FileShellInput extends AbstractShellInput {

    private final String filePath;
    private final String content;
    private int position = 0;

    public FileShellInput(String filePath, IToolFileSystem fileSystem) {
        this.filePath = filePath;
        TextResult result = fileSystem.readText(filePath, 0);
        this.content = result != null ? result.getContent() : "";
    }

    public FileShellInput(String filePath, String content) {
        this.filePath = filePath;
        this.content = content != null ? content : "";
    }

    @Override
    public ShellChunk read() {
        if (isClosed()) return null;
        if (position >= content.length()) return null;
        String remaining = content.substring(position);
        position = content.length();
        return ShellChunk.text(remaining);
    }

    public String getFilePath() {
        return filePath;
    }
}
