/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.io.stream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * 由FileChannel创建的输入流
 *
 * @author WangXiaoJun
 */
public class ChannelInputStream extends InputStream {
    private final ReadableByteChannel channel;
    private final SeekableByteChannel seek;
    private final boolean disableClose;

    /**
     * 构建ChannelInputStream
     *
     * @param channel FileChannel
     */
    public ChannelInputStream(ReadableByteChannel channel, boolean disableClose) {
        this.channel = channel;
        this.seek = channel instanceof SeekableByteChannel ? (SeekableByteChannel) channel : null;
        this.disableClose = disableClose;
    }

    public int read() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        int n = channel.read(buf);
        if (n <= 0)
            return -1;
        buf.position(0);
        return buf.get() & 0xFF;
    }

    /**
     * b的长度必须和缓冲区长度相同
     *
     * @param b byte[]
     * @return int
     * @throws IOException
     */
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * len必须和缓冲区长度相同
     *
     * @param b   byte[]
     * @param off int
     * @param len int
     * @return int
     * @throws IOException
     */
    public int read(byte[] b, int off, int len) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(len);
        int n = channel.read(buffer);
        if (n <= 0) {
            return n;
        }

        buffer.position(0);
        buffer.get(b, off, n);
        return n;
    }

    /**
     * 跳过指定的字节
     *
     * @param n 字节数
     */
    public long skip(long n) throws IOException {
        if (seek != null) {
            seek.position(seek.position() + n);
            return n;
        } else {
            return 0;
        }
    }

    /**
     * 返回可用的字节数
     */
    public int available() throws IOException {
        if (seek != null) {
            return (int) (seek.size() - seek.position());
        } else {
            return -1;
        }
    }

    /**
     * 关闭输入流
     */
    public void close() throws IOException {
        if (!disableClose)
            channel.close();
    }
}
