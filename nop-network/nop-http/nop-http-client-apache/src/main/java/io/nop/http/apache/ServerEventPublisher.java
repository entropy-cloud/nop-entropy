package io.nop.http.apache;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.IBlockingQueue;
import io.nop.commons.concurrent.QueueOverflowPolicy;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.http.api.client.AbstractServerEventSubscription;
import io.nop.http.api.client.IServerEventResponse;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.nop.http.api.HttpApiErrors.ARG_BODY;
import static io.nop.http.api.HttpApiErrors.ARG_HTTP_STATUS;
import static io.nop.http.api.HttpApiErrors.ARG_RESPONSE_HEADERS;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_RESPONSE_ERROR;

/**
 * Apache SSE 事件流发布器。
 *
 * <p><b>4.3-C6-004 面②/④（plan 2505，设计权威来源
 * {@code ai-dev/design/concurrency/sse-backpressure-and-body-phase-lifecycle.md} §3.2）</b>：
 * HC5 I/O reactor 线程不再同步直调 {@code processLine}/终态派发——reactor 线程只把行与
 * 终态投递到有界 {@link OverflowBlockingQueue}（溢出策略 = 满载 fail-loud 抛队列满错误，
 * 进入 HC5 failed 路径终态化），由专用 daemon 消费线程逐行执行基类处理（demand 等待
 * 发生在消费线程上，被基类有界化收敛）。同 reactor 上其他连接不再被停滞订阅者挂起。
 */
public class ServerEventPublisher implements Flow.Publisher<IServerEventResponse> {
    static final Logger LOG = LoggerFactory.getLogger(ServerEventPublisher.class);

    /**
     * SSE 事件派发承载线程工厂：daemon 形态（4.3-C6-004 面④，挂死不阻断 JVM 退出）。
     */
    private static final NamedThreadFactory SSE_DISPATCH_THREAD_FACTORY =
            NamedThreadFactory.newThreadFactory("nop-sse-dispatch", true);

    private final CloseableHttpAsyncClient client;
    private final SimpleHttpRequest request;
    private final HttpClientContext context;
    private final ICancelToken cancelToken;

    public ServerEventPublisher(CloseableHttpAsyncClient client, SimpleHttpRequest request,
                                HttpClientContext context, ICancelToken cancelToken) {
        this.client = client;
        this.request = request;
        this.context = context;
        this.cancelToken = cancelToken;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super IServerEventResponse> subscriber) {
        ServerEventSubscription subscription = new ServerEventSubscription(
                subscriber
        );
        subscriber.onSubscribe(subscription);
    }

    protected class ServerEventSubscription extends AbstractServerEventSubscription {

        /**
         * 行/终态投递队列（4.3-C6-004 面②）：容量有界 + 满载 fail-loud（ERROR 策略，
         * 抛 ERR_QUEUE_FULL 进入 HC5 failed 路径终态化）。拒绝 BLOCK_WAIT（reactor
         * 阻塞回归缺陷形态）与 DROP_*（SSE 数据静默丢失 = 数据完整性违约）。
         */
        private final IBlockingQueue<Object> dispatchQueue =
                new OverflowBlockingQueue<>(1024, QueueOverflowPolicy.ERROR);

        /**
         * 终态单次投递守卫：首个终态（complete/error）胜出，后到终态丢弃（Flow 单终态纪律）。
         */
        private final AtomicBoolean terminalQueued = new AtomicBoolean();

        /**
         * 派发消费线程（daemon，4.3-C6-004 面④）。demand 停滞时被基类有界预算终态化，
         * 线程按订阅有界存活。
         */
        private volatile Thread dispatchThread;

        /**
         * 终态 sentinel：经队列串行化（保持「事件先于终态」顺序），消费线程执行基类终态
         * （含 final flush 的有界 demand 等待）——reactor 线程零内联派发。
         */
        private final Object completeSentinel = new Object();

        ServerEventSubscription(
                Flow.Subscriber<? super IServerEventResponse> subscriber
        ) {
            super(subscriber);
        }

        @Override
        protected Future<?> startRequest() {
            CompletableFuture<SimpleHttpResponse> promise = new CompletableFuture<>();

            // 发送异步HTTP请求
            Future<SimpleHttpResponse> future = client.execute(SimpleRequestProducer.create(request), new ServerEventResponseConsumer(this::newServerEventConsumer),
                    context, new FutureCallback<>() {
                        @Override
                        public void completed(SimpleHttpResponse result) {
                            promise.complete(result);
                            onComplete();
                        }

                        @Override
                        public void failed(Exception ex) {
                            promise.completeExceptionally(ex);
                            onError(ApacheHttpClientHelper.wrapException(ex));
                        }

                        @Override
                        public void cancelled() {
                            promise.cancel(false);
                        }
                    });

            FutureHelper.bindCancelToken(cancelToken, this::cleanup, promise);
            return future;
        }

        ServerEventConsumer newServerEventConsumer() {
            return new ServerEventConsumer(2048, CharCodingConfig.DEFAULT, 2048);
        }

        /**
         * 4.3-C6-004 面②：reactor 线程只投递不阻塞。队列满（消费停滞且积压超容量）时
         * fail-loud 抛出，经 HC5 协议进入 {@code failed} 终态化——停滞显式可见。
         */
        @Override
        protected void processLine(String line) {
            ensureDispatchThread();
            try {
                dispatchQueue.send(line);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cleanup(null);
            }
        }

        /**
         * 终态经队列串行化（保持事件顺序），消费线程执行基类 onComplete（final flush 有界）；
         * 无行派发（如空流/非 2xx 直接终态）时无排队顺序约束，直接派发，不为纯错误流建线程。
         */
        @Override
        protected void onComplete() {
            if (dispatchThread == null) {
                super.onComplete();
                return;
            }
            enqueueTerminal(completeSentinel);
        }

        /**
         * 错误经队列串行化（消费线程统一 wrap）；派发线程自身产生的停滞终态（基类
         * onDemandStall）直接透传基类（再入队会被自身退出循环丢弃，终态信号丢失）；
         * 无行派发时直接派发（见 onComplete 注）。
         */
        @Override
        protected void onError(Exception e) {
            if (Thread.currentThread() == dispatchThread || dispatchThread == null) {
                super.onError(e);
                return;
            }
            enqueueTerminal(e);
        }

        private void enqueueTerminal(Object sentinel) {
            if (!terminalQueued.compareAndSet(false, true))
                return; // 后到终态丢弃：首个终态已交付（Flow 单终态纪律）
            try {
                dispatchQueue.send(sentinel);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cleanup(null);
            } catch (RuntimeException e) {
                // 队列满 = 消费端已终态（停滞终态/取消）——后到终态不可达，显式记录不静默升级
                LOG.debug("nop.http.sse.post-terminal-drop: {}", e.toString());
            }
        }

        private void ensureDispatchThread() {
            if (dispatchThread != null)
                return;
            synchronized (this) {
                if (dispatchThread != null)
                    return;
                Thread thread = SSE_DISPATCH_THREAD_FACTORY.newThread(this::drainDispatchQueue);
                dispatchThread = thread;
                thread.start();
            }
        }

        private void drainDispatchQueue() {
            try {
                while (!isCancelled()) {
                    Object item = dispatchQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (item == null)
                        continue;
                    if (item == completeSentinel) {
                        super.onComplete();
                        return;
                    }
                    if (item instanceof Throwable t) {
                        super.onError(ApacheHttpClientHelper.wrapException(t));
                        return;
                    }
                    super.processLine((String) item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                dispatchQueue.clear();
            }
        }

        class ServerEventConsumer extends LineAsyncDataConsumer {
            public ServerEventConsumer(int bufSize, CharCodingConfig charCodingConfig, int capacityIncrement) {
                super(bufSize, charCodingConfig, capacityIncrement);
            }

            @Override
            public void onStreamBegin(HttpResponse response, ContentType contentType) {
                super.onStreamBegin(response, contentType);
                onStart(getHttpStatus(), getHeaders());
            }

            @Override
            protected void onLine(String line) {
                processLine(line);
            }

            @Override
            public void failed(Exception cause) {
                onError(ApacheHttpClientHelper.wrapException(cause));
            }

            @Override
            protected void onStreamComplete() throws IOException {
                if (!success)
                    throw new NopException(ERR_HTTP_RESPONSE_ERROR)
                            .param(ARG_HTTP_STATUS, response.getCode())
                            .param(ARG_BODY, getContentBody())
                            .param(ARG_RESPONSE_HEADERS, getHeaders());
                onComplete();
            }
        }
    }
}
