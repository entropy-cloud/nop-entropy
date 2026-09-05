package io.nop.http.apache;

import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.utils.FileDownloadSink;
import io.nop.http.api.utils.FileTransferHelper;
import io.nop.http.api.utils.HttpHelper;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * 断点续传下载消费器：落盘/摘要/进度委托 FileDownloadSink，校验与 .part 改名由 downloadAsync 统一执行
 */
public class DownloadResponseConsumer extends AbstractStreamResponseConsumer<DownloadTransferResult, DownloadTransferResult> {

    public DownloadResponseConsumer(IHttpOutputFile targetFile, long offset, DownloadOptions options,
                                    Object progressMessage) {
        super(new EntityConsumerSupplier(targetFile, options, progressMessage));
    }

    static class EntityConsumerSupplier implements Supplier<AsyncEntityConsumer<DownloadTransferResult>> {
        private final IHttpOutputFile targetFile;
        private final DownloadOptions options;
        private final Object progressMessage;

        EntityConsumerSupplier(IHttpOutputFile targetFile, DownloadOptions options, Object progressMessage) {
            this.targetFile = targetFile;
            this.options = options;
            this.progressMessage = progressMessage;
        }

        @Override
        public AsyncEntityConsumer<DownloadTransferResult> get() {
            return new EntityConsumer(targetFile, options, progressMessage);
        }
    }

    static class EntityConsumer extends AbstractBinAsyncEntityConsumer<DownloadTransferResult>
            implements IStreamResponseConsumer {
        private final FileDownloadSink sink;

        private final DownloadTransferResult result = new DownloadTransferResult();
        private boolean ignoreBody;

        EntityConsumer(IHttpOutputFile targetFile, DownloadOptions options, Object progressMessage) {
            this.sink = new FileDownloadSink(targetFile, options, progressMessage);
        }

        @Override
        public void onStreamBegin(HttpResponse response, ContentType contentType) {
            result.setStatus(response.getCode());
            result.setHeaders(ApacheHttpClientHelper.getHeaders(response.getHeaders()));
            // 非 2xx 与 416 不落盘：416 由 downloadAsync 删除 .part 后整体重传
            this.ignoreBody = !HttpHelper.isOk(response.getCode()) || response.getCode() == 416;
        }

        @Override
        protected void streamStart(final ContentType contentType) throws IOException {
            if (ignoreBody)
                return;
            sink.begin(result.getStatus(), headerIgnoreCase("content-range"), parseContentLength());
        }

        private String headerIgnoreCase(String name) {
            Map<String, String> headers = result.getHeaders();
            if (headers == null)
                return null;
            String value = headers.get(name);
            if (value != null)
                return value;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name))
                    return entry.getValue();
            }
            return null;
        }

        private long parseContentLength() {
            String len = headerIgnoreCase("content-length");
            if (len == null)
                return -1;
            try {
                return Long.parseLong(len.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        @Override
        protected int capacityIncrement() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
            if (src == null || ignoreBody)
                return;
            byte[] bytes = new byte[src.remaining()];
            src.get(bytes);
            sink.write(bytes, 0, bytes.length);
        }

        @Override
        protected DownloadTransferResult generateContent() throws IOException {
            if (!ignoreBody) {
                sink.finish();
                result.setStartOffset(sink.getStartOffset());
                result.setBytesWritten(sink.getBytesWritten());
                result.setTotalBytes(sink.getTotalBytes());
                result.setPartFile(sink.getPartFile());
                result.setSha256(sink.digestFor(FileTransferHelper.SHA256));
                result.setSha1(sink.digestFor(FileTransferHelper.SHA1));
            }
            return result;
        }

        @Override
        public void releaseResources() {
            sink.close();
        }
    }

    @Override
    protected DownloadTransferResult buildResult(final HttpResponse response, final DownloadTransferResult entity,
                                                 final ContentType contentType) {
        if (entity == null) {
            DownloadTransferResult ret = new DownloadTransferResult();
            ret.setStatus(response.getCode());
            ret.setHeaders(ApacheHttpClientHelper.getHeaders(response.getHeaders()));
            return ret;
        }
        return entity;
    }
}
