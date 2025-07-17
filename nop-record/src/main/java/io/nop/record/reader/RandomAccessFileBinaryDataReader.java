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

import io.nop.api.core.exceptions.NopException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * An implementation of {@link IBinaryDataReader} backed by a {@link RandomAccessFile}.
 * <p>
 * Allows reading from local files. Generally, one would want to use
 * {@link ByteBufferBinaryDataReader} instead, as it most likely would be faster,
 * but there are two situations when one should consider this one instead:
 *
 * <ul>
 * <li>Processing many small files. Every ByteBuffer invocation requires a mmap
 * call, which can be relatively expensive (per file).</li>
 * <li>Accessing extra-long files (&gt;31 bits positioning). Unfortunately, Java's
 * implementation of mmap uses ByteBuffer, which is not addressable beyond 31 bit
 * offsets, even if you use a 64-bit platform.</li>
 * </ul>
 */
public class RandomAccessFileBinaryDataReader implements IBinaryDataReader {
    protected final RandomAccessFile raf;
    private long bits;
    private int bitsLeft;
    private long pos = 0;
    private long size;

    public RandomAccessFileBinaryDataReader(File file) {
        try {
            raf = new RandomAccessFile(file, "r");
            size = raf.length();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public long getBits() {
        return bits;
    }

    @Override
    public void setBits(long bits) {
        this.bits = bits;
    }

    @Override
    public int getBitsLeft() {
        return bitsLeft;
    }

    @Override
    public void setBitsLeft(int bitsLeft) {
        this.bitsLeft = bitsLeft;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    protected RuntimeException wrapErr(IOException e) {
        throw NopException.adapt(e);
    }

    @Override
    public boolean isEof() throws IOException {
        return !(pos < size() || bitsLeft > 0);
    }

    @Override
    public void seek(long newPos) throws IOException {
        raf.seek(newPos);
        this.pos = raf.getFilePointer();
    }

    @Override
    public boolean hasRemainingBytes() throws IOException {
        return (pos < size());
    }

    @Override
    public long pos() {
        return pos;
    }

    public long size() throws IOException {
        return size;
    }

    public long available() throws IOException {
        return size() - pos();
    }

    @Override
    public int read(byte[] data, int offset, int len) throws IOException {
        int n = raf.read(data, offset, len);
        if (n > 0)
            pos += n;
        return n;
    }

    @Override
    public int read(byte[] data) throws IOException {
        int n = raf.read(data);
        if (n > 0)
            pos += n;
        return n;
    }

    @Override
    public int read() throws IOException {
        int c = raf.read();
        if (c >= 0)
            pos++;
        return c;
    }

    @Override
    public IBinaryDataReader subInput(long maxLength) throws IOException {
        return new SubBinaryDataReader(this, maxLength);
    }

    //region Helper methods

    //endregion


    @Override
    public void alignToByte() {
        this.bits = 0;
        this.bitsLeft = 0;
    }

    @Override
    public void skip(long n) throws IOException {
        seek(pos() + n);
    }

    @Override
    public void reset() throws IOException {
        seek(0);
    }

    @Override
    public IBinaryDataReader detach() throws IOException {
        return subInput(size());
    }

    @Override
    public IBinaryDataReader duplicate() throws IOException {
        return subInput(size());
    }

    @Override
    public boolean isDetached() {
        return false;
    }
}