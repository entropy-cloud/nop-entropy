package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;

import java.io.IOException;
import java.io.Reader;

import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;

/**
 * 基于Reader的文本数据读取器
 * 提供流式读取功能，支持detach操作将所有数据读取到内存中
 */
public class ReaderTextDataReader implements ITextDataReader {

    private final Reader reader;
    private long position = 0;
    private boolean closed = false;
    private final boolean markSupported;

    // 单字符回退缓存
    private int pushedBackChar = -1;
    private boolean hasPushedBack = false;

    public ReaderTextDataReader(Reader reader) {
        if (reader == null) throw new IllegalArgumentException("reader cannot be null");
        this.reader = reader;
        this.markSupported = reader.markSupported();
    }

    @Override
    public long available() {
        checkNotClosed();
        try {
            // Reader没有可靠的available方法
            return reader.ready() ? 1 : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public void skip(int n) {
        if (n < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                    .param("reason", "Skip count cannot be negative: " + n);
        }
        if (n == 0) return;

        checkNotClosed();

        try {
            long remaining = n;
            final int BUF_SIZE = 1024;
            char[] buffer = null;

            // 先消费推回的字符
            if (hasPushedBack) {
                clearPushedBackChar();
                remaining--;
                position++;
            }

            // 批量跳过字符
            while (remaining > 0) {
                long skipped = reader.skip(remaining);
                if (skipped > 0) {
                    remaining -= skipped;
                    position += skipped;
                } else {
                    // skip没有跳，直接物理拉取读丢弃
                    int toRead = (int) Math.min(remaining, BUF_SIZE);
                    if (buffer == null) buffer = new char[BUF_SIZE];
                    int readCount = reader.read(buffer, 0, toRead);
                    if (readCount == -1)
                        throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                                .param("reason", "Reached EOF, cannot skip " + remaining + " more characters");
                    remaining -= readCount;
                    position += readCount;
                }
            }
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public boolean isEof() {
        checkNotClosed();

        if (hasPushedBack) {
            return false;
        }

        try {
            int ch = reader.read();
            if (ch == -1) {
                return true;
            } else {
                pushedBackChar = ch;
                hasPushedBack = true;
                return false;
            }
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * 尽量尝试读取n个字符，如遇EOF或者可用字符少于n，则返回实际读到的内容，哪怕为""也不会抛异常。
     *
     * @param n 期望读取字符数
     * @return 最多n个字符，实际可用多少返回多少，不足n不抛异常
     */
    public String tryRead(int n) throws IOException {
        if (n < 0)
            throw new IllegalArgumentException("tryRead length cannot be negative: " + n);
        if (n == 0)
            return "";

        checkNotClosed();

        char[] buffer = new char[n];
        int totalRead = 0;

        // 推回字符先填充
        if (hasPushedBack) {
            buffer[0] = (char) pushedBackChar;
            totalRead = 1;
            clearPushedBackChar();
        }

        // 继续读取
        while (totalRead < n) {
            int readCount = reader.read(buffer, totalRead, n - totalRead);
            if (readCount == -1) {
                break;
            }
            totalRead += readCount;
        }

        if (totalRead > 0) {
            position += totalRead;
            return new String(buffer, 0, totalRead);
        } else {
            // EOF 或不可读
            return "";
        }
    }

    @Override
    public String readFully(int len) {
        if (len < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                    .param("reason", "Read length cannot be negative: " + len);
        }
        if (len == 0) {
            return "";
        }

        checkNotClosed();

        try {
            char[] buffer = new char[len];
            int totalRead = 0;

            // 推回字符先填充
            if (hasPushedBack) {
                buffer[0] = (char) pushedBackChar;
                totalRead = 1;
                clearPushedBackChar();
            }

            // 继续读取
            while (totalRead < len) {
                int readCount = reader.read(buffer, totalRead, len - totalRead);
                if (readCount == -1) {
                    if (totalRead == 0) {
                        throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                                .param("reason", "Reached EOF, no data available");
                    }
                    break;
                }
                totalRead += readCount;
            }

            if (totalRead < len) {
                throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                        .param("reason", "Requested " + len + " characters but only " + totalRead + " available");
            }

            position += totalRead;
            return new String(buffer, 0, totalRead);
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public int readChar() {
        checkNotClosed();

        try {
            int ch;
            if (hasPushedBack) {
                ch = pushedBackChar;
                clearPushedBackChar();
            } else {
                ch = reader.read();
            }

            if (ch != -1) {
                position++;
            }
            return ch;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public String readLine(int maxLength) {
        if (maxLength < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                    .param("reason", "Max length cannot be negative: " + maxLength);
        }

        checkNotClosed();

        try {
            StringBuilder result = new StringBuilder();
            int charsRead = 0;

            while (charsRead < maxLength) {
                int ch;
                if (hasPushedBack) {
                    ch = pushedBackChar;
                    clearPushedBackChar();
                } else {
                    ch = reader.read();
                }

                if (ch == -1) {
                    break;
                }

                position++;
                charsRead++;

                if (ch == '\r') {
                    int nextCh = reader.read();
                    if (nextCh == '\n') {
                        position++;
                    } else if (nextCh != -1) {
                        pushedBackChar = nextCh;
                        hasPushedBack = true;
                    }
                    break;
                } else if (ch == '\n') {
                    break;
                } else {
                    result.append((char) ch);
                }
            }

            return result.toString();
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public long pos() {
        return position;
    }

    public void seek(long pos) {
        throw new UnsupportedOperationException("Reader does not support seek operation. Use detach() to get seekable reader.");
    }

    @Override
    public void reset() {
        checkNotClosed();

        if (!markSupported) {
            throw new UnsupportedOperationException("Reader does not support reset operation. Mark not supported.");
        }

        try {
            reader.reset();
            position = 0;
            clearPushedBackChar();
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public ITextDataReader detach() {
        checkNotClosed();

        try {
            StringBuilder fullText = new StringBuilder();

            if (hasPushedBack) {
                fullText.append((char) pushedBackChar);
            }

            String remainingText = IoHelper.readText(reader);
            fullText.append(remainingText);

            String allText = fullText.toString();
            SimpleTextDataReader detached = new SimpleTextDataReader(allText);

            return detached;
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public boolean isDetached() {
        return false;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            reader.close();
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Reader has been closed");
        }
    }

    protected Reader getUnderlyingReader() {
        return reader;
    }

    public boolean isMarkSupported() {
        return markSupported;
    }

    public void mark(int readAheadLimit) {
        checkNotClosed();

        if (!markSupported) {
            throw new UnsupportedOperationException("Reader does not support mark operation");
        }

        try {
            reader.mark(readAheadLimit);
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    public String getReaderInfo() {
        return String.format("ReaderTextDataReader{position=%d, closed=%s, markSupported=%s, hasPushedBack=%s}",
                position, closed, markSupported, hasPushedBack);
    }

    private void clearPushedBackChar() {
        hasPushedBack = false;
        pushedBackChar = -1;
    }
}