package io.nop.ai.shell.io;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListShellOutput implements IShellOutput {

    private final List<ShellChunk> chunks;
    private volatile boolean closed = false;

    public ListShellOutput() {
        this.chunks = new CopyOnWriteArrayList<>();
    }

    public ListShellOutput(List<ShellChunk> chunks) {
        this.chunks = chunks;
    }

    @Override
    public void write(ShellChunk chunk) {
        if (closed) throw new IllegalStateException("output closed");
        chunks.add(chunk);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            chunks.add(ShellChunk.eof());
        }
    }

    @Override
    public IShellInput asInput() {
        return new AbstractShellInput() {
            private int readIndex = 0;

            @Override
            public ShellChunk read() {
                if (readIndex >= chunks.size()) return null;
                ShellChunk chunk = chunks.get(readIndex++);
                if (chunk.isEof()) return null;
                return chunk;
            }

            @Override
            public boolean isClosed() {
                return closed || super.isClosed();
            }
        };
    }

    public List<ShellChunk> getChunks() {
        return chunks;
    }

    public String getTextContent() {
        StringBuilder sb = new StringBuilder();
        for (ShellChunk chunk : chunks) {
            if (chunk.isText()) {
                sb.append(chunk.asText());
            }
        }
        return sb.toString();
    }
}
