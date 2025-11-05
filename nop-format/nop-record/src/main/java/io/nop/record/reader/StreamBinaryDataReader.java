package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;

/**
 * 只作为基础的顺序InputStream读取器，不负责缓存和seek
 */
public class StreamBinaryDataReader implements IBinaryDataReader {

    private final InputStream inputStream;
    private long position = 0;
    private boolean closed = false;
    private final boolean markSupported;

    // 单字节回退缓存
    private int pushedBackByte = -1;
    private boolean hasPushedBack = false;

    public StreamBinaryDataReader(InputStream inputStream) {
        if (inputStream == null) throw new IllegalArgumentException("inputStream cannot be null");
        this.inputStream = inputStream;
        this.markSupported = inputStream.markSupported();
    }

    @Override
    public long pos() {
        return position;
    }

    @Override
    public void alignToByte() {
        // Stream不支持位操作，无需处理
    }

    @Override
    public int getBitsLeft() {
        return 0;
    }

    @Override
    public void setBitsLeft(int bitsLeft) {
        // Stream不支持位操作，忽略
    }

    @Override
    public long getBits() {
        return 0;
    }

    @Override
    public void setBits(long bits) {
        // Stream不支持位操作，忽略
    }

    @Override
    public long available() throws IOException {
        checkNotClosed();
        long streamAvailable = inputStream.available();
        return hasPushedBack ? streamAvailable + 1 : streamAvailable;
    }

    @Override
    public void skip(long n) throws IOException {
        if (n < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                    .param("reason", "Skip count cannot be negative: " + n);
        }
        if (n == 0) return;

        checkNotClosed();

        long remaining = n;

        // 先消费推回的字节
        if (hasPushedBack) {
            clearPushedBackByte();
            remaining--;
            position++;
        }

        // 批量跳过字节
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                position += skipped;
            } else {
                // skip没有跳过，直接读取一个字节丢弃
                int val = inputStream.read();
                if (val == -1) {
                    throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                            .param("reason", "Reached EOF, cannot skip " + remaining + " more bytes");
                }
                remaining--;
                position++;
            }
        }
    }

    @Override
    public void seek(long newPos) {
        throw new UnsupportedOperationException("seek() is not supported for underlying stream");
    }

    @Override
    public int read() throws IOException {
        checkNotClosed();

        int val;
        if (hasPushedBack) {
            val = pushedBackByte;
            clearPushedBackByte();
        } else {
            val = inputStream.read();
        }

        if (val != -1) {
            position++;
        }
        return val;
    }

    @Override
    public int read(byte[] out, int off, int len) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        if (off < 0 || len < 0 || off + len > out.length) {
            throw new IllegalArgumentException("Invalid offset or length");
        }
        if (len == 0) return 0;

        checkNotClosed();

        int totalRead = 0;

        // 先处理推回的字节
        if (hasPushedBack) {
            out[off] = (byte) pushedBackByte;
            clearPushedBackByte();
            totalRead = 1;
            position++;

            if (len == 1) {
                return 1;
            }
        }

        // 继续读取剩余字节
        int bytesRead = inputStream.read(out, off + totalRead, len - totalRead);
        if (bytesRead > 0) {
            totalRead += bytesRead;
            position += bytesRead;
        }

        return totalRead > 0 ? totalRead : bytesRead;
    }



    @Override
    public byte[] readBytesTerm(byte term, boolean includeTerm, boolean consumeTerm, boolean eosError) throws IOException {
        checkNotClosed();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int val;
            if (hasPushedBack) {
                val = pushedBackByte;
                clearPushedBackByte();
            } else {
                val = inputStream.read();
            }

            if (val == -1) {
                if (eosError) {
                    throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                            .param("reason", "End of stream reached, but no terminator " + term + " found");
                } else {
                    return buf.toByteArray();
                }
            }

            position++;
            byte c = (byte) val;
            if (c == term) {
                if (includeTerm) {
                    buf.write(c);
                }
                if (!consumeTerm) {
                    // 将终止符推回
                    pushedBackByte = val;
                    hasPushedBack = true;
                    position--; // 回退位置计数
                }
                return buf.toByteArray();
            }
            buf.write(c);
        }
    }

    @Override
    public boolean isEof() throws IOException {
        checkNotClosed();

        if (hasPushedBack) {
            return false;
        }

        int val = inputStream.read();
        if (val == -1) {
            return true;
        } else {
            pushedBackByte = val;
            hasPushedBack = true;
            return false;
        }
    }

    @Override
    public boolean hasRemainingBytes() throws IOException {
        checkNotClosed();
        return hasPushedBack || available() > 0;
    }

    @Override
    public void reset() throws IOException {
        checkNotClosed();

        if (!markSupported) {
            throw new UnsupportedOperationException("InputStream does not support mark/reset");
        }

        try {
            inputStream.reset();
            position = 0;
            clearPushedBackByte();
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public IBinaryDataReader subInput(long maxLength) throws IOException {
        return new SubBinaryDataReader(this, maxLength);
    }

    @Override
    public IBinaryDataReader detach() throws IOException {
        checkNotClosed();

        ByteArrayOutputStream fullData = new ByteArrayOutputStream();

        // 先处理推回的字节
        if (hasPushedBack) {
            fullData.write(pushedBackByte);
        }

        // 读取剩余数据
        byte[] remaining = IoHelper.readBytes(inputStream);
        fullData.write(remaining);

        byte[] allData = fullData.toByteArray();
        return new ByteBufferBinaryDataReader(allData);
    }

    @Override
    public IBinaryDataReader duplicate() throws IOException {
        throw new UnsupportedOperationException("duplicate is not supported for stream-based reader");
    }

    @Override
    public boolean isDetached() {
        return false;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("InputStream has been closed");
        }
    }

    private void clearPushedBackByte() {
        hasPushedBack = false;
        pushedBackByte = -1;
    }

    public boolean isMarkSupported() {
        return markSupported;
    }

    public void mark(int readlimit) {
        checkNotClosed();

        if (!markSupported) {
            throw new UnsupportedOperationException("InputStream does not support mark operation");
        }

        inputStream.mark(readlimit);
    }

    public String getReaderInfo() {
        return String.format("StreamBinaryDataReader{position=%d, closed=%s, markSupported=%s, hasPushedBack=%s}",
                position, closed, markSupported, hasPushedBack);
    }
}