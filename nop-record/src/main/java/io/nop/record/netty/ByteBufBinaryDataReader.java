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

package io.nop.record.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.nop.record.reader.IBinaryDataReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 基于Kaitai项目的ByteBufferKaitaiStream类修改。
 * An implementation of {@link IBinaryDataReader} backed by a {@link ByteBuffer}.
 * Any underlying implementation of ByteBuffer can be used, for example:
 * <ul>
 *     <li>ByteBuffer returned as result of {@link ByteBuffer#wrap}, wrapping
 *         a byte array into a buffer.</li>
 *     <li>{@link MappedByteBuffer} backed by {@link FileChannel}</li>
 * </ul>
 */
public class ByteBufBinaryDataReader implements IBinaryDataReader {
    private ByteBuf bb;
    private int bitsLeft;
    private long bits;


    /**
     * Initializes a stream that will get data from given byte array when read.
     * Internally, ByteBuffer wrapping given array will be used.
     *
     * @param arr byte array to read
     */
    public ByteBufBinaryDataReader(byte[] arr) {
        bb = Unpooled.wrappedBuffer(arr);
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
    public long getBits() {
        return bits;
    }

    @Override
    public void setBits(long bits) {
        this.bits = bits;
    }

    /**
     * Initializes a stream that will get data from given ByteBuffer when read.
     *
     * @param buffer ByteBuffer to read
     */
    public ByteBufBinaryDataReader(ByteBuf buffer) {
        bb = buffer;
    }

    /**
     * Provide a read-only version of the {@link ByteBuffer} backing the data of this instance.
     * <p>
     * This way one can access the underlying raw bytes associated with this structure, but it is
     * important to note that the caller needs to know what this raw data is: Depending on the
     * hierarchy of user types, how the format has been described and how a user type is actually
     * used, it might be that one accesses all data of some format or only a special substream
     * view of it. We can't know currently, so one needs to keep that in mind when authoring a KSY
     * and e.g. use substreams with user types whenever such a type most likely needs to access its
     * underlying raw data. Using a substream in KSY and directly passing some raw data to a user
     * type outside of normal KS parse order is equivalent and will provide the same results. If no
     * substream is used instead, the here provided data might differ depending on the context in
     * which the associated type was parsed, because the underlying {@link ByteBuffer} might
     * contain the data of all parent types and such as well and not only the one the caller is
     * actually interested in.
     * </p>
     * <p>
     * The returned {@link ByteBuffer} is always rewinded to position 0, because this stream was
     * most likely used to parse a type already, in which case the former position would have been
     * at the end of the buffer. Such a position doesn't help a common reading user much and that
     * fact can easily be forgotten, repositioning to another index than the start is pretty easy
     * as well. Rewinding/repositioning doesn't even harm performance in any way.
     * </p>
     *
     * @return read-only {@link ByteBuffer} to access raw data for the associated type.
     */
    public ByteBuf asRoBuffer() {
        ByteBuf retVal = this.bb.asReadOnly();
        retVal.resetReaderIndex();

        return retVal;
    }

    /**
     * Closes the stream safely. If there was an open file associated with it, closes that file.
     * For streams that were reading from in-memory array, does nothing.
     *
     * @implNote <p>
     * Unfortunately, there is no simple way to close memory-mapped ByteBuffer in
     * Java and unmap underlying file. As {@link MappedByteBuffer} documentation suggests,
     * "mapped byte buffer and the file mapping that it represents remain valid until the
     * buffer itself is garbage-collected". Thus, the best we can do is to delete all
     * references to it, which breaks all subsequent <code>read..</code> methods with
     * {@link NullPointerException}. Afterwards, a call to {@link System#gc()} will
     * typically release the mmap, if garbage collection will be triggered.
     * </p>
     * <p>
     * There is a <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4724038">
     * JDK-4724038 request for adding unmap method</a> filed at Java bugtracker since 2002,
     * but as of 2018, it is still unresolved.
     * </p>
     * <p>
     * A couple of unsafe approaches (such as using JNI, or using reflection to invoke JVM
     * internal APIs) have been suggested and used with some success, but these are either
     * unportable or dangerous (may crash JVM), so we're not using them in this general
     * purpose code.
     * </p>
     * <p>
     * For more examples and suggestions, see:
     * <a href="https://stackoverflow.com/q/2972986">How to unmap a file from memory mapped using FileChannel in java?</a>
     * </p>
     */
    @Override
    public void close() {
        ReferenceCountUtil.release(bb);
        bb = null;
    }

    //region Stream positioning

    @Override
    public boolean isEof() {
        return !(bb.isReadable() || bitsLeft > 0);
    }

    @Override
    public boolean hasRemainingBytes() {
        return bb.isReadable();
    }

    @Override
    public void seek(long newPos) {
        if (newPos > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Java ByteBuffer can't be seeked past Integer.MAX_VALUE");
        }
        bb.readerIndex((int) newPos);
        asRoBuffer().duplicate();
    }

    @Override
    public long pos() {
        return bb.readerIndex();
    }

    @Override
    public long available() {
        return bb.readableBytes();
    }

    //endregion

    //region Integer numbers

    //region Signed

    /**
     * Reads one signed 1-byte integer, returning it properly as Java's "byte" type.
     *
     * @return 1-byte integer read from a stream
     */
    @Override
    public byte readS1() {
        return bb.readByte();
    }

    //region Big-endian

    @Override
    public short readS2be() {
        //bb.order(ByteOrder.BIG_ENDIAN);
        return bb.readShort();
    }

    @Override
    public int readS4be() {
        //bb.order(ByteOrder.BIG_ENDIAN);
        return bb.readInt();
    }

    @Override
    public long readS8be() {
        //bb.order(ByteOrder.BIG_ENDIAN);
        return bb.readLong();
    }

    //endregion

    //region Little-endian

    @Override
    public short readS2le() {
        return bb.readShortLE();
    }

    @Override
    public int readS4le() {
        return bb.readIntLE();
    }

    @Override
    public long readS8le() {
        return bb.readLongLE();
    }

    //endregion

    //endregion

    //region Unsigned

    @Override
    public short readU1() {
        return bb.readUnsignedByte();
    }

    //region Big-endian

    @Override
    public int readU2be() {
        //bb.order(ByteOrder.BIG_ENDIAN);
        return bb.readUnsignedShort();
    }

    @Override
    public long readU4be() {
        // bb.order(ByteOrder.BIG_ENDIAN);
        return bb.readUnsignedInt();
    }

    //endregion

    //region Little-endian

    @Override
    public int readU2le() {
        //bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.readUnsignedShortLE();
    }

    @Override
    public long readU4le() {
        //bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.readUnsignedIntLE();
    }

    @Override
    public long readU8be() {
        return bb.readLong();
    }

    //endregion

    //endregion

    //endregion

    //region Floating point numbers

    //region Big-endian

    @Override
    public float readF4be() {
        return bb.readFloat();
    }

    @Override
    public double readF8be() {
        return bb.readDouble();
    }

    //endregion

    //region Little-endian

    @Override
    public float readF4le() {
        return bb.readFloatLE();
    }

    @Override
    public double readF8le() {
        return bb.readDoubleLE();
    }

    //endregion

    //endregion

    //region Byte arrays

    /**
     * Reads designated number of bytes from the stream.
     *
     * @param n number of bytes to read
     * @return read bytes as byte array
     */
    /**
     * Reads designated number of bytes from the stream.
     *
     * @param n number of bytes to read
     * @return read bytes as byte array
     */
    @Override
    public byte[] readBytes(int n) {
        byte[] buf = new byte[n];
        bb.readBytes(buf);
        return buf;
    }

    @Override
    public int read(byte[] data, int offset, int len) throws IOException {
        int length = Math.min(len, bb.readableBytes());
        bb.readBytes(data, offset, length);
        return length;
    }

    /**
     * Reads all the remaining bytes in a stream as byte array.
     *
     * @return all remaining bytes in a stream as byte array
     */
    @Override
    public byte[] readAvailableBytes() {
        return readBytes(bb.readableBytes());
    }

    @Override
    public byte[] readBytesTerm(byte term, boolean includeTerm, boolean consumeTerm, boolean eosError) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            if (!this.hasRemainingBytes()) {
                if (eosError) {
                    throw new RuntimeException("End of stream reached, but no terminator " + term + " found");
                } else {
                    return buf.toByteArray();
                }
            }
            byte c = readS1();
            if (c == term) {
                if (includeTerm)
                    buf.write(c);
                if (!consumeTerm)
                    seek(pos() - 1);
                return buf.toByteArray();
            }
            buf.write(c);
        }
    }

    //endregion

    @Override
    public IBinaryDataReader subInput(long n) {
        if (n > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Java ByteBuffer can't be limited beyond Integer.MAX_VALUE");
        }

        ByteBuf newBuffer = bb.slice();
        newBuffer.writerIndex((int) n);
        // 更新原始 ByteBuf 的读索引
        bb.readerIndex(bb.readerIndex() + (int) n);

        return new ByteBufBinaryDataReader(newBuffer);
    }

    @Override
    public String readString(int length, Charset charset) {
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        return bb.readCharSequence(length, charset).toString();
    }

    @Override
    public void alignToByte() {
        bitsLeft = 0;
        bits = 0;
    }

    @Override
    public void skip(long n) {
        seek(pos() + n);
    }

    @Override
    public void readFully(byte[] data, int offset, int len) {
        bb.readBytes(data, offset, len);
    }

    @Override
    public void reset() {
        seek(0);
    }

    @Override
    public IBinaryDataReader detach() {
        return new ByteBufBinaryDataReader(bb.duplicate());
    }

    @Override
    public IBinaryDataReader duplicate() {
        return new ByteBufBinaryDataReader(bb.duplicate());
    }

    @Override
    public boolean isDetached() {
        return true;
    }
}