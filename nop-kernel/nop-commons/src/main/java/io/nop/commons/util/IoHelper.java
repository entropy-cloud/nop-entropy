/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.env.PlatformEnv;
import io.nop.commons.io.serialize.IByteArraySerializer;
import io.nop.commons.io.serialize.IStreamSerializer;
import io.nop.commons.io.serialize.JavaSerializer;
import io.nop.commons.io.stream.BoundedInputStream;
import io.nop.commons.io.stream.IBufferedStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;

import static io.nop.commons.CommonConfigs.CFG_IO_DEFAULT_BUF_SIZE;
import static io.nop.commons.CommonConstants.ENCODING_UTF8;
import static io.nop.commons.CommonErrors.ARG_EXPECTED;
import static io.nop.commons.CommonErrors.ERR_IO_NOT_FIND_EXPECTED_BYTE;
import static io.nop.commons.CommonErrors.ERR_IO_UNEXPECTED_EOF;

public class IoHelper {
    static final Logger LOG = LoggerFactory.getLogger(IoHelper.class);

    static IStreamSerializer s_streamSerializer = JavaSerializer.INSTANCE;
    static IByteArraySerializer s_byteArraySerializer = JavaSerializer.INSTANCE;

    public static IStreamSerializer streamSerializer() {
        return s_streamSerializer;
    }

    public static void registerStreamSerializer(IStreamSerializer serializer) {
        s_streamSerializer = serializer;
    }

    public static IByteArraySerializer byteArraySerializer() {
        return s_byteArraySerializer;
    }

    public static void registerByteArraySerializer(IByteArraySerializer serializer) {
        s_byteArraySerializer = serializer;
    }

    public static void serializeToStream(Object o, OutputStream os) {
        s_streamSerializer.serializeToStream(o, os);
    }

    public static Object deserializeFromStream(InputStream is) {
        return s_streamSerializer.deserializeFromStream(is);
    }

    public static byte[] serializeToByteArray(Object o) {
        return s_byteArraySerializer.serializeToByteArray(o);
    }

    public static Object deserializeFromByteArray(byte[] data) {
        return s_byteArraySerializer.deserializeFromByteArray(data);
    }

    public static boolean isBufferedInputStream(InputStream is) {
        return is instanceof BufferedInputStream || is instanceof ByteArrayInputStream || is instanceof IBufferedStream;
    }

    public static InputStream toBufferedInputStream(InputStream is) {
        if (!isBufferedInputStream(is)) {
            return new BufferedInputStream(is);
        }
        return is;
    }

    public static boolean isBufferedOutputStream(OutputStream out) {
        return out instanceof BufferedOutputStream || out instanceof ByteArrayOutputStream
                || out instanceof IBufferedStream;
    }

    public static OutputStream toBufferedOutputStream(OutputStream out) {
        if (!isBufferedOutputStream(out)) {
            return new BufferedOutputStream(out);
        }
        return out;
    }

    public static boolean isBufferedWriter(Writer out) {
        return out instanceof BufferedWriter || out instanceof StringWriter;
    }

    public static Writer toBufferedWriter(Writer out) {
        if (!isBufferedWriter(out))
            return new BufferedWriter(out);
        return out;
    }

    public static boolean isBufferedReader(Reader in) {
        return in instanceof BufferedReader || in instanceof StringReader || in instanceof IBufferedStream;
    }

    public static Object serializeClone(Object o) {
        if (o == null)
            return o;
        return deserializeFromByteArray(serializeToByteArray(o));
    }

    public static void safeClose(Object o) {
        if (o instanceof AutoCloseable) {
            try {
                ((AutoCloseable) o).close();
            } catch (Exception e) {
                LOG.info("nop.err.commons.util.close:obj={}", o, e);
            }
        }
    }

    public static void safeCloseObject(AutoCloseable o) {
        if (o != null) {
            try {
                o.close();
            } catch (Exception e) {
                LOG.info("nop.err.commons.util.close:obj={}", o, e);
            }
        }
    }

    public static void safeCloseAll(Collection<?> c) {
        if (c != null) {
            for (Object o : c) {
                safeClose(o);
            }
        }
    }

    public static void write(OutputStream os, byte[] data, IStepProgressListener listener) throws IOException {
        if (listener != null)
            listener.begin();
        os.write(data);
        if (listener != null) {
            listener.onStep(data.length);
            listener.end();
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), null);
    }

    public static void copy(InputStream is, OutputStream os, int bufSize, IStepProgressListener listener)
            throws IOException {
        // if (is instanceof FileInputStream) {
        // if (listener != null)
        // listener.begin();
        //
        // long step = fileTransferTo(getInputFileChannel(is), Channels.newChannel(os), 0, Long.MAX_VALUE);
        //
        // if (listener != null) {
        // listener.onStep(step);
        // listener.end();
        // }
        // return;
        // }
        //
        // if (os instanceof FileOutputStream) {
        // if (listener != null)
        // listener.begin();
        //
        // long step = fileTransferFrom(getOutputFileChannel(os), Channels.newChannel(is), 0, Long.MAX_VALUE);
        //
        // if (listener != null) {
        // listener.onStep(step);
        // listener.end();
        // }
        // return;
        // }

        if (listener != null)
            listener.begin();

        final byte[] buf = new byte[bufSize];
        int n = 0;
        while (true) {
            n = is.read(buf);
            if (n < 0) {
                if (listener != null)
                    listener.end();
                return;
            }
            os.write(buf, 0, n);
            if (listener != null) {
                listener.onStep(n);
            }
        }
    }

    public static void copy(Reader is, Writer os) throws IOException {
        copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), null);
    }

    public static void copy(Reader is, Writer os, int bufSize, IStepProgressListener listener) throws IOException {
        final char[] buf = new char[bufSize];
        if (listener != null) {
            listener.begin();
        }
        int n = 0;
        while (true) {
            n = is.read(buf);
            if (n < 0) {
                if (listener != null)
                    listener.end();
                return;
            }
            os.write(buf, 0, n);
            if (listener != null) {
                listener.onStep(n);
            }
        }
    }

    public static String readText(Reader is) throws IOException {
        StringWriter out = new StringWriter();
        copy(is, out, CFG_IO_DEFAULT_BUF_SIZE.get(), null);
        return out.toString();
    }

    public static byte[] readBytes(InputStream is) throws IOException {
        if (PlatformEnv.javaVersion() >= 9) {
            return is.readAllBytes();
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), null);
        return os.toByteArray();
    }

    public static String readText(InputStream is, String encoding) throws IOException {
        return readText(toReader(is, encoding));
    }

    public static Reader toReader(InputStream is, String encoding) throws IOException {
        if (encoding == null)
            encoding = ENCODING_UTF8;

        return new InputStreamReader(is, encoding);
    }

    public static Writer toWriter(OutputStream os, String encoding) throws IOException {
        if (encoding == null)
            encoding = ENCODING_UTF8;
        return new OutputStreamWriter(os, encoding);
    }

    public static int readTill(InputStream is, byte end, byte[] data, int off) throws IOException {
        int len = data.length;
        do {
            int c = is.read();
            if (c < 0)
                throw new NopException(ERR_IO_UNEXPECTED_EOF);
            data[off] = (byte) c;
            if (c == end)
                return off;
            off++;
            if (off >= len)
                throw new NopException(ERR_IO_NOT_FIND_EXPECTED_BYTE).param(ARG_EXPECTED, end);
        } while (true);
    }

    public static void readFully(InputStream is, byte[] data, int off, int len) throws IOException {
        int n = read(is, data, off, len);
        if (n != len)
            throw new NopException(ERR_IO_UNEXPECTED_EOF);
    }

    public static int read(InputStream is, byte[] data, int off, int len) throws IOException {
        int nRead = 0;
        do {
            int n = is.read(data, off, len);
            if (n < 0)
                return nRead > 0 ? nRead : -1;
            nRead += n;
            len -= n;
            if (len <= 0)
                break;
            off += n;
        } while (true);
        return nRead;
    }

    public static int read(Reader is, char[] data, int off, int len) throws IOException {
        int nRead = 0;
        do {
            int n = is.read(data, off, len);
            if (n < 0)
                return nRead > 0 ? nRead : -1;
            nRead += n;
            len -= n;
            if (len <= 0)
                break;
            off += n;
        } while (true);
        return nRead;
    }

    public static void readFully(InputStream is, byte[] data) throws IOException {
        readFully(is, data, 0, data.length);
    }

    /**
     * 根据BOM头来判定文件的编码类型。如果成功解析得到encoding, 则跳过BOM头，否则恢复stream原先的位置。
     *
     * @param is
     * @return
     */
    public static String getEncodingFromBOM(InputStream is) throws IOException {
        is.mark(4);
        byte[] bom = new byte[4];
        readFully(is, bom);

        String encoding = null;
        if (bom[0] == 0x00 && bom[1] == 0x00 && bom[2] == (byte) 0xFE && bom[3] == (byte) 0xFF || // BE
                bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE && bom[2] == 0x00 && bom[3] == 0x00) { // LE
            encoding = "UTF-32"; // and I hope it's on your system
        } else if (bom[0] == (byte) 0xFE && bom[1] == (byte) 0xFF || // BE
                bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE) {
            encoding = "UTF-16"; // in all Javas
            is.reset();
            is.read(bom, 0, 2);
        } else if (bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF) {
            encoding = "UTF-8"; // in all Javas
            is.reset();
            is.read(bom, 0, 3);
        } else {
            is.reset();
        }
        return encoding;
    }

    /**
     * Peeks at the first N bytes of the stream. Returns those bytes, but with the stream unaffected. Requires a stream
     * that supports mark/reset, or a PushbackInputStream. If the stream has &gt;0 but &lt;N bytes, remaining bytes will
     * be zero.
     */
    public static byte[] peekFirstNBytes(InputStream stream, int limit) throws IOException {
        stream.mark(limit);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(limit);
        copy(new BoundedInputStream(stream, limit), bos);

        int readBytes = bos.size();
        if (readBytes == 0) {
            throw new IOException();
        }

        if (readBytes < limit) {
            bos.write(new byte[limit - readBytes]);
        }
        byte[] peekedBytes = bos.toByteArray();
        if (stream instanceof PushbackInputStream) {
            PushbackInputStream pin = (PushbackInputStream) stream;
            pin.unread(peekedBytes, 0, readBytes);
        } else {
            stream.reset();
        }

        return peekedBytes;
    }

}