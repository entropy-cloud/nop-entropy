package io.nop.http.apache;

import io.nop.http.api.utils.HttpHelper;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.AbstractCharDataConsumer;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class LineAsyncDataConsumer extends AbstractCharDataConsumer
        implements AsyncEntityConsumer<Void>, IStreamResponseConsumer {
    private final int capacityIncrement;

    private StringBuilder buf = new StringBuilder();

    // \r 结尾的行需要跳过紧跟的 \n（CRLF 跨 chunk 分割的情况）
    private boolean skipNextLF;

    private FutureCallback<Void> resultCallback;

    protected HttpResponse response;
    protected ContentType contentType;

    protected Map<String, String> headers;
    protected boolean success;

    public LineAsyncDataConsumer(int bufSize, CharCodingConfig charCodingConfig, int capacityIncrement) {
        super(bufSize, charCodingConfig);
        this.capacityIncrement = capacityIncrement;
    }

    @Override
    protected int capacityIncrement() {
        return capacityIncrement;
    }

    @Override
    public void onStreamBegin(HttpResponse response, ContentType contentType) {
        this.response = response;
        this.contentType = contentType;
        this.headers = ApacheHttpClientHelper.getHeaders(response.getHeaders());
        this.success = HttpHelper.isOk(response.getCode());
    }

    public int getHttpStatus() {
        return response.getCode();
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getContentBody() {
        return buf.toString();
    }

    @Override
    protected void data(CharBuffer src, boolean endOfStream) throws IOException {
        if (!success) {
            buf.append(src);
            return;
        }

        while (src.hasRemaining()) {
            char c = src.get();
            if (skipNextLF && c == '\n') {
                skipNextLF = false;
                continue;
            }
            skipNextLF = false;
            if (c == '\n') {
                emitLine();
            } else if (c == '\r') {
                // SSE 规范允许 CR、LF、CRLF 三种行结尾
                emitLine();
                skipNextLF = true;
            } else {
                buf.append(c);
            }
        }
        if (endOfStream) {
            if (buf.length() > 0) {
                emitLine();
            }
        }
    }

    private void emitLine() throws IOException {
        onLine(buf.toString());
        buf.setLength(0);
    }

    protected abstract void onLine(String line);


    @Override
    public void releaseResources() {

    }

    @Override
    public void streamStart(EntityDetails entityDetails, FutureCallback<Void> resultCallback) throws HttpException, IOException {
        // AsyncEntityConsumer 契约：实体消费完成后必须完成该回调，否则 execute() 返回的 future 永不完成
        this.resultCallback = resultCallback;
        final ContentType contentType = entityDetails != null ? ContentType.parse(entityDetails.getContentType()) : null;
        setCharset(ContentType.getCharset(contentType, StandardCharsets.UTF_8));
    }

    @Override
    protected void completed() throws IOException {
        onStreamComplete();
        FutureCallback<Void> callback = this.resultCallback;
        this.resultCallback = null;
        if (callback != null) {
            callback.completed(null);
        }
    }

    /**
     * 流正常结束时回调。子类应覆盖此方法（而不是 completed()），回调 resultCallback 的逻辑由基类处理。
     */
    protected void onStreamComplete() throws IOException {
    }

    @Override
    public Void getContent() {
        return null;
    }
}
