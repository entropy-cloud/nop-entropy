/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.service.LifeCycleSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Redis Pub/Sub 的消息服务实现（plan 2518 裁定①②③）。
 *
 * <p>能力边界：Redis Pub/Sub 是无持久化、无消费组语义的 fire-and-forget 投递模式。
 * {@link #subscribe(String, IMessageConsumer, MessageSubscribeOptions)} 的 options
 * （subscribeName/transactional/subscriptionType 等）与
 * {@link #sendAsync(String, Object, MessageSendOptions)} 的 options
 * （delay/sendTimeout 等）在此实现中不生效：未声明能力对应的选项按 D1 warn-once
 * 或类型化拒绝显式处置，绝不静默。</p>
 *
 * <p><b>并发契约（plan 2518）</b>：</p>
 * <ul>
 * <li>裁定①：订阅管理（subscribe/cancel/suspend/resume/doStop 对账）收敛于服务实例单一监视器；
 *     cancelled 重入守卫 + 锁内空判复查；监视器内阻塞 Redis 往返显式接受（5s 命令超时预算封顶）。</li>
 * <li>裁定②：消费回调经模块内单投递线程有界 executor 外移执行（IO 线程仅入队）；
 *     CompletionStage 返回值有界超时告警（不等待）；队列满拒绝走 onConsumeFailure 类型化留痕（禁静默丢弃）。</li>
 * <li>裁定③：断线重连重订阅由 lettuce activated() 自主完成（服务零代做）；服务侧唯一修复面 = 可观测——
 *     连接激活事件只投递专用对账线程，对注册表快照发幂等确认 SUBSCRIBE，失败 WARN + 计数；
 *     确认只订快照内条目（禁复活已 cancel 订阅）；幽灵通道接受不处置（R2 watch-only）。</li>
 * </ul>
 */
public class LettucePubSubService extends LifeCycleSupport implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(LettucePubSubService.class);

    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE);

    /** Fire-and-forget transport: non-basic options are warn-once (D1), never silent. */
    private static final Set<MessageCapability> WARN_ONCE = EnumSet.of(
            MessageCapability.DELAYED_SEND, MessageCapability.SEND_TIMEOUT,
            MessageCapability.BATCH_CONSUME, MessageCapability.SEEK);

    /** 消费投递线程名前缀（裁定②：外移接线证据锚点）。 */
    static final String CONSUMER_THREAD_PREFIX = "nop-lettuce-pubsub-consumer";
    private static final String RECONCILER_THREAD_PREFIX = "nop-lettuce-pubsub-reconciler";

    /** 投递队列缺省上界（裁定②有界化；禁静默丢弃——拒绝走类型化回调）。 */
    static final int DEFAULT_DELIVERY_QUEUE_CAPACITY = 1000;
    /** CompletionStage 返回值有界观测超时（裁定②缺省 60s，只告警不等待）。 */
    private static final long COMPLETION_TIMEOUT_SECONDS = 60;
    /** doStop 排空等待上界。 */
    private static final long STOP_AWAIT_SECONDS = 5;

    @Override
    public Set<MessageCapability> getCapabilities() {
        return CAPABILITIES;
    }

    private final LettuceRedisConnectionProvider connectionProvider;

    /** 投递队列容量（测试缝：包内可调小以验证拒绝策略）。 */
    int deliveryQueueCapacity = DEFAULT_DELIVERY_QUEUE_CAPACITY;

    private volatile PubSubLink link;
    private final ConcurrentMap<String, SubscriptionEntry> subscriptions = new ConcurrentHashMap<>();
    /** 投递 executor 创建锁（IO 线程入队路径专用——禁取服务监视器，M1 线程域硬条款）。 */
    private final Object execLock = new Object();
    /** 对账线程创建锁（IO 线程激活回调专用——同上）。 */
    private final Object reconcilerLock = new Object();

    private volatile ThreadPoolExecutor deliveryExecutor;
    private volatile java.util.concurrent.ExecutorService reconciler;
    /** 服务停止后不再接受新投递/对账动作。 */
    private volatile boolean stopped;
    /** 裁定③：幂等确认 SUBSCRIBE 失败计数（nop.lettuce.pubsub.resubscribe-failed 观测面）。 */
    private final AtomicLong resubscribeFailureCount = new AtomicLong();
    /** 裁定②：非 CompletionStage 非 null 返回值 warn-once（fire-and-forget 无消费语义）。 */
    private final AtomicBoolean unsupportedReturnWarned = new AtomicBoolean();

    public LettucePubSubService(LettuceRedisConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /** 连接链构造缝（测试注入 fake 链接，非 docker 可跑）。 */
    protected PubSubLink createLink() {
        return new LettucePubSubLink(connectionProvider.createPubSubConnection());
    }

    private PubSubLink getOrCreateLink() {
        PubSubLink l = link;
        if (l == null) {
            synchronized (this) {
                if (link == null) {
                    PubSubLink created = createLink();
                    // 裁定③：连接激活事件侦听（IO 线程只投递事件至专用对账线程）
                    created.addStateListener(this::onLinkActivated);
                    link = created;
                }
                l = link;
            }
        }
        return l;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "lettuce", WARN_ONCE);
        return connectionProvider.getAsyncCommands().publish(topic, message)
                .thenApply(ignored -> null);
    }

    @Override
    public synchronized IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                                       MessageSubscribeOptions options) {
        MessageOptionsValidator.validateSubscribe(CAPABILITIES, options, "lettuce", WARN_ONCE);
        PubSubLink l = getOrCreateLink();
        SubscriptionEntry entry = subscriptions.get(topic);
        if (entry == null) {
            entry = new SubscriptionEntry(topic);
            subscriptions.put(topic, entry);
            entry.addListener(listener);
            try {
                // 监视器内阻塞往返：显式接受队头阻塞代价（裁定①，5s 命令超时预算封顶）
                l.subscribe(topic);
            } catch (RuntimeException e) {
                // roll back the stale entry, otherwise later subscribes would only add a listener
                // without ever sending SUBSCRIBE to Redis again (锁内原子回滚，消除 addListener 泄漏窗)
                entry.removeListener(listener);
                subscriptions.remove(topic, entry);
                throw e;
            }
        } else {
            entry.addListener(listener);
        }
        return new PubSubSubscription(topic, entry, listener);
    }

    @Override
    protected void doStart() {
        stopped = false;
    }

    /**
     * 停机对账（裁定①，与裁定③共享「注册表=SSOT」不变式）：锁内清空注册表 + 逐 topic
     * UNSUBSCRIBE/摘除 listener；随后锁外停投递 executor 并排空残余队列（逐条 onConsumeFailure
     * 留痕，禁静默消失），最后关闭连接。
     */
    @Override
    protected void doStop() {
        List<SubscriptionEntry> entries;
        PubSubLink l;
        synchronized (this) {
            entries = new ArrayList<>(subscriptions.values());
            subscriptions.clear();
            l = link;
        }
        if (l != null) {
            for (SubscriptionEntry entry : entries) {
                try {
                    l.unsubscribe(entry.topic);
                } catch (RuntimeException e) {
                    LOG.warn("nop.lettuce.pubsub.stop-unsubscribe-failed:topic={}", entry.topic, e);
                }
                try {
                    l.removeMessageListener(entry);
                } catch (RuntimeException e) {
                    LOG.warn("nop.lettuce.pubsub.stop-listener-remove-failed:topic={}", entry.topic, e);
                }
            }
        }

        ThreadPoolExecutor exec;
        java.util.concurrent.ExecutorService reconcilerRef;
        synchronized (execLock) {
            stopped = true;
            exec = deliveryExecutor;
            deliveryExecutor = null;
        }
        synchronized (reconcilerLock) {
            reconcilerRef = reconciler;
            reconciler = null;
        }
        if (exec != null) {
            // 排空残余队列：未投递消息逐条 onConsumeFailure 留痕后丢弃（禁静默消失）
            for (Runnable pending : exec.shutdownNow()) {
                if (pending instanceof DeliveryTask)
                    ((DeliveryTask) pending).invokeConsumeFailure("pubsub service stopped");
            }
            awaitExecutorTermination(exec);
        }
        if (reconcilerRef != null)
            reconcilerRef.shutdownNow();
        if (l != null)
            l.close();
    }

    private static void awaitExecutorTermination(ThreadPoolExecutor exec) {
        try {
            if (!exec.awaitTermination(STOP_AWAIT_SECONDS, TimeUnit.SECONDS))
                LOG.warn("nop.lettuce.pubsub.stop-drain-timeout:waitSeconds={}", STOP_AWAIT_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("nop.lettuce.pubsub.stop-drain-interrupted");
        }
    }

    // ------------------------------------------------------------------
    // 裁定②：消费外移投递
    // ------------------------------------------------------------------

    void dispatchToExecutor(String channel, Object message, List<IMessageConsumer> consumers) {
        try {
            ThreadPoolExecutor exec = getOrCreateDeliveryExecutor();
            exec.execute(new DeliveryTask(channel, message, consumers));
        } catch (RejectedExecutionException e) {
            // 队列满 / 服务已停：登记豁免面——拒绝回调在 IO 线程内联执行（无法再入队）；
            // 契约 = onConsumeFailure 实现方不得阻塞/发起 Redis 调用（仅日志/计数语义）
            new DeliveryTask(channel, message, consumers).invokeConsumeFailure(
                    "delivery queue rejected the task (queue full or service stopped)");
        }
    }

    private ThreadPoolExecutor getOrCreateDeliveryExecutor() {
        // M1 线程域硬条款：本方法会从 lettuce IO 线程抵达（message 入队路径）——
        // 不得取服务监视器，用独立 execLock
        ThreadPoolExecutor exec = deliveryExecutor;
        if (exec == null) {
            synchronized (execLock) {
                if (deliveryExecutor == null) {
                    if (stopped)
                        throw new RejectedExecutionException("pubsub service already stopped");
                    deliveryExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                            new LinkedBlockingQueue<>(deliveryQueueCapacity), new ConsumerThreadFactory(),
                            new DeliveryRejectionHandler());
                }
                exec = deliveryExecutor;
            }
        }
        return exec;
    }

    private static final class ConsumerThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, CONSUMER_THREAD_PREFIX + "-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 队列满拒绝策略（裁定②，rule 24 禁静默丢弃）：在提交线程（IO 线程）内联执行类型化
     * onConsumeFailure 留痕——该内联为登记豁免面（契约：回调实现不得阻塞/发起 Redis 调用）。
     */
    private final class DeliveryRejectionHandler implements java.util.concurrent.RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (r instanceof DeliveryTask)
                ((DeliveryTask) r).invokeConsumeFailure("delivery queue is full (capacity="
                        + deliveryQueueCapacity + ")");
            else
                LOG.warn("nop.lettuce.pubsub.delivery-rejected:task={}", r);
        }
    }

    private final class DeliveryTask implements Runnable {
        private final String channel;
        private final Object message;
        private final List<IMessageConsumer> consumers;

        DeliveryTask(String channel, Object message, List<IMessageConsumer> consumers) {
            this.channel = channel;
            this.message = message;
            this.consumers = consumers;
        }

        @Override
        public void run() {
            IMessageConsumeContext context = new PubSubConsumeContext();
            for (IMessageConsumer consumer : consumers) {
                Object result;
                try {
                    result = consumer.onMessage(channel, message, context);
                } catch (Exception e) {
                    // WI16: full-context failure callback (D6 layer one).
                    consumer.onConsumeFailure(new ConsumeFailure(channel, channel, message, 0, e));
                    continue;
                }
                observeReturnValue(result);
            }
        }

        /** 停机排空/队列满拒绝路径：类型化回调留痕，不执行消费者回调。 */
        void invokeConsumeFailure(String cause) {
            LOG.warn("nop.lettuce.pubsub.delivery-rejected:topic={},cause={}", channel, cause);
            ConsumeFailure failure = new ConsumeFailure(channel, channel, message, 0,
                    new PubSubDeliveryRejectedException(cause));
            for (IMessageConsumer consumer : consumers) {
                try {
                    consumer.onConsumeFailure(failure);
                } catch (RuntimeException e) {
                    LOG.warn("nop.lettuce.pubsub.consume-failure-callback-failed:topic={}", channel, e);
                }
            }
        }
    }

    /** 拒绝/停机排空专用异常类型（英文消息，模块内部）。 */
    static final class PubSubDeliveryRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PubSubDeliveryRejectedException(String message) {
            super(message);
        }
    }

    /**
     * 返回值契约（裁定②定夺 = 有界超时告警，不等待完成）：CompletionStage 挂 60s 一次性观测
     * （globalTimer 只消费零触碰）；其余非 null 返回值在 fire-and-forget 传输无消费语义，warn-once。
     */
    private void observeReturnValue(Object result) {
        if (result == null)
            return;
        if (result instanceof CompletionStage) {
            CompletableFuture<?> future = ((CompletionStage<?>) result).toCompletableFuture();
            java.util.concurrent.Future<?> timeoutTask = GlobalExecutors.globalTimer().schedule(() -> {
                if (!future.isDone())
                    LOG.warn("nop.lettuce.pubsub.completion-timeout:timeoutSeconds={}", COMPLETION_TIMEOUT_SECONDS);
                return null;
            }, COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            future.whenComplete((v, t) -> timeoutTask.cancel(false));
            return;
        }
        // 非 CompletionStage 的非 null 返回值（ConsumeLater/响应消息形态）在 fire-and-forget
        // 传输无消费语义：warn-once 显式登记（D1 先例）
        if (unsupportedReturnWarned.compareAndSet(false, true))
            LOG.warn("nop.lettuce.pubsub.unsupported-consume-return:type={},hint={}", result.getClass().getName(),
                    "redis pub/sub is fire-and-forget: only null or CompletionStage returns carry meaning");
    }

    // ------------------------------------------------------------------
    // 裁定③：重连恢复可观测（lettuce 自主 resubscribe 的幂等确认面）
    // ------------------------------------------------------------------

    /** 幂等确认 SUBSCRIBE 失败计数（观测面）。 */
    public long getResubscribeFailureCount() {
        return resubscribeFailureCount.get();
    }

    private void onLinkActivated() {
        java.util.concurrent.ExecutorService ref = reconciler();
        if (ref == null)
            return;
        try {
            ref.execute(this::reconcileSubscriptions);
        } catch (RejectedExecutionException e) {
            LOG.warn("nop.lettuce.pubsub.reconcile-rejected:service stopped");
        }
    }

    private java.util.concurrent.ExecutorService reconciler() {
        // M1 线程域硬条款：由 lettuce IO 线程抵达（激活事件回调）——不得取服务监视器，用独立 reconcilerLock
        java.util.concurrent.ExecutorService ref = reconciler;
        if (ref == null) {
            synchronized (reconcilerLock) {
                if (reconciler == null && !stopped) {
                    reconciler = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                        Thread thread = new Thread(r, RECONCILER_THREAD_PREFIX);
                        thread.setDaemon(true);
                        return thread;
                    });
                }
                ref = reconciler;
            }
        }
        return ref;
    }

    /**
     * 专用对账线程执行：取服务监视器拷贝注册表快照（锁内只读拷贝）→ 锁外对快照逐 topic 发
     * 幂等确认 SUBSCRIBE（拷贝-释放-发送三段；确认失败 WARN + 失败计数）。已 cancel 订阅在
     * cancel 时已锁内移除，结构性不复活。
     */
    private void reconcileSubscriptions() {
        List<SubscriptionEntry> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(subscriptions.values());
        }
        PubSubLink l = link;
        if (l == null)
            return;
        for (SubscriptionEntry entry : snapshot) {
            try {
                l.subscribe(entry.topic);
            } catch (RuntimeException e) {
                long failures = resubscribeFailureCount.incrementAndGet();
                LOG.warn("nop.lettuce.pubsub.resubscribe-failed:topic={},failureCount={}", entry.topic, failures, e);
            }
        }
        LOG.info("nop.lettuce.pubsub.reconnect-resubscribe-confirmed:topics={}", snapshot.size());
    }

    // ------------------------------------------------------------------

    class SubscriptionEntry extends RedisPubSubAdapter<String, Object> {
        final String topic;
        private final ConcurrentMap<IMessageConsumer, Boolean> listeners = new ConcurrentHashMap<>();

        SubscriptionEntry(String topic) {
            this.topic = topic;
        }

        /** 仅在服务监视器内调用（裁定①单锁域）。 */
        void addListener(IMessageConsumer consumer) {
            listeners.put(consumer, Boolean.TRUE);
            if (listeners.size() == 1) {
                // 锁内空判复查：并发 addListener×removeListener 交错下 size==1 门由监视器串行化
                getOrCreateLink().addMessageListener(this);
            }
        }

        /** 仅在服务监视器内调用（裁定①单锁域）。 */
        void removeListener(IMessageConsumer consumer) {
            listeners.remove(consumer);
            if (listeners.isEmpty()) {
                getOrCreateLink().removeMessageListener(this);
            }
        }

        boolean isEmpty() {
            return listeners.isEmpty();
        }

        @Override
        public void message(String channel, Object message) {
            // one shared connection carries all subscriptions; only deliver to this entry's own topic
            if (!topic.equals(channel))
                return;
            // IO 线程仅入队（裁定②）：listeners 快照在入队时取（锁外 CHM 快照，与裁定①锁域无嵌套）
            dispatchToExecutor(channel, message, new ArrayList<>(listeners.keySet()));
        }
    }

    private class PubSubConsumeContext implements IMessageConsumeContext {
        @Override
        public ICancelToken getCancelToken() {
            return null;
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return LettucePubSubService.this.sendAsync(topic, message, options);
        }
    }

    private class PubSubSubscription implements IMessageSubscription {
        private final String topic;
        private final SubscriptionEntry entry;
        private final IMessageConsumer listener;
        private volatile boolean suspended;
        private volatile boolean cancelled;

        PubSubSubscription(String topic, SubscriptionEntry entry, IMessageConsumer listener) {
            this.topic = topic;
            this.entry = entry;
            this.listener = listener;
        }

        /**
         * 裁定①：cancelled 重入守卫 + 三步 check-then-act（removeListener → isEmpty →
         * unsubscribe+remove）收敛入服务监视器，锁内原子化；属主契约 = 本 subscription 只管理
         * 自己 subscribe 时注册的 listener。
         */
        @Override
        public void cancel() {
            synchronized (LettucePubSubService.this) {
                if (cancelled) {
                    LOG.debug("nop.lettuce.pubsub.cancel-reentry:topic={},cancelled=true", topic);
                    return;
                }
                cancelled = true;
                entry.removeListener(listener);
                // 锁内空判复查：unsubscribe 与注册表移除原子绑定——仅当本 entry 仍是注册条目时
                // 才退订一次（最后一个属主 cancel 触发；已注册条目被新订阅替换后不再重复退订）
                if (entry.isEmpty() && subscriptions.remove(topic, entry)) {
                    PubSubLink l = getOrCreateLink();
                    try {
                        l.unsubscribe(topic);
                    } catch (RuntimeException e) {
                        // 退订失败不阻断注销（注册表=SSOT）；Redis 侧残留由停机对账/重连确认面收敛
                        LOG.warn("nop.lettuce.pubsub.unsubscribe-failed:topic={}", topic, e);
                    }
                }
            }
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void suspend() {
            synchronized (LettucePubSubService.this) {
                if (cancelled) {
                    LOG.debug("nop.lettuce.pubsub.suspend-after-cancel:topic={},ignored", topic);
                    return;
                }
                if (!suspended) {
                    suspended = true;
                    entry.removeListener(listener);
                }
            }
        }

        @Override
        public void resume() {
            synchronized (LettucePubSubService.this) {
                if (cancelled) {
                    // resume 对 cancelled 恒拒绝（既有判定保留并入锁）——显式 no-op + 留痕
                    LOG.debug("nop.lettuce.pubsub.resume-after-cancel:topic={},rejected", topic);
                    return;
                }
                if (suspended) {
                    suspended = false;
                    // 已被并发 cancel 处置（注销且移出注册表）的 entry 不再复挂 listener
                    if (subscriptions.get(topic) == entry)
                        entry.addListener(listener);
                }
            }
        }
    }
}
