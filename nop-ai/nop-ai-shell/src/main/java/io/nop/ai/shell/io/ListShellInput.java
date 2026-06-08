package io.nop.ai.shell.io;

import java.util.List;

public class ListShellInput extends AbstractShellInput {

    private final List<ShellChunk> chunks;
    private int readIndex = 0;

    public ListShellInput(List<ShellChunk> chunks) {
        this.chunks = chunks;
    }

    public ListShellInput() {
        this.chunks = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    @Override
    public ShellChunk read() {
        if (isClosed()) return null;
        if (readIndex >= chunks.size()) return null;
        ShellChunk chunk = chunks.get(readIndex++);
        if (chunk.isEof()) return null;
        return chunk;
    }

    public void add(ShellChunk chunk) {
        if (isClosed()) throw new IllegalStateException("input closed");
        chunks.add(chunk);
    }

    public void addText(String text) {
        add(ShellChunk.text(text));
    }

    public void sendEof() {
        chunks.add(ShellChunk.eof());
    }

    public List<ShellChunk> getChunks() {
        return chunks;
    }
}
