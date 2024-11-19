/**
 * Copyright 2015-2022 Kaitai Project: MIT license
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.nop.record.reader;

import io.nop.commons.bytes.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * 从KaitaiStruct项目的KaitaiStream类拷贝部分实现代码
 */
public interface IBinaryDataReader extends IDataReaderBase, DataInput {

    int DEFAULT_BUFFER_SIZE = 4 * 1024;

    //region Stream positioning

    /**
     * Check if stream pointer is at the end of stream.
     *
     * @return true if we are located at the end of the stream
     */
    boolean isEof() throws IOException;

    boolean hasRemainingBytes() throws IOException;

    default void seek(long newPos) throws IOException {
        reset();
        skip(newPos);
    }

    default <T> T doAtPos(Supplier<T> task) throws IOException {
        long pos = pos();
        try {
            return task.get();
        } finally {
            seek(pos);
        }
    }

    /**
     * Get current position of a stream pointer.
     *
     * @return pointer position, number of bytes from the beginning of the stream
     */
    long pos();

    //endregion

    //region Integer numbers

    //region Signed

    /**
     * Reads one signed 1-byte integer, returning it properly as Java's "byte" type.
     *
     * @return 1-byte integer read from a stream
     */
    default byte readS1() throws IOException {
        int t = read();
        if (t < 0) {
            throw new EOFException();
        } else {
            return (byte) t;
        }
    }

    //region Big-endian

    default short readS2be() throws IOException {
        int b1 = read();
        int b2 = read();
        if ((b1 | b2) < 0) {
            throw new EOFException();
        } else {
            return (short) ((b1 << 8) + (b2));
        }
    }

    default int readS4be() throws IOException {
        int b1 = read();
        int b2 = read();
        int b3 = read();
        int b4 = read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new EOFException();
        } else {
            return (b1 << 24) + (b2 << 16) + (b3 << 8) + (b4);
        }
    }

    default long readS8be() throws IOException {
        long b1 = readU4be();
        long b2 = readU4be();
        return (b1 << 32) + (b2);
    }

    //endregion

    //region Little-endian

    default short readS2le() throws IOException {
        int b1 = read();
        int b2 = read();
        if ((b1 | b2) < 0) {
            throw new EOFException();
        } else {
            return (short) ((b2 << 8) + (b1));
        }
    }

    default int readS4le() throws IOException {
        int b1 = read();
        int b2 = read();
        int b3 = read();
        int b4 = read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new EOFException();
        } else {
            return (b4 << 24) + (b3 << 16) + (b2 << 8) + (b1);
        }
    }

    default long readS8le() throws IOException {
        long b1 = readU4le();
        long b2 = readU4le();
        return (b2 << 32) + (b1);
    }

    //endregion

    //endregion

    //region Unsigned

    default short readU1() throws IOException {
        int t = read();
        if (t < 0) {
            throw new EOFException();
        } else {
            return (short) t;
        }
    }

    //region Big-endian

    default int readU2be() throws IOException {
        int b1 = read();
        int b2 = read();
        if ((b1 | b2) < 0) {
            throw new EOFException();
        } else {
            return (b1 << 8) + (b2);
        }
    }

    default long readU4be() throws IOException {
        long b1 = read();
        long b2 = read();
        long b3 = read();
        long b4 = read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new EOFException();
        } else {
            return (b1 << 24) + (b2 << 16) + (b3 << 8) + (b4);
        }
    }

    /**
     * Reads one unsigned 8-byte integer in big-endian encoding. As Java does not
     * have a primitive data type to accomodate it, we just reuse {@link #readS8be()}.
     *
     * @return 8-byte signed integer (pretending to be unsigned) read from a stream
     */
    default long readU8be() throws IOException {
        return readS8be();
    }

    //endregion

    //region Little-endian

    default int readU2le() throws IOException {
        int b1 = read();
        int b2 = read();
        if ((b1 | b2) < 0) {
            throw new EOFException();
        } else {
            return (b2 << 8) + (b1);
        }
    }

    default long readU4le() throws IOException {
        long b1 = read();
        long b2 = read();
        long b3 = read();
        long b4 = read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new EOFException();
        } else {
            return (b4 << 24) + (b3 << 16) + (b2 << 8) + (b1);
        }
    }

    /**
     * Reads one unsigned 8-byte integer in little-endian encoding. As Java does not
     * have a primitive data type to accomodate it, we just reuse {@link #readS8le()}.
     *
     * @return 8-byte signed integer (pretending to be unsigned) read from a stream
     */
    default long readU8le() throws IOException {
        return readS8le();
    }

    //endregion

    //endregion

    //endregion

    //region Floating point numbers

    //region Big-endian

    default float readF4be() throws IOException {
        return readBufferBe(4).getFloat();
    }

    default double readF8be() throws IOException {
        return readBufferBe(8).getDouble();
    }

    //endregion

    //region Little-endian

    default float readF4le() throws IOException {
        return readBufferLe(4).getFloat();
    }

    default double readF8le() throws IOException {
        return readBufferLe(8).getDouble();
    }

    //endregion

    //endregion

    //region Unaligned bit values

    void alignToByte();

    int getBitsLeft();

    void setBitsLeft(int bitsLeft);

    long getBits();

    void setBits(long bits);

    default long readBitsIntBe(int n) throws IOException {
        long res = 0;
        int bitsLeft = getBitsLeft();
        long bits = getBits();

        int bitsNeeded = n - bitsLeft;
        bitsLeft = -bitsNeeded & 7; // `-bitsNeeded mod 8`

        if (bitsNeeded > 0) {
            // 1 bit  => 1 byte
            // 8 bits => 1 byte
            // 9 bits => 2 bytes
            int bytesNeeded = ((bitsNeeded - 1) / 8) + 1; // `ceil(bitsNeeded / 8)`
            byte[] buf = readBytes(bytesNeeded);
            for (byte b : buf) {
                // `b` is signed byte, convert to unsigned using the "& 0xff" trick
                res = res << 8 | (b & 0xff);
            }

            long newBits = res;
            res = res >>> bitsLeft | bits << bitsNeeded;
            bits = newBits; // will be masked at the end of the function
        } else {
            res = bits >>> -bitsNeeded; // shift unneeded bits out
        }

        long mask = (1L << bitsLeft) - 1; // `bitsLeft` is in range 0..7, so `(1L << 64)` does not have to be considered
        bits &= mask;

        setBits(bits);
        setBitsLeft(bitsLeft);
        return res;
    }

    default long readBitsIntLe(int n) throws IOException {
        long res = 0;
        int bitsLeft = getBitsLeft();
        long bits = getBits();

        int bitsNeeded = n - bitsLeft;

        if (bitsNeeded > 0) {
            // 1 bit  => 1 byte
            // 8 bits => 1 byte
            // 9 bits => 2 bytes
            int bytesNeeded = ((bitsNeeded - 1) / 8) + 1; // `ceil(bitsNeeded / 8)`
            byte[] buf = readBytes(bytesNeeded);
            for (int i = 0; i < bytesNeeded; i++) {
                // `buf[i]` is signed byte, convert to unsigned using the "& 0xff" trick
                res |= ((long) (buf[i] & 0xff)) << (i * 8);
            }

            // NB: in Java, bit shift operators on left-hand operand of type `long` work
            // as if the right-hand operand were subjected to `& 63` (`& 0b11_1111`) (see
            // https://docs.oracle.com/javase/specs/jls/se7/html/jls-15.html#jls-15.19),
            // so `res >>> 64` is equivalent to `res >>> 0` (but we don't want that)
            long newBits = bitsNeeded < 64 ? res >>> bitsNeeded : 0;
            res = res << bitsLeft | bits;
            bits = newBits;
        } else {
            res = bits;
            bits >>>= n;
        }

        bitsLeft = -bitsNeeded & 7; // `-bitsNeeded mod 8`

        if (n < 64) {
            long mask = (1L << n) - 1;
            res &= mask;
        }

        setBits(bits);
        setBitsLeft(bitsLeft);

        // if `n == 64`, do nothing
        return res;
    }

    //endregion

    //region Byte arrays

    /**
     * Reads designated number of bytes from the stream.
     *
     * @param n number of bytes to read
     * @return read bytes as byte array
     */
    default byte[] readBytes(int n) throws IOException {
        return readFully(n);
    }

    default ByteString readByteString(int n) throws IOException {
        return ByteString.of(readBytes(n));
    }

    /**
     * Reads all the remaining bytes in a stream as byte array.
     *
     * @return all remaining bytes in a stream as byte array
     */
    default byte[] readAvailableBytes() throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int readCount;
        while (-1 != (readCount = read(buffer)))
            baos.write(buffer, 0, readCount);

        return baos.toByteArray();
    }

    default byte[] readBytesTerm(byte term, boolean includeTerm, boolean consumeTerm, boolean eosError) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int c = read();
            if (c < 0) {
                if (eosError) {
                    throw new RuntimeException("End of stream reached, but no terminator " + term + " found");
                } else {
                    return buf.toByteArray();
                }
            } else if ((byte) c == term) {
                if (includeTerm)
                    buf.write(c);
                if (!consumeTerm)
                    seek(pos() - 1);
                return buf.toByteArray();
            }
            buf.write(c);
        }
    }

    default String readString(int length, Charset charset) throws IOException {
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        byte[] data = readBytes(length);
        return new String(data, charset);
    }

    @Override
    default String readLine() throws IOException {
        throw new UnsupportedOperationException("readLine");
    }

    @Override
    default String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }

    long available() throws IOException;

    void skip(long n) throws IOException;

    default int skipBytes(int n) throws IOException {
        skip(n);
        return n;
    }

    default int tryReadFully(byte[] data, int offset, int len) throws IOException {
        int nRead = 0;
        do {
            int n = read(data, offset, len);
            if (n < 0)
                return nRead > 0 ? nRead : -1;
            nRead += n;
            len -= n;
            if (len <= 0)
                break;
            offset += n;
        } while (true);
        return nRead;
    }

    default void readFully(byte[] data, int offset, int len) throws IOException {
        int nRead = tryReadFully(data, offset, len);
        if (nRead != len)
            throw new EOFException("Read " + nRead + " bytes, but expected " + len);
    }

    default void readFully(byte[] data) throws IOException {
        readFully(data, 0, data.length);
    }

    int read(byte[] data, int offset, int len) throws IOException;

    default byte[] readFully(int n) throws IOException {
        byte[] data = new byte[n];
        readFully(data, 0, n);
        return data;
    }

    default int read(byte[] data) throws IOException {
        return read(data, 0, data.length);
    }

    default int read() throws IOException {
        return readS1();
    }

    default boolean readBoolean() throws IOException {
        int n = read();
        if (n < 0)
            throw new EOFException();
        return n != 0;
    }

    default byte readByte() throws IOException {
        return readS1();
    }

    default int readUnsignedByte() throws IOException {
        return readU1();
    }

    default short readShort() throws IOException {
        return readS2be();
    }

    default int readUnsignedShort() throws IOException {
        return readU2be();
    }

    default char readChar() throws IOException {
        return (char) readShort();
    }

    default int readInt() throws IOException {
        return readS4be();
    }

    default long readLong() throws IOException {
        return readS8be();
    }

    default float readFloat() throws IOException {
        return readF4be();
    }

    default double readDouble() throws IOException {
        return readF8be();
    }

    default ByteBuffer readBufferLe(int count) throws IOException {
        return ByteBuffer.wrap(readBytes(count)).order(ByteOrder.LITTLE_ENDIAN);
    }

    default ByteBuffer readBufferBe(int count) throws IOException {
        return ByteBuffer.wrap(readBytes(count)).order(ByteOrder.BIG_ENDIAN);
    }

    /**
     * 重置offset为0
     */
    void reset() throws IOException;

    IBinaryDataReader subInput(long maxLength) throws IOException;

    IBinaryDataReader detach() throws IOException;

    IBinaryDataReader duplicate() throws IOException;

    boolean isDetached();
}