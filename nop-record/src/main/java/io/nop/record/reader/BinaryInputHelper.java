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

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class BinaryInputHelper {

    /**
     * Checks if supplied number of bytes is a valid number of elements for Java
     * byte array: converts it to int, if it is, or throws an exception if it is not.
     *
     * @param n number of bytes for byte array as long
     * @return number of bytes, converted to int
     */
    public static int toByteArrayLength(long n) {
        if (n > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Java byte arrays can be indexed only up to 31 bits, but " + n + " size was requested"
            );
        }
        if (n < 0) {
            throw new IllegalArgumentException(
                    "Byte array size can't be negative, but " + n + " size was requested"
            );
        }
        return (int) n;
    }

    //endregion

    //region Byte array processing

    /**
     * Performs a XOR processing with given data, XORing every byte of input with a single
     * given value.
     *
     * @param data data to process
     * @param key  value to XOR with
     * @return processed data
     */
    public static byte[] processXor(byte[] data, byte key) {
        int dataLen = data.length;
        byte[] r = new byte[dataLen];
        for (int i = 0; i < dataLen; i++)
            r[i] = (byte) (data[i] ^ key);
        return r;
    }

    /**
     * Performs a XOR processing with given data, XORing every byte of input with a key
     * array, repeating key array many times, if necessary (i.e. if data array is longer
     * than key array).
     *
     * @param data data to process
     * @param key  array of bytes to XOR with
     * @return processed data
     */
    public static byte[] processXor(byte[] data, byte[] key) {
        int dataLen = data.length;
        int valueLen = key.length;

        byte[] r = new byte[dataLen];
        int j = 0;
        for (int i = 0; i < dataLen; i++) {
            r[i] = (byte) (data[i] ^ key[j]);
            j = (j + 1) % valueLen;
        }
        return r;
    }

    /**
     * Performs a circular left rotation shift for a given buffer by a given amount of bits,
     * using groups of groupSize bytes each time. Right circular rotation should be performed
     * using this procedure with corrected amount.
     *
     * @param data      source data to process
     * @param amount    number of bits to shift by
     * @param groupSize number of bytes per group to shift
     * @return copy of source array with requested shift applied
     */
    public static byte[] processRotateLeft(byte[] data, int amount, int groupSize) {
        byte[] r = new byte[data.length];
        switch (groupSize) {
            case 1:
                for (int i = 0; i < data.length; i++) {
                    byte bits = data[i];
                    // http://stackoverflow.com/a/19181827/487064
                    r[i] = (byte) (((bits & 0xff) << amount) | ((bits & 0xff) >>> (8 - amount)));
                }
                break;
            default:
                throw new UnsupportedOperationException("unable to rotate group of " + groupSize + " bytes yet");
        }
        return r;
    }

    private final static int ZLIB_BUF_SIZE = 4096;

    /**
     * Performs an unpacking ("inflation") of zlib-compressed data with usual zlib headers.
     *
     * @param data data to unpack
     * @return unpacked data
     * @throws RuntimeException if data can't be decoded
     */
    public static byte[] processZlib(byte[] data) {
        Inflater ifl = new Inflater();
        ifl.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte buf[] = new byte[ZLIB_BUF_SIZE];
        while (!ifl.finished()) {
            try {
                int decBytes = ifl.inflate(buf);
                baos.write(buf, 0, decBytes);
            } catch (DataFormatException e) {
                throw new RuntimeException(e);
            }
        }
        ifl.end();
        return baos.toByteArray();
    }

    //endregion


    /**
     * Performs modulo operation between two integers: dividend `a`
     * and divisor `b`. Divisor `b` is expected to be positive. The
     * result is always 0 &lt;= x &lt;= b - 1.
     *
     * @param a dividend
     * @param b divisor
     * @return result
     */
    public static int mod(int a, int b) {
        if (b <= 0)
            throw new ArithmeticException("mod divisor <= 0");
        int r = a % b;
        if (r < 0)
            r += b;
        return r;
    }

    /**
     * Performs modulo operation between two integers: dividend `a`
     * and divisor `b`. Divisor `b` is expected to be positive. The
     * result is always 0 &lt;= x &lt;= b - 1.
     *
     * @param a dividend
     * @param b divisor
     * @return result
     */
    public static long mod(long a, long b) {
        if (b <= 0)
            throw new ArithmeticException("mod divisor <= 0");
        long r = a % b;
        if (r < 0)
            r += b;
        return r;
    }

    /**
     * Compares two byte arrays in lexicographical order. Makes extra effort
     * to compare bytes properly, as *unsigned* bytes, i.e. [0x90] would be
     * greater than [0x10].
     *
     * @param a first byte array to compare
     * @param b second byte array to compare
     * @return negative number if a &lt; b, 0 if a == b, positive number if a &gt; b
     * @see Comparable#compareTo(Object)
     */
    public static int byteArrayCompare(byte[] a, byte[] b) {
        if (a == b)
            return 0;
        int al = a.length;
        int bl = b.length;
        int minLen = Math.min(al, bl);
        for (int i = 0; i < minLen; i++) {
            int cmp = (a[i] & 0xff) - (b[i] & 0xff);
            if (cmp != 0)
                return cmp;
        }

        // Reached the end of at least one of the arrays
        if (al == bl) {
            return 0;
        } else {
            return al - bl;
        }
    }

    /**
     * Finds the minimal byte in a byte array, treating bytes as
     * unsigned values.
     *
     * @param b byte array to scan
     * @return minimal byte in byte array as integer
     */
    public static int byteArrayMin(byte[] b) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < b.length; i++) {
            int value = b[i] & 0xff;
            if (value < min)
                min = value;
        }
        return min;
    }

    /**
     * Finds the maximal byte in a byte array, treating bytes as
     * unsigned values.
     *
     * @param b byte array to scan
     * @return maximal byte in byte array as integer
     */
    public static int byteArrayMax(byte[] b) {
        int max = 0;
        for (int i = 0; i < b.length; i++) {
            int value = b[i] & 0xff;
            if (value > max)
                max = value;
        }
        return max;
    }
}
