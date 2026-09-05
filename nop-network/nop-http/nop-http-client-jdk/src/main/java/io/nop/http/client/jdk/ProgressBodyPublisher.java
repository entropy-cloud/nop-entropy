package io.nop.http.client.jdk;

import io.nop.api.core.util.progress.IProgressListener;

import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

/**
 * 包装请求体 publisher：按实际交付字节回调进度
 */
public class ProgressBodyPublisher implements BodyPublisher {
    private final BodyPublisher delegate;
    private final IProgressListener progressListener;
    private final Object message;
    private final long total;

    public ProgressBodyPublisher(BodyPublisher delegate, IProgressListener progressListener, Object message) {
        this.delegate = delegate;
        this.progressListener = progressListener;
        this.message = message;
        this.total = delegate.contentLength();
    }

    @Override
    public long contentLength() {
        return delegate.contentLength();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(new Flow.Subscriber<>() {
            private long sent;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
                sent += item.remaining();
                if (progressListener != null)
                    progressListener.onProgress(message, sent, total);
                subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }
}
