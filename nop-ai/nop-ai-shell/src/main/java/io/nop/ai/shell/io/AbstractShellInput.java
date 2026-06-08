package io.nop.ai.shell.io;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class AbstractShellInput implements IShellInput {

    private final StringBuilder buffer = new StringBuilder();
    private volatile boolean eofSeen = false;
    private volatile boolean closed = false;

    public String readLine() {
        if (eofSeen) return null;

        while (true) {
            int nlIndex = buffer.indexOf("\n");
            if (nlIndex >= 0) {
                String line = buffer.substring(0, nlIndex);
                buffer.delete(0, nlIndex + 1);
                return line;
            }

            ShellChunk chunk = read();
            if (chunk == null) {
                eofSeen = true;
                if (buffer.length() > 0) {
                    String line = buffer.toString();
                    buffer.setLength(0);
                    return line;
                }
                return null;
            }

            if (chunk.isEof()) {
                eofSeen = true;
                if (buffer.length() > 0) {
                    String line = buffer.toString();
                    buffer.setLength(0);
                    return line;
                }
                return null;
            }

            if (chunk.isText()) {
                buffer.append(chunk.asText());
            }
        }
    }

    public String readAllText() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            ShellChunk chunk = read();
            if (chunk == null || chunk.isEof()) break;
            if (chunk.isText()) {
                sb.append(chunk.asText());
            }
        }
        return sb.toString();
    }

    public Iterator<String> lines() {
        return new Iterator<String>() {
            private String nextLine = null;

            @Override
            public boolean hasNext() {
                if (nextLine != null) return true;
                if (eofSeen) return false;
                nextLine = readLine();
                return nextLine != null;
            }

            @Override
            public String next() {
                if (!hasNext()) throw new NoSuchElementException();
                String line = nextLine;
                nextLine = null;
                return line;
            }
        };
    }

    public Iterator<ShellChunk> chunks() {
        return new Iterator<ShellChunk>() {
            private ShellChunk nextChunk = null;
            private boolean done = false;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (nextChunk != null) return true;
                nextChunk = read();
                if (nextChunk == null || nextChunk.isEof()) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public ShellChunk next() {
                if (!hasNext()) throw new NoSuchElementException();
                ShellChunk chunk = nextChunk;
                nextChunk = null;
                return chunk;
            }
        };
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        eofSeen = true;
    }
}
