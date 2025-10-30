package io.nop.record.reader;

import java.io.IOException;

/**
 * 受限的二进制数据读取器，限制最大读取长度
 */
public class SubBinaryDataReader implements IBinaryDataReader {
    private final IBinaryDataReader underlying;
    private final long maxLength;
    private long position = 0;

    public SubBinaryDataReader(IBinaryDataReader underlying, long maxLength) {
        this.underlying = underlying;
        this.maxLength = maxLength;
    }

    @Override
    public boolean isEof() throws IOException {
        return position >= maxLength || underlying.isEof();
    }

    @Override
    public boolean hasRemainingBytes() throws IOException {
        return position < maxLength && underlying.hasRemainingBytes();
    }

    @Override
    public long pos() {
        return position;
    }

    @Override
    public long realPos() {
        return underlying.realPos();
    }

    @Override
    public void seek(long newPos) throws IOException {
        if (newPos > maxLength) {
            throw new IOException("Seek position " + newPos + " exceeds max length " + maxLength);
        }
        underlying.seek(newPos);
        position = newPos;
    }

    @Override
    public void skip(long n) throws IOException {
        long newPos = position + n;
        if (newPos > maxLength) {
            newPos = maxLength;
        }
        underlying.skip(newPos - position);
        position = newPos;
    }

    @Override
    public int read() throws IOException {
        if (position >= maxLength) {
            return -1;
        }
        int result = underlying.read();
        if (result >= 0) {
            position++;
        }
        return result;
    }

    @Override
    public int read(byte[] data, int offset, int len) throws IOException {
        if (position >= maxLength) {
            return -1;
        }

        long remainingInLimit = maxLength - position;
        int toRead = (int) Math.min(len, remainingInLimit);

        int bytesRead = underlying.read(data, offset, toRead);
        if (bytesRead > 0) {
            position += bytesRead;
        }
        return bytesRead;
    }

    @Override
    public long available() throws IOException {
        long underlyingAvailable = underlying.available();
        long remainingInLimit = maxLength - position;
        return Math.min(underlyingAvailable, remainingInLimit);
    }

    @Override
    public void reset() throws IOException {
        underlying.reset();
        position = 0;
    }

    // 委托其他方法到底层reader
    @Override
    public void alignToByte() {
        underlying.alignToByte();
    }

    @Override
    public int getBitsLeft() {
        return underlying.getBitsLeft();
    }

    @Override
    public void setBitsLeft(int bitsLeft) {
        underlying.setBitsLeft(bitsLeft);
    }

    @Override
    public long getBits() {
        return underlying.getBits();
    }

    @Override
    public void setBits(long bits) {
        underlying.setBits(bits);
    }

    @Override
    public IBinaryDataReader subInput(long maxLength) throws IOException {
        return underlying.subInput(Math.min(maxLength, this.maxLength - position));
    }

    @Override
    public IBinaryDataReader detach() throws IOException {
        return new SubBinaryDataReader(underlying.detach(), maxLength);
    }

    @Override
    public IBinaryDataReader duplicate() throws IOException {
        return new SubBinaryDataReader(underlying.duplicate(), maxLength);
    }

    @Override
    public boolean isDetached() {
        return underlying.isDetached();
    }

    @Override
    public void close() throws IOException {

    }
}