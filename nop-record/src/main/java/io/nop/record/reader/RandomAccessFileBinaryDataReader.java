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

import static io.nop.record.reader.BinaryReaderHelper.toByteArrayLength;

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

    public RandomAccessFileBinaryDataReader(File file) throws IOException {
        raf = new RandomAccessFile(file, "r");
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
        return !(raf.getFilePointer() < raf.length() || bitsLeft > 0);
    }

    @Override
    public void seek(long newPos) throws IOException {
        raf.seek(newPos);
    }

    @Override
    public boolean hasRemainingBytes() throws IOException {
        return (raf.getFilePointer() < raf.length());
    }

    @Override
    public long pos() {
        try {
            return raf.getFilePointer();
        } catch (IOException e) {
            throw wrapErr(e);
        }
    }

    public long size() throws IOException {
        return raf.length();
    }

    public long available() throws IOException {
        return size() - pos();
    }

    @Override
    public int read(byte[] data, int offset, int len) throws IOException {
        return raf.read(data, offset, len);
    }

    @Override
    public int read(byte[] data) throws IOException {
        return raf.read(data);
    }

    @Override
    public IBinaryDataReader subInput(long n) throws IOException {
        // This implementation mirrors what ksc was doing up to v0.10, and the fallback that
        // it is still doing in case something non-trivial has to happen with the byte contents.
        //
        // Given that RandomAccessFile-based stream is not really efficient anyway, this seems
        // to be a reasonable fallback without resorting to a special limiting implementation.
        //
        // If and when somebody will come up with a reason why substreams have to implemented
        // for RAF, feel free to contribute relevant implementation with some rationale (e.g. a
        // benchmark).

        return new ByteBufferBinaryDataReader(readBytes(toByteArrayLength(n)));
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