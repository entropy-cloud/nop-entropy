package io.nop.ai.shell.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;

public interface IShellInput extends Closeable {

    Logger LOG = LoggerFactory.getLogger(IShellInput.class);

    ShellChunk read();

    /**
     * Reads one line of text. A line is terminated by '\n' (excluded) or by EOF. Returns null
     * when the stream is exhausted.
     * <p>
     * The default implementation is a stateless {@link #read()}-based loop: it accumulates text
     * chunks until a '\n' or EOF is reached, and hands any text following the '\n' back to the
     * stream via {@link #pushBack(ShellChunk)} so the next call can consume it. Implementations
     * that cannot buffer between calls should override {@link #pushBack(ShellChunk)} or extend
     * {@link AbstractShellInput}, which keeps an internal buffer and provides exact line
     * semantics across calls.
     * <p>
     * Non-text chunks (binary) are skipped with a WARN log — this is a text-only read.
     */
    default String readLine() {
        StringBuilder line = new StringBuilder();
        while (true) {
            ShellChunk chunk = read();
            if (chunk == null || chunk.isEof()) {
                return line.length() > 0 ? line.toString() : null;
            }
            if (chunk.isText()) {
                String text = chunk.asText();
                int nlIndex = text.indexOf('\n');
                if (nlIndex >= 0) {
                    line.append(text, 0, nlIndex);
                    String rest = text.substring(nlIndex + 1);
                    if (!rest.isEmpty()) {
                        pushBack(ShellChunk.text(rest));
                    }
                    return line.toString();
                }
                line.append(text);
            } else {
                LOG.warn("skip non-text chunk while reading a line: type={}", chunk.isBinary() ? "binary" : "other");
            }
        }
    }

    /**
     * Reads all remaining text until EOF and returns it as a single string.
     * <p>
     * This is a text-only read: non-text chunks (binary) are skipped with a WARN log instead of
     * being silently discarded. Implementations should override this method when they can
     * consume chunks more efficiently (see {@link AbstractShellInput}).
     */
    default String readAllText() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            ShellChunk chunk = read();
            if (chunk == null || chunk.isEof()) {
                return sb.toString();
            }
            if (chunk.isText()) {
                sb.append(chunk.asText());
            } else {
                LOG.warn("skip non-text chunk while reading text: type={}", chunk.isBinary() ? "binary" : "other");
            }
        }
    }

    /**
     * Returns an iterator over the lines of the stream (see {@link #readLine()} for line
     * semantics).
     */
    default Iterator<String> lines() {
        return new Iterator<String>() {
            private String nextLine;

            @Override
            public boolean hasNext() {
                if (nextLine != null)
                    return true;
                nextLine = readLine();
                return nextLine != null;
            }

            @Override
            public String next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                String line = nextLine;
                nextLine = null;
                return line;
            }
        };
    }

    /**
     * Returns an iterator over all chunks of the stream, excluding the EOF signal.
     */
    default Iterator<ShellChunk> chunks() {
        return new Iterator<ShellChunk>() {
            private ShellChunk nextChunk;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (done)
                    return false;
                if (nextChunk != null)
                    return true;
                nextChunk = read();
                if (nextChunk == null || nextChunk.isEof()) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public ShellChunk next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                ShellChunk chunk = nextChunk;
                nextChunk = null;
                return chunk;
            }
        };
    }

    /**
     * Hands a chunk back to the stream so the next {@link #read()} (or convenience method) call
     * can consume it first. The default implementation is stateless and drops the chunk;
     * implementations that support buffering (e.g. {@link AbstractShellInput}) should override
     * this to retain the chunk. Only text chunks are ever handed back by the default
     * {@link #readLine()} implementation.
     */
    default void pushBack(ShellChunk chunk) {
    }

    void close();

    boolean isClosed();
}
