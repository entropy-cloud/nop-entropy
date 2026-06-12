package io.nop.commons.io.stream;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SafeLineReader implements AutoCloseable {
    private static final int BUF_SIZE = 8192;

    private final Reader reader;
    private final char[] buf;
    private int pos;
    private int limit;
    private boolean eof;

    private long lineIndex;
    private boolean closed;

    public SafeLineReader(Reader reader) {
        this.reader = reader;
        this.buf = new char[BUF_SIZE];
    }

    private int nextChar() {
        if (pos >= limit) {
            if (eof) return -1;
            try {
                limit = reader.read(buf);
                pos = 0;
                if (limit <= 0) {
                    eof = true;
                    return -1;
                }
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
        return buf[pos++];
    }

    private void skipUntilNewline() {
        while (true) {
            int c = nextChar();
            if (c == -1) return;
            if (c == '\n') return;
            if (c == '\r') {
                c = nextChar();
                if (c != -1 && c != '\n') {
                    pos--;
                }
                return;
            }
        }
    }

    public long getLineIndex() {
        return lineIndex;
    }

    public boolean hasNext() {
        return !eof;
    }

    public LineRead readLine(int maxChars) {
        StringBuilder sb = maxChars > 0 ? new StringBuilder(Math.min(maxChars, BUF_SIZE)) : new StringBuilder(BUF_SIZE);
        boolean truncated = false;

        while (true) {
            int c = nextChar();
            if (c == -1) {
                lineIndex++;
                return new LineRead(sb.length() == 0 && !truncated ? null : sb.toString(), truncated, false);
            }
            if (c == '\n') {
                lineIndex++;
                return new LineRead(sb.toString(), truncated, true);
            }
            if (c == '\r') {
                int next = nextChar();
                if (next != -1 && next != '\n') {
                    pos--;
                }
                lineIndex++;
                return new LineRead(sb.toString(), truncated, true);
            }
            if (!truncated && sb.length() < maxChars) {
                sb.append((char) c);
            } else if (!truncated && sb.length() == maxChars) {
                truncated = true;
            }
        }
    }

    public int skipLines(int count) {
        for (int i = 0; i < count; i++) {
            if (eof) return i;
            skipUntilNewline();
            lineIndex++;
        }
        return count;
    }

    public static List<LineRead> readLines(Reader reader, int fromLine, int toLine, int maxChars) {
        List<LineRead> result = new ArrayList<>();
        processLines(reader, fromLine, toLine, maxChars, result::add);
        return result;
    }

    public static int countLines(Reader reader) {
        SafeLineReader r = new SafeLineReader(reader);
        try {
            while (r.hasNext()) {
                LineRead line = r.readLine(Integer.MAX_VALUE);
                if (line.content == null && !line.truncated) break;
            }
            return (int) r.getLineIndex();
        } finally {
            IoHelper.safeCloseObject(r);
        }
    }

    public static int countLines(Reader reader, int maxLines) {
        SafeLineReader r = new SafeLineReader(reader);
        try {
            while (r.hasNext() && r.getLineIndex() < maxLines) {
                LineRead line = r.readLine(Integer.MAX_VALUE);
                if (line.content == null && !line.truncated) break;
            }
            return (int) r.getLineIndex();
        } finally {
            IoHelper.safeCloseObject(r);
        }
    }

    public static void processLines(Reader reader, int fromLine, int toLine, int maxChars, Consumer<LineRead> consumer) {
        SafeLineReader r = new SafeLineReader(reader);
        try {
            if (fromLine > 1) {
                r.skipLines(fromLine - 1);
            }

            long endLine = (long) fromLine + (toLine - fromLine);
            while (r.hasNext() && r.getLineIndex() < endLine) {
                LineRead line = r.readLine(maxChars);
                if (line.content == null && !line.truncated) break;
                consumer.accept(line);
            }
        } finally {
            IoHelper.safeCloseObject(r);
        }
    }

    public static String readText(Reader reader, int maxChars) {
        StringBuilder sb = new StringBuilder(Math.min(maxChars > 0 ? maxChars : BUF_SIZE, BUF_SIZE));
        boolean truncated = false;
        SafeLineReader r = new SafeLineReader(reader);
        try {
            char[] tmp = new char[BUF_SIZE];
            while (true) {
                int n;
                try {
                    n = r.reader.read(tmp);
                } catch (IOException e) {
                    throw NopException.adapt(e);
                }
                if (n <= 0) break;
                if (!truncated) {
                    if (maxChars > 0 && sb.length() + n > maxChars) {
                        sb.append(tmp, 0, maxChars - sb.length());
                        truncated = true;
                    } else {
                        sb.append(tmp, 0, n);
                    }
                }
                if (truncated) break;
            }
        } finally {
            IoHelper.safeCloseObject(r);
        }
        return sb.toString();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            IoHelper.safeCloseObject(reader);
        }
    }

    public static final class LineRead {
        private final String content;
        private final boolean truncated;
        private final boolean hasNewline;

        public LineRead(String content, boolean truncated, boolean hasNewline) {
            this.content = content;
            this.truncated = truncated;
            this.hasNewline = hasNewline;
        }

        public String getContent() {
            return content;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public boolean isHasNewline() {
            return hasNewline;
        }

        public long getLineNumber() {
            return -1;
        }
    }
}
