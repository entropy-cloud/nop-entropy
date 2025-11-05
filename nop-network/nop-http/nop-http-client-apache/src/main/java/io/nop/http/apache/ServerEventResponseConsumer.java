package io.nop.http.apache;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;

public class ServerEventResponseConsumer extends AbstractStreamResponseConsumer<SimpleHttpResponse, Void> {
    public ServerEventResponseConsumer(Supplier<AsyncEntityConsumer<Void>> dataConsumerSupplier) {
        super(dataConsumerSupplier);
    }

    @Override
    protected SimpleHttpResponse buildResult(HttpResponse response, Void entity, ContentType contentType) {
        return SimpleHttpResponse.copy(response);
    }
}
