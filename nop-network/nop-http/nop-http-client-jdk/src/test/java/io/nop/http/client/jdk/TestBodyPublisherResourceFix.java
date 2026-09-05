package io.nop.http.client.jdk;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBodyPublisherResourceFix {

    static class TrackingInputStream extends ByteArrayInputStream {
        final AtomicBoolean closed = new AtomicBoolean();

        TrackingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            super.close();
        }
    }

    static class TrackingFile implements io.nop.http.api.client.IHttpInputFile {
        final TrackingInputStream stream;
        final AtomicBoolean streamOpened = new AtomicBoolean();

        TrackingFile(byte[] data) {
            this.stream = new TrackingInputStream(data);
        }

        @Override
        public java.io.File toFile() {
            return null;
        }

        @Override
        public String getName() {
            return "input";
        }

        @Override
        public java.io.InputStream getInputStream() {
            streamOpened.set(true);
            return stream;
        }

        @Override
        public long getLength() {
            return stream.available();
        }
    }

    @Test
    public void testInputFileClosedOnCompletion() throws Exception {
        TrackingFile file = new TrackingFile("hello".getBytes());
        HttpInputFileBodyPublisher publisher = new HttpInputFileBodyPublisher(file);

        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get(5, TimeUnit.SECONDS);
        // 上传完成后输入流必须关闭，否则每次上传泄漏一个文件句柄
        assertTrue(file.stream.closed.get(), "input stream must be closed after completion");
    }

    static class FailingCancelToken implements ICancelToken {
        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public String getCancelReason() {
            return "test";
        }

        @Override
        public void appendOnCancel(Consumer<String> task) {
        }

        @Override
        public void removeOnCancel(Consumer<String> task) {
        }
    }

    static class TrackingOutputStream extends OutputStream {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void write(int b) throws IOException {
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @Test
    public void testDownloadSubscriberClosesChannelOnCancel() {
        TrackingOutputStream channel = new TrackingOutputStream();
        CompletableFuture<Void> result = new CompletableFuture<>();
        FileDownloadSubscriber subscriber = new FileDownloadSubscriber(channel, result, new FailingCancelToken());

        Flow.Subscription subscription = new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        };
        subscriber.onSubscribe(subscription);
        subscriber.onNext(ByteBuffer.wrap("x".getBytes()));

        // 已取消的下载必须关闭输出流，否则泄漏文件句柄
        assertTrue(channel.closed.get(), "channel must be closed when download is cancelled");
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    public void testDownloadSubscriberClosesChannelOnWriteError() {
        TrackingOutputStream channel = new TrackingOutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };
        CompletableFuture<Void> result = new CompletableFuture<>();
        FileDownloadSubscriber subscriber = new FileDownloadSubscriber(channel, result, null);

        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        subscriber.onNext(ByteBuffer.wrap("x".getBytes()));

        assertTrue(channel.closed.get(), "channel must be closed on write failure");
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    public void testUploadAsyncRejectsNullInputFile() {
        JdkHttpClient client = new JdkHttpClient(new HttpClientConfig());
        assertThrows(IllegalArgumentException.class,
                () -> client.uploadAsync(HttpRequest.post("http://localhost/x"), null, null, null));
    }
}
