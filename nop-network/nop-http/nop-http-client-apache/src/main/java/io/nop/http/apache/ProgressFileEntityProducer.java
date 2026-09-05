package io.nop.http.apache;

import io.nop.api.core.util.progress.IProgressListener;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * 流式文件请求体 producer：边发送边回调进度，不在内存整体缓冲文件。
 * 仿照 httpcore5 的 FileEntityProducer 实现 produce 状态机（writeData/streamEnd 是其包私有方法，
 * 无法跨包继承，故直接实现 AsyncEntityProducer）。
 */
public class ProgressFileEntityProducer implements AsyncEntityProducer {
    private static final int CHUNK = 8192;

    private final FileChannel file;
    private final long length;
    private final ContentType contentType;
    private final IProgressListener progressListener;
    private final Object message;

    private ByteBuffer buf;
    private long sent;

    public ProgressFileEntityProducer(File file, ContentType contentType, IProgressListener progressListener,
                                      Object message) throws IOException {
        this.file = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        this.length = file.length();
        this.contentType = contentType;
        this.progressListener = progressListener;
        this.message = message;
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public int available() {
        return CHUNK;
    }

    @Override
    public void produce(DataStreamChannel channel) throws IOException {
        while (true) {
            if (buf == null || !buf.hasRemaining()) {
                buf = ByteBuffer.allocate(CHUNK);
                int n = file.read(buf);
                if (n == 0)
                    return;
                if (n < 0) {
                    buf = null;
                    channel.endStream();
                    return;
                }
                buf.flip();
                sent += n;
                if (progressListener != null)
                    progressListener.onProgress(message, sent, length);
            }

            if (buf.hasRemaining()) {
                int written = channel.write(buf);
                if (written == 0)
                    return; // 通道背压，等待下一次 produce
            }
            if (!buf.hasRemaining()) {
                if (sent >= length) {
                    channel.endStream();
                    return;
                }
                buf = null;
            }
        }
    }

    @Override
    public void failed(Exception cause) {
    }

    @Override
    public void releaseResources() {
        try {
            file.close();
        } catch (IOException ignored) {
            // 传输已结束/失败，关闭失败仅造成只读句柄延迟回收
        }
    }
}
