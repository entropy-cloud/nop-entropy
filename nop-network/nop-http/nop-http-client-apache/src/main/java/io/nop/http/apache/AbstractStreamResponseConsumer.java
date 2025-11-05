package io.nop.http.apache;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractStreamResponseConsumer<T, E> implements AsyncResponseConsumer<T> {

    private final Supplier<AsyncEntityConsumer<E>> dataConsumerSupplier;
    private final AtomicReference<AsyncEntityConsumer<E>> dataConsumerRef;

    public AbstractStreamResponseConsumer(Supplier<AsyncEntityConsumer<E>> dataConsumerSupplier) {
        this.dataConsumerSupplier = Args.notNull(dataConsumerSupplier, "Data consumer supplier");
        this.dataConsumerRef = new AtomicReference<>();
    }

    @Override
    public void informationResponse(HttpResponse response, HttpContext context) {

    }

    /**
     * Triggered to generate object that represents a result of response message processing.
     *
     * @param response    the response message.
     * @param entity      the response entity.
     * @param contentType the response content type.
     * @return the result of response processing.
     */
    protected abstract T buildResult(HttpResponse response, E entity, ContentType contentType);

    @Override
    public final void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final HttpContext httpContext, final FutureCallback<T> resultCallback) throws HttpException, IOException {
        if (entityDetails != null) {
            final AsyncEntityConsumer<E> dataConsumer = dataConsumerSupplier.get();
            if (dataConsumer == null) {
                throw new HttpException("Supplied data consumer is null");
            }
            dataConsumerRef.set(dataConsumer);

            ContentType contentType = parseContentType(entityDetails);
            if (dataConsumer instanceof IStreamResponseConsumer)
                ((IStreamResponseConsumer) dataConsumer).onStreamBegin(response, contentType);

            dataConsumer.streamStart(entityDetails, new CallbackContribution<>(resultCallback) {

                @Override
                public void completed(final E entity) {
                    try {
                        final T result = buildResult(response, entity, contentType);
                        if (resultCallback != null) {
                            resultCallback.completed(result);
                        }
                    } catch (final UnsupportedCharsetException ex) {
                        if (resultCallback != null) {
                            resultCallback.failed(ex);
                        }
                    }
                }

            });
        } else {
            final T result = buildResult(response, null, null);
            if (resultCallback != null) {
                resultCallback.completed(result);
            }
        }

    }

    ContentType parseContentType(EntityDetails entityDetails) {
        return ContentType.parse(entityDetails.getContentType());
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        if (dataConsumer != null) {
            dataConsumer.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        if (dataConsumer != null) {
            dataConsumer.consume(src);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.get();
        if (dataConsumer != null) {
            dataConsumer.streamEnd(trailers);
        }
    }

    @Override
    public final void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        final AsyncEntityConsumer<E> dataConsumer = dataConsumerRef.getAndSet(null);
        if (dataConsumer != null) {
            dataConsumer.releaseResources();
        }
    }
}
