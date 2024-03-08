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
package io.nop.record.input;

import java.io.Closeable;

/**
 * 从KaitaiStruct项目的KaitaiStream类拷贝部分实现代码
 */
public interface IRecordBinaryInput extends Closeable {

    //region Stream positioning

    /**
     * Check if stream pointer is at the end of stream.
     *
     * @return true if we are located at the end of the stream
     */
    boolean isEof();

    boolean hasRemainingBytes();

    default void seek(long newPos) {
        reset();
        skip(newPos);
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
    byte readS1();

    //region Big-endian

    short readS2be();

    int readS4be();

    long readS8be();

    //endregion

    //region Little-endian

    short readS2le();

    int readS4le();

    long readS8le();

    //endregion

    //endregion

    //region Unsigned

    int readU1();

    //region Big-endian

    int readU2be();

    long readU4be();

    /**
     * Reads one unsigned 8-byte integer in big-endian encoding. As Java does not
     * have a primitive data type to accomodate it, we just reuse {@link #readS8be()}.
     *
     * @return 8-byte signed integer (pretending to be unsigned) read from a stream
     */
    default long readU8be() {
        return readS8be();
    }

    //endregion

    //region Little-endian

    int readU2le();

    long readU4le();

    /**
     * Reads one unsigned 8-byte integer in little-endian encoding. As Java does not
     * have a primitive data type to accomodate it, we just reuse {@link #readS8le()}.
     *
     * @return 8-byte signed integer (pretending to be unsigned) read from a stream
     */
    default long readU8le() {
        return readS8le();
    }

    //endregion

    //endregion

    //endregion

    //region Floating point numbers

    //region Big-endian

    float readF4be();

    double readF8be();

    //endregion

    //region Little-endian

    float readF4le();

    double readF8le();

    //endregion

    //endregion

    //region Unaligned bit values

    void alignToByte();

    int getBitsLeft();

    void setBitsLeft(int bitsLeft);

    long getBits();

    void setBits(long bits);

    default long readBitsIntBe(int n) {
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

    default long readBitsIntLe(int n) {
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
    byte[] readBytes(int n);

    /**
     * Reads all the remaining bytes in a stream as byte array.
     *
     * @return all remaining bytes in a stream as byte array
     */
    byte[] readBytesFull();

    byte[] readBytesTerm(byte term, boolean includeTerm, boolean consumeTerm, boolean eosError);

    long available();

    void skip(long n);

    void read(byte[] data, int offset, int len);

    default int readByte() {
        return readS1();
    }

    /**
     * 重置offset为0
     */
    void reset();

    IRecordBinaryInput subInput(long maxLength);

    IRecordBinaryInput detach();

    boolean isDetached();
}