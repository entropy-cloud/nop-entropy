/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.nop.commons.bytes;

import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

// Include code copy from kafka Utils

public class ByteBufferHelper {

    public static byte[] getBytes(ByteBuffer buffer) {
        ByteBuffer buf = buffer.duplicate();
        buf.flip(); // 读取范围变为 [0, original.position)
        byte[] dst = new byte[buf.remaining()];
        buf.get(dst);
        return dst;
    }

    /*
     * Read a size-delimited byte buffer starting at the given offset.
     *
     * @param buffer Buffer containing the size and data
     *
     * @param start Offset in the buffer to read from
     *
     * @return A slice of the buffer containing only the delimited data (excluding the size)
     */
    public static ByteBuffer sizeDelimited(ByteBuffer buffer, int start) {
        int size = buffer.getInt(start);
        if (size < 0) {
            return null;
        } else {
            ByteBuffer b = buffer.duplicate();
            b.position(start + 4);
            b = b.slice();
            b.limit(size);
            b.rewind();
            return b;
        }
    }

    /**
     * Read data from the channel to the given byte buffer until there are no bytes remaining in the buffer. If the end
     * of the file is reached while there are bytes remaining in the buffer, an EOFException is thrown.
     *
     * @param channel           File channel containing the data to read from
     * @param destinationBuffer The buffer into which bytes are to be transferred
     * @param position          The file position at which the transfer is to begin; it must be non-negative
     * @param description       A description of what is being read, this will be included in the EOFException if it is thrown
     * @throws IllegalArgumentException If position is negative
     * @throws EOFException             If the end of the file is reached while there are remaining bytes in the destination buffer
     * @throws IOException              If an I/O error occurs, see {@link FileChannel#read(ByteBuffer, long)} for details on the possible
     *                                  exceptions
     */
    public static void readFullyOrFail(FileChannel channel, ByteBuffer destinationBuffer, long position,
                                       String description) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("The file channel position cannot be negative, but it is " + position);
        }
        int expectedReadBytes = destinationBuffer.remaining();
        readFully(channel, destinationBuffer, position);
        if (destinationBuffer.hasRemaining()) {
            throw new EOFException(String.format(
                    "Failed to read `%s` from file channel `%s`. Expected to read %d bytes, "
                            + "but reached end of file after reading %d bytes. Started read from position %d.",
                    description, channel, expectedReadBytes, expectedReadBytes - destinationBuffer.remaining(),
                    position));
        }
    }

    /**
     * Read data from the channel to the given byte buffer until there are no bytes remaining in the buffer or the end
     * of the file has been reached.
     *
     * @param channel           File channel containing the data to read from
     * @param destinationBuffer The buffer into which bytes are to be transferred
     * @param position          The file position at which the transfer is to begin; it must be non-negative
     * @throws IllegalArgumentException If position is negative
     * @throws IOException              If an I/O error occurs, see {@link FileChannel#read(ByteBuffer, long)} for details on the possible
     *                                  exceptions
     */
    public static void readFully(FileChannel channel, ByteBuffer destinationBuffer, long position) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("The file channel position cannot be negative, but it is " + position);
        }
        long currentPosition = position;
        int bytesRead;
        do {
            bytesRead = channel.read(destinationBuffer, currentPosition);
            currentPosition += bytesRead;
        } while (bytesRead != -1 && destinationBuffer.hasRemaining());
    }

    /**
     * Read data from the input stream to the given byte buffer until there are no bytes remaining in the buffer or the
     * end of the stream has been reached.
     *
     * @param inputStream       Input stream to read from
     * @param destinationBuffer The buffer into which bytes are to be transferred (it must be backed by an array)
     * @throws IOException If an I/O error occurs
     */
    public static final void readFully(InputStream inputStream, ByteBuffer destinationBuffer) throws IOException {
        if (!destinationBuffer.hasArray())
            throw new IllegalArgumentException("destinationBuffer must be backed by an array");
        int initialOffset = destinationBuffer.arrayOffset() + destinationBuffer.position();
        byte[] array = destinationBuffer.array();
        int length = destinationBuffer.remaining();
        int totalBytesRead = 0;
        do {
            int bytesRead = inputStream.read(array, initialOffset + totalBytesRead, length - totalBytesRead);
            if (bytesRead == -1)
                break;
            totalBytesRead += bytesRead;
        } while (length > totalBytesRead);
        destinationBuffer.position(destinationBuffer.position() + totalBytesRead);
    }

    public static void writeToOutput(DataOutput out, ByteBuffer buffer) throws IOException {
        int length = buffer.remaining(); // 记录要写入的长度
        writeToOutput0(out, buffer, length);
        buffer.position(buffer.position() + length); // 更新 position
    }

    public static void writeToOutput(DataOutput out, ByteBuffer buffer, int length) throws IOException {
        writeToOutput0(out, buffer, length);
        buffer.position(buffer.position() + length);
    }

    /**
     * Write the contents of a buffer to an output stream. The bytes are copied from the current position in the buffer.
     *
     * @param out    The output to write to
     * @param buffer The buffer to write from
     * @param length The number of bytes to write
     * @throws IOException For any errors writing to the output
     */
    public static void writeToOutput0(DataOutput out, ByteBuffer buffer, int length) throws IOException {
        if (buffer.hasArray()) {
            out.write(buffer.array(), buffer.position() + buffer.arrayOffset(), length);
        } else {
            ByteBuffer slice = buffer.slice().limit(length);
            byte[] temp = new byte[Math.min(length, 8192)];
            while (slice.hasRemaining()) {
                int chunkSize = Math.min(slice.remaining(), temp.length);
                slice.get(temp, 0, chunkSize);
                out.write(temp, 0, chunkSize);
            }
        }
    }

    public static void writeToStream(OutputStream out, ByteBuffer buffer) throws IOException {
        writeToStream(out, buffer, buffer.remaining());
    }

    public static void writeToStream(OutputStream out, ByteBuffer buffer, int length) throws IOException {
        writeToStream0(out, buffer, length);
        buffer.position(buffer.position() + length);
    }

    public static void writeToStream0(OutputStream out, ByteBuffer buffer, int length) throws IOException {
        if (buffer.hasArray()) {
            // 直接访问底层数组
            out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), length);
        } else {
            // 对于非数组支持的缓冲区，使用临时数组批量写入
            ByteBuffer slice = buffer.slice().limit(length);

            // INTENTIONAL: Cast OutputStream to WritableByteChannel for performance.
            // This is a common optimization in high-performance I/O frameworks. If the 'out'
            // instance is a custom wrapper that implements both interfaces (e.g., wrapping a SocketChannel),
            // this allows us to write a DirectByteBuffer directly, avoiding an intermediate
            // memory copy to a temporary byte array and enabling potential zero-copy operations.
            if (out instanceof WritableByteChannel) {
                ((WritableByteChannel) out).write(slice);
            } else {
                byte[] temp = new byte[Math.min(length, 8192)];
                while (slice.hasRemaining()) {
                    int chunkSize = Math.min(slice.remaining(), temp.length);
                    slice.get(temp, 0, chunkSize);
                    out.write(temp, 0, chunkSize);
                }
            }
        }
    }

    // copy from ElasticSearch Channels.java

    /**
     * Writes part of a byte array to a {@link java.nio.channels.WritableByteChannel}
     *
     * @param source  byte array to copy from
     * @param offset  start copying from this offset
     * @param length  how many bytes to copy
     * @param channel target WritableByteChannel
     */
    public static void writeToChannel(WritableByteChannel channel, byte[] source, int offset, int length)
            throws IOException {
        int remaining = length;
        int pos = offset;
        while (remaining > 0) {
            int toWrite = Math.min(remaining, WRITE_CHUNK_SIZE);
            ByteBuffer buf = ByteBuffer.wrap(source, pos, toWrite);
            while (buf.hasRemaining()) {
                channel.write(buf);
            }
            pos += toWrite;
            remaining -= toWrite;
        }
    }

    /**
     * The maximum chunk size for writes in bytes. JDK内部对于过大的chunk处理不优化
     */
    private static final int WRITE_CHUNK_SIZE = 8192;

    /**
     * copy from ElasticSearch Channels
     * <p>
     * Writes a {@link java.nio.ByteBuffer} to a {@link java.nio.channels.WritableByteChannel}
     *
     * @param byteBuffer source buffer
     * @param channel    channel to write to
     */
    public static void writeFully(WritableByteChannel channel, ByteBuffer byteBuffer) throws IOException {
        if (byteBuffer.isDirect() || byteBuffer.remaining() <= WRITE_CHUNK_SIZE) {
            while (byteBuffer.hasRemaining()) {
                channel.write(byteBuffer);
            }
        } else {
            // duplicate the buffer in order to be able to change the limit
            ByteBuffer tmpBuffer = byteBuffer.duplicate();
            try {
                while (byteBuffer.hasRemaining()) {
                    tmpBuffer.limit(Math.min(byteBuffer.limit(), tmpBuffer.position() + WRITE_CHUNK_SIZE));
                    while (tmpBuffer.hasRemaining()) {
                        channel.write(tmpBuffer);
                    }
                    byteBuffer.position(tmpBuffer.position());
                }
            } finally {
                // make sure we update byteBuffer to indicate how far we came..
                byteBuffer.position(tmpBuffer.position());
            }
        }
    }
}