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
            if (c == '\n') {
                onLine(buf.toString());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        if (endOfStream) {
            if (buf.length() > 0) {
                onLine(buf.toString());
                buf.setLength(0);
            }
        }
    }

    protected abstract void onLine(String line);


    @Override
    public void releaseResources() {

    }

    @Override
    public void streamStart(EntityDetails entityDetails, FutureCallback<Void> resultCallback) throws HttpException, IOException {
        final ContentType contentType = entityDetails != null ? ContentType.parse(entityDetails.getContentType()) : null;
        setCharset(ContentType.getCharset(contentType, StandardCharsets.UTF_8));
    }

    @Override
    public Void getContent() {
        return null;
    }
}
