/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BinaryCommand {
    public static final byte[] EMPTY_BYTES = new byte[0];

    private final short masks;
    private final short version;
    private final short cmd;
    private final short flags;
    private final byte[] data;

    public BinaryCommand(short masks, short version, short cmd, short flags, byte[] data) {
        this.masks = masks;
        this.version = version;
        this.cmd = cmd;
        this.flags = flags;
        this.data = data == null || data.length == 0 ? EMPTY_BYTES : data;
    }

    public BinaryCommand(short masks, short version) {
        this(masks, version, (short) 0, (short) 0, EMPTY_BYTES);
    }

    public BinaryCommand(short masks, short version, short cmd, short flags, String data) {
        this(masks, version, cmd, flags, data.getBytes(StandardCharsets.UTF_8));
    }

    public static BinaryCommand newEmptyCommand(short masks, short version) {
        return new BinaryCommand(masks, version, (short) 0, (short) 0, EMPTY_BYTES);
    }

    public static BinaryCommand newCommand(AbstractSocketConfig config, short cmd, short flags, String data) {
        byte[] bytes = data == null || data.length() <= 0 ? null : data.getBytes(StandardCharsets.UTF_8);
        return new BinaryCommand(config.getMasks(), config.getVersion(), cmd, flags, bytes);
    }

    public boolean isEmptyCommand() {
        return cmd == 0 && flags == 0 && data.length == 0;
    }

    public short getMasks() {
        return masks;
    }

    public short getFlags() {
        return flags;
    }

    public short getVersion() {
        return version;
    }

    public short getCmd() {
        return cmd;
    }

    public byte[] getData() {
        return data;
    }

    public String getDataAsString() {
        if (data == null || data.length <= 0)
            return "";
        return new String(data, StandardCharsets.UTF_8);
    }

    public int getPacketLength() {
        return 4 + getLength();
    }

    public int getLength() {
        if (isEmptyCommand())
            return 4;
        return 8 + data.length;
    }

    public static void writeTo(BinaryCommand command, ByteBuffer buffer) {
        buffer.putShort(command.getMasks());
        buffer.putShort(command.getVersion());

        if (!command.isEmptyCommand()) {
            buffer.putShort(command.getCmd());
            buffer.putShort(command.getFlags());
            buffer.put(command.getData());
        }
    }

    public ByteBuffer toPacket() {
        int len = getLength();
        ByteBuffer buf = ByteBuffer.allocate(4 + len);
        buf.putInt(len);
        writeTo(this, buf);
        buf.rewind();
        return buf;
    }

    public static BinaryCommand readFrom(ByteBuffer buffer) {
        short masks = buffer.getShort();
        short version = buffer.getShort();

        if (buffer.remaining() == 0) {
            return new BinaryCommand(masks, version, (short) 0, (short) 0, EMPTY_BYTES);
        }

        short cmd = buffer.getShort();
        short flags = buffer.getShort();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return new BinaryCommand(masks, version, cmd, flags, data);
    }

    public static void writePacketToStream(BinaryCommand command, OutputStream os) throws IOException {
        ByteBuffer buf = command.toPacket();
        os.write(buf.array(), buf.arrayOffset(), buf.remaining());
    }

    public static BinaryCommand readPacketFromStream(InputStream is, short masks, int minLen, int maxLen,
                                                     ByteBuffer buf) throws IOException {
        buf.rewind();
        if (!readFully(is, buf, 8)) {
            if (buf.position() == 0) {
                return null;
            }
            throw new IOException("nop.err.socket.read-no-enough-data");
        }
        buf.limit(8);
        short readMasks = buf.getShort(4);
        if (readMasks != masks) {
            throw new IOException("nop.err.socket.read-packet-masks-mismatch:masks=" + readMasks);
        }
        short version = buf.getShort(6);

        int len = buf.getInt(0);
        if (len < minLen)
            throw new IOException("nop.err.socket.packet-is-too-small:len=" + len);
        if (len > maxLen)
            throw new IOException("nop.err.socket.packet-is-too-large:len=" + len);

        ByteBuffer dataBuf = ByteBuffer.allocate(len);
        dataBuf.putShort(masks);
        dataBuf.putShort(version);

        if (len > 4) {
            if (!readFully(is, dataBuf.array(), 4, len - 4)) {
                throw new IOException("nop.err.socket.packet-is-incomplete");
            }
        }
        dataBuf.rewind();
        return readFrom(dataBuf);
    }

    private static boolean readFully(InputStream is, byte[] buf, int offset, int n) throws IOException {
        int toRead = n;
        do {
            int nRead = is.read(buf, offset, toRead);
            if (nRead <= 0)
                return false;

            toRead -= nRead;
            offset += nRead;
        } while (toRead > 0);
        return true;
    }

    public static boolean readFully(InputStream is, ByteBuffer buf, int n) throws IOException {
        int toRead = n;
        byte[] array = buf.array();
        int offset = buf.arrayOffset();
        int pos = buf.position();
        do {
            int nRead = is.read(array, offset + pos, toRead);
            if (nRead <= 0) {
                buf.position(pos);
                return false;
            }

            toRead -= nRead;
            pos += nRead;
        } while (toRead > 0);
        buf.position(pos);
        return true;
    }
}