package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static io.nop.ai.shell.NopAiShellErrors.ARG_DETAIL;
import static io.nop.ai.shell.NopAiShellErrors.ERR_AI_SHELL_INPUT_CAP_EXCEEDED;

/**
 * Buffered {@link IShellInput} base class. Keeps an internal buffer so that text split across
 * chunks is assembled correctly, and overrides the interface's stateless default convenience
 * methods ({@link #readLine()}, {@link #readAllText()}, {@link #lines()}) with exact
 * cross-call semantics. All built-in shell input implementations extend this class.
 */
public abstract class AbstractShellInput implements IShellInput {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractShellInput.class);

    /**
     * plan 2557 C8-012 (ruling J): per-readLine bound. A line longer than this
     * cap is returned as successive bounded SPLICES — each readLine call
     * returns at most this many chars and the remainder stays buffered, so the
     * concatenation of successive splices restores the full line. This keeps
     * every returned string bounded without changing the IShellInput signature
     * (observable contract, semantics reused from SafeLineReader's cap).
     */
    public static final int MAX_READ_LINE_CHARS = 64 * 1024;

    /**
     * plan 2557 C8-012 (ruling J): total char cap for {@link #readAllText()}.
     * Exceeding it fails loud with {@code ERR_AI_SHELL_INPUT_CAP_EXCEEDED} —
     * never a silent truncation. Subclasses may override to narrow the bound.
     */
    public static final int MAX_READ_ALL_CHARS = 2 * 1024 * 1024;

    private final StringBuilder buffer = new StringBuilder();
    private volatile boolean eofSeen = false;
    private volatile boolean closed = false;

    /**
     * Override point for the {@link #readAllText()} total cap (test seam).
     */
    protected int readAllTextMaxChars() {
        return MAX_READ_ALL_CHARS;
    }

    @Override
    public String readLine() {
        if (eofSeen) return null;

        while (true) {
            int nlIndex = buffer.indexOf("\n");
            if (nlIndex >= 0) {
                if (nlIndex > MAX_READ_LINE_CHARS) {
                    // ruling J: the completed line itself is over-cap — return
                    // a bounded splice first and keep the rest (including the
                    // newline) buffered for the following readLine calls
                    String splice = buffer.substring(0, MAX_READ_LINE_CHARS);
                    buffer.delete(0, MAX_READ_LINE_CHARS);
                    return splice;
                }
                String line = buffer.substring(0, nlIndex);
                buffer.delete(0, nlIndex + 1);
                return line;
            }

            // ruling J: over-cap line without a newline yet — return a bounded
            // splice and keep the remainder buffered; successive readLine calls
            // return successive splices whose concatenation is the full line.
            if (buffer.length() > MAX_READ_LINE_CHARS) {
                String splice = buffer.substring(0, MAX_READ_LINE_CHARS);
                buffer.delete(0, MAX_READ_LINE_CHARS);
                return splice;
            }

            ShellChunk chunk = read();
            if (chunk == null) {
                eofSeen = true;
                return takeEofRemainder();
            }

            if (chunk.isEof()) {
                eofSeen = true;
                return takeEofRemainder();
            }

            if (chunk.isText()) {
                buffer.append(chunk.asText());
            } else {
                LOG.warn("skip non-text chunk while reading a line: type={}", chunk.isBinary() ? "binary" : "other");
            }
        }
    }

    private String takeEofRemainder() {
        // the loop-top splice check keeps the buffered remainder at
        // MAX_READ_LINE_CHARS or below by the time EOF is observed
        if (buffer.length() > 0) {
            String line = buffer.toString();
            buffer.setLength(0);
            return line;
        }
        return null;
    }

    @Override
    public String readAllText() {
        StringBuilder sb = new StringBuilder();
        int maxChars = readAllTextMaxChars();
        while (true) {
            ShellChunk chunk = read();
            if (chunk == null || chunk.isEof()) break;
            if (chunk.isText()) {
                sb.append(chunk.asText());
                if (sb.length() > maxChars) {
                    // ruling J: total cap is fail-loud, never silent truncation
                    throw new NopException(ERR_AI_SHELL_INPUT_CAP_EXCEEDED).param(ARG_DETAIL,
                            "maxChars=" + maxChars + ", lineCap=" + MAX_READ_LINE_CHARS);
                }
            } else {
                LOG.warn("skip non-text chunk while reading text: type={}", chunk.isBinary() ? "binary" : "other");
            }
        }
        return sb.toString();
    }

    @Override
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

    @Override
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
    public void pushBack(ShellChunk chunk) {
        if (chunk != null && chunk.isText()) {
            buffer.insert(0, chunk.asText());
        }
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
