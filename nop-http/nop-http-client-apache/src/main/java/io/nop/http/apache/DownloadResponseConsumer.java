package io.nop.http.apache;

import io.nop.commons.bytes.ByteBufferHelper;
import io.nop.commons.util.IoHelper;
import io.nop.http.api.client.IHttpOutputFile;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class DownloadResponseConsumer extends AbstractAsyncResponseConsumer<SimpleHttpResponse, byte[]> {
    public DownloadResponseConsumer(IHttpOutputFile file) {
        super(new EntityConsumer(file));
    }

    static class EntityConsumer extends AbstractBinAsyncEntityConsumer<byte[]> {
        private final IHttpOutputFile file;
        private OutputStream out;

        public EntityConsumer(IHttpOutputFile file) {
            this.file = file;
        }

        @Override
        protected void streamStart(final ContentType contentType) throws HttpException, IOException {
            this.out = file.getOutputStream();
        }

        @Override
        protected int capacityIncrement() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
            if (src == null) {
                return;
            }
            ByteBufferHelper.writeToStream(out, src);
        }

        @Override
        protected byte[] generateContent() throws IOException {
            out.flush();
            return null;
        }

        @Override
        public void releaseResources() {
            IoHelper.safeClose(out);
        }

    }


    @Override
    public void informationResponse(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
    }

    @Override
    protected SimpleHttpResponse buildResult(final HttpResponse response, final byte[] entity, final ContentType contentType) {
        return SimpleHttpResponse.copy(response);
    }
}
