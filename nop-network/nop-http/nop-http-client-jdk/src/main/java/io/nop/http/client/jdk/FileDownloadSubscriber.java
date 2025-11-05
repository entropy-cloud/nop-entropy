package io.nop.http.client.jdk;

import io.nop.api.core.util.ICancelToken;
import io.nop.commons.bytes.ByteBufferHelper;
import io.nop.commons.util.IoHelper;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public class FileDownloadSubscriber implements Flow.Subscriber<ByteBuffer> {
    private final OutputStream channel;
    private final CompletableFuture<Void> resultFuture;
    private final ICancelToken cancelToken;
    private Flow.Subscription subscription;

    FileDownloadSubscriber(OutputStream channel, CompletableFuture<Void> resultFuture,
                           ICancelToken cancelToken) {
        this.channel = channel;
        this.resultFuture = resultFuture;
        this.cancelToken = cancelToken;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1); // 请求第一个数据块
    }

    @Override
    public void onNext(ByteBuffer item) {
        try {
            // 检查取消状态
            if (cancelToken != null && cancelToken.isCancelled()) {
                subscription.cancel();
                resultFuture.completeExceptionally(new CancellationException("Download was cancelled"));
                return;
            }

            // 写入文件
            ByteBufferHelper.writeToStream(channel, item);

            // 请求下一个数据块
            subscription.request(1);
        } catch (Exception e) {
            subscription.cancel();
            resultFuture.completeExceptionally(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        try {
            channel.close();
            resultFuture.completeExceptionally(throwable);
        } catch (Exception e) {
            resultFuture.completeExceptionally(e);
        }
    }

    @Override
    public void onComplete() {
        try {
            channel.flush();
            channel.close();
            resultFuture.complete(null);
        } catch (Exception e) {
            IoHelper.safeClose(channel);
            resultFuture.completeExceptionally(e);
        }
    }
}
