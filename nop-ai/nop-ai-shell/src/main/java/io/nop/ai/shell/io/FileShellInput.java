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
        // plan 2557 C8-012 (ruling J): a giant stored file must not surface as
        // one giant chunk — split into successive bounded chunks so every
        // downstream consumer sees per-chunk boundedness.
        int end = Math.min(position + AbstractShellInput.MAX_READ_LINE_CHARS, content.length());
        String slice = content.substring(position, end);
        position = end;
        return ShellChunk.text(slice);
    }

    public String getFilePath() {
        return filePath;
    }
}
