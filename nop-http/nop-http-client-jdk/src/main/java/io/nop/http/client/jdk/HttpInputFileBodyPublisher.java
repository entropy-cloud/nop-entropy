package io.nop.http.client.jdk;

import io.nop.http.api.client.IHttpInputFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public class HttpInputFileBodyPublisher implements BodyPublisher {
    private static final int BUFFER_SIZE = 8192; // 8KB buffer size
    private final IHttpInputFile inputFile;

    public HttpInputFileBodyPublisher(IHttpInputFile inputFile) {
        this.inputFile = inputFile;
    }

    @Override
    public long contentLength() {
        return inputFile.getLength();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            private final InputStream inputStream = inputFile.getInputStream();
            private boolean completed = false;

            @Override
            public void request(long n) {
                if (completed) {
                    return;
                }
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead = -1;
                    while (n > 0 && (bytesRead = inputStream.read(buffer)) != -1) {
                        subscriber.onNext(ByteBuffer.wrap(buffer, 0, bytesRead));
                        n--;
                    }
                    if (bytesRead == -1) {
                        completed = true;
                        subscriber.onComplete();
                    }
                } catch (IOException e) {
                    subscriber.onError(e);
                }
            }

            @Override
            public void cancel() {
                completed = true;
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        });
    }
}