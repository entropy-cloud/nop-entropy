package io.nop.record.writer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class StreamBinaryDataWriter implements IBinaryDataWriter {
    private final OutputStream out;

    public StreamBinaryDataWriter(OutputStream out) {
        this.out = out;
    }

    @Override
    public void writeBytes(byte[] bytes) throws IOException {
        out.write(bytes);
    }

    @Override
    public void writeBytesPart(byte[] str, int start, int end) throws IOException {
        out.write(str, start, end - start);
    }

    @Override
    public void writeByteBuffer(ByteBuffer buf) throws IOException {
        out.write(buf.array(), buf.position(), buf.remaining());
    }

    @Override
    public void writeS1(byte c) throws IOException {
        out.write(c);
    }

    @Override
    public void writeU1(int c) throws IOException {
        out.write(c);
    }

    @Override
    public void writeS2be(short c) throws IOException {
        out.write((byte) (c >> 8));
        out.write((byte) c);
    }

    @Override
    public void writeS2le(short c) throws IOException {
        out.write((byte) c);
        out.write((byte) (c >> 8));
    }

    @Override
    public void writeU2be(int c) throws IOException {
        out.write((byte) (c >> 8));
        out.write((byte) c);
    }

    @Override
    public void writeU2le(int c) throws IOException {
        out.write((byte) c);
        out.write((byte) (c >> 8));
    }

    @Override
    public void writeS4be(int c) throws IOException {
        out.write((byte) (c >> 24));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 8));
        out.write((byte) c);
    }

    @Override
    public void writeS4le(int c) throws IOException {
        out.write((byte) c);
        out.write((byte) (c >> 8));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 24));
    }

    @Override
    public void writeU4be(long c) throws IOException {
        out.write((byte) (c >> 24));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 8));
        out.write((byte) c);
    }

    @Override
    public void writeU4le(long c) throws IOException {
        out.write((byte) c);
        out.write((byte) (c >> 8));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 24));
    }

    @Override
    public void writeS8be(long c) throws IOException {
        out.write((byte) (c >> 56));
        out.write((byte) (c >> 48));
        out.write((byte) (c >> 40));
        out.write((byte) (c >> 32));
        out.write((byte) (c >> 24));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 8));
        out.write((byte) c);
    }

    @Override
    public void writeS8le(long c) throws IOException {
        out.write((byte) c);
        out.write((byte) (c >> 8));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 24));
        out.write((byte) (c >> 32));
        out.write((byte) (c >> 40));
        out.write((byte) (c >> 48));
        out.write((byte) (c >> 56));
    }

    @Override
    public void writeU8be(long c) throws IOException {
        out.write((byte) (c >> 56));
        out.write((byte) (c >> 48));
        out.write((byte) (c >> 40));
        out.write((byte) (c >> 32));
        out.write((byte) (c >> 24));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 8));
        out.write((byte) c);
    }

    @Override
    public void writeU8le(long c) throws IOException {
        out.write((byte) c);
        out.write((byte) (c >> 8));
        out.write((byte) (c >> 16));
        out.write((byte) (c >> 24));
        out.write((byte) (c >> 32));
        out.write((byte) (c >> 40));
        out.write((byte) (c >> 48));
        out.write((byte) (c >> 56));
    }

    @Override
    public void writeF4le(float c) throws IOException {
        writeU4le(Float.floatToRawIntBits(c));
    }

    @Override
    public void writeF4be(float c) throws IOException {
        writeU4be(Float.floatToRawIntBits(c));
    }

    @Override
    public void writeF8be(double c) throws IOException {
        writeU8be(Double.doubleToRawLongBits(c));
    }

    @Override
    public void writeF8le(double c) throws IOException {
        writeU8le(Double.doubleToRawLongBits(c));
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }
}
