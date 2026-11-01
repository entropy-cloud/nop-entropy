package io.nop.sys.dao.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.PartialCommit;
import io.nop.api.core.message.TerminateConsume;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.TopicMessage;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.message.core.local.LocalMessageService;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.NopSysDaoException;
import io.nop.sys.dao.entity.NopSysBroadcastEvent;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.message.core.MessageCoreConstants.TOPIC_PREFIX_BROADCAST;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class SysDaoMessageService extends LifeCycleSupport
        implements IMessageService, io.nop.message.core.channel.ChannelBacklogProbe {
    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE, MessageCapability.DELAYED_SEND,
            MessageCapability.ACK_REPLY);

    /** DB polling transport: batch / seek options are warn-once (D1). */
    private static final Set<MessageCapability> WARN_ONCE = EnumSet.of(
            MessageCapability.SEND_TIMEOUT, MessageCapability.BATCH_CONSUME, MessageCapability.SEEK);

    @Override
    public Set<MessageCapability> getCapabilities() {
        return CAPABILITIES;
    }

    static final Logger LOG = LoggerFactory.getLogger(SysDaoMessageService.class);

    private IDaoProvider daoProvider;

    private IScheduledExecutor timer;

    private IThreadPoolExecutor executor;

    private Duration checkBroadcastEventInterval = Duration.of(500, ChronoUnit.MILLIS);

    private Duration checkSimpleEventInterval = Duration.of(500, ChronoUnit.MILLIS);

    private int maxScanLoops = 1000;

    private int fetchSize = 100;

    private int startGap = 5000;

    private IRetryPolicy<SysDaoMessageService> retryPolicy = new RetryPolicy<>();

    private Map<String, IRetryPolicy<SysDaoMessageService>> topicRetryPolicies = new ConcurrentHashMap<>();


    private Future<?> checkBroadcastFuture;
    private Future<?> checkNonBroadcastFuture;
    private Future<?> cleanupFuture;

    /**
     * 已处理事件的保留天数（含广播表），到期清理防止事件表无限膨胀拖垮轮询扫描。0=禁用。
     */
    private int cleanupRetentionDays = 7;
    private Duration cleanupInterval = Duration.ofHours(1);

    /**
     * WAITING/CLAIMED 事件的超长滞留清理阈值（天），覆盖发往无订阅者 topic 的事件
     * （topic-in 过滤使其永不被扫描）与被接管后遗弃的 CLAIMED 行。0=禁用。
     */
    private int cleanupStaleWaitingDays = 30;

    @InjectValue("@cfg:nop.sys.event.cleanup-retention-days|7")
    public void setCleanupRetentionDays(int cleanupRetentionDays) {
        this.cleanupRetentionDays = cleanupRetentionDays;
    }

    @InjectValue("@cfg:nop.sys.event.cleanup-stale-waiting-days|30")
    public void setCleanupStaleWaitingDays(int cleanupStaleWaitingDays) {
        this.cleanupStaleWaitingDays = cleanupStaleWaitingDays;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    // 懒初始化发布卫生（MA-06/C1-003）：timer线程×batch XLang线程并发首访（claimNonBroadcastEvents
    // 经AOP进入）下的检查-然后-行动会构造双实例——volatile + DCL（范本 DynCodeGen.getTenantCodeCache）
    private volatile BroadcastEventProcessor broadcastProcessor;

    private volatile NonBroadcastEventProcessor nonBroadcastProcessor;

    private boolean nonBroadcastAutoScanEnabled = true;

    private long minProcessDelay = 10000;

    private long leaseTimeout = 10000;

    private IntRangeSet assignedPartitions = IntRangeBean.shortRange().toRangeSet();

    private final Map<String, List<SubscriptionState>> durableSubscriptions = new ConcurrentHashMap<>();

    private LocalMessageService localService = new LocalMessageService() {
        @Override
        public void send(String topic, Object message, MessageSendOptions options) {
            SysDaoMessageService.this.send(topic, message, options);
        }
    };

    public void setRetryPolicy(IRetryPolicy<SysDaoMessageService> retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public void setTopicRetryPolicies(Map<String, IRetryPolicy<SysDaoMessageService>> topicRetryPolicies) {
        this.topicRetryPolicies = topicRetryPolicies;
    }

    public void setMinProcessDelay(long minProcessDelay) {
        this.minProcessDelay = minProcessDelay;
    }

    public void setLeaseTimeout(long leaseTimeout) {
        this.leaseTimeout = leaseTimeout;
    }

    /**
     * 单次事件消费的同步等待总预算（毫秒）。消费者返回未完成的 future 时等待不能无界（C8-001：
     * syncGet 永久阻塞使 scheduleWithFixedDelay 后续轮询永不开始——全部事件消费静默停摆）；
     * 预算耗尽按 ConsumeLater 回 WAITING 重排。独立配置键与 leaseTimeout 解耦（leaseTimeout
     * 另承载 MA-01 续租窗口语义，混用会隐改等待预算）。
     */
    private long consumeWaitTimeout = 10000;

    @InjectValue("@cfg:nop.sys.event.consume-wait-timeout|10000")
    public void setConsumeWaitTimeout(long consumeWaitTimeout) {
        this.consumeWaitTimeout = consumeWaitTimeout;
    }

    public void setAssignedPartitions(IntRangeSet assignedPartitions) {
        this.assignedPartitions = assignedPartitions;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public void setStartGap(int startGap) {
        this.startGap = startGap;
    }

    public void setMaxScanLoops(int maxScanLoops) {
        this.maxScanLoops = maxScanLoops;
    }

    public void setNonBroadcastAutoScanEnabled(boolean nonBroadcastAutoScanEnabled) {
        this.nonBroadcastAutoScanEnabled = nonBroadcastAutoScanEnabled;
    }

    public void setCheckBroadcastEventInterval(Duration checkBroadcastEventInterval) {
        this.checkBroadcastEventInterval = checkBroadcastEventInterval;
    }

    public void setCheckSimpleEventInterval(Duration checkSimpleEventInterval) {
        this.checkSimpleEventInterval = checkSimpleEventInterval;
    }

    public void setExecutor(IThreadPoolExecutor executor) {
        this.executor = executor;
    }

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }


    @Override
    public void doStart() {
        if (timer == null)
            timer = GlobalExecutors.globalTimer();
        if (executor == null)
            executor = GlobalExecutors.globalWorker();

        checkBroadcastFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::processBroadcastEvent,
                checkBroadcastEventInterval.toMillis(), checkBroadcastEventInterval.toMillis(), TimeUnit.MILLISECONDS);

        if (nonBroadcastAutoScanEnabled) {
            checkNonBroadcastFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::processNonBroadcastEvent,
                    checkSimpleEventInterval.toMillis(), checkSimpleEventInterval.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            LOG.info("nop.sys.message.non-broadcast-auto-scan-disabled");
        }

        if (cleanupRetentionDays > 0) {
            cleanupFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::cleanupExpiredEvents,
                    cleanupInterval.toMillis(), cleanupInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * WI6 (plan 2331): producer-side backpressure probe — the number of unconsumed
     * messages retained for the topic (WAITING + CLAIMED rows on the indexed
     * (eventTopic, eventStatus) pair). Consumed rows stay until retention cleanup, so
     * only the status filter separates backlog from history.
     */
    @Override
    public long backlog(String topic) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopSysEvent.PROP_NAME_eventTopic, topic));
        query.addFilter(FilterBeans.in(NopSysEvent.PROP_NAME_eventStatus,
                List.of(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING,
                        NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED)));
        return dao().countByQuery(query);
    }

    /**
     * 清理已处理/过期事件：PROCESSED/FAILED 超保留期、WAITING/CLAIMED 超长滞留（无订阅者
     * topic 的事件永不被扫描也永不过期）、以及超出保留期的广播行。周期任务异常保护（同process入口）。
     */
    protected void cleanupExpiredEvents() {
        try {
            Timestamp expireBefore = new Timestamp(
                    dao().getDbEstimatedClock().getMaxCurrentTimeMillis() - cleanupRetentionDays * 24L * 3600_000L);

            QueryBean eventQuery = new QueryBean();
            eventQuery.addFilter(FilterBeans.in(NopSysEvent.PROP_NAME_eventStatus,
                    List.of(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED,
                            NopSysDaoConstants.SYS_EVENT_STATUS_FAILED)));
            eventQuery.addFilter(FilterBeans.lt(NopSysEvent.PROP_NAME_eventTime, expireBefore));
            dao().deleteByQuery(eventQuery);

            if (cleanupStaleWaitingDays > 0) {
                Timestamp staleBefore = new Timestamp(
                        dao().getDbEstimatedClock().getMaxCurrentTimeMillis() - cleanupStaleWaitingDays * 24L * 3600_000L);
                QueryBean staleQuery = new QueryBean();
                staleQuery.addFilter(FilterBeans.in(NopSysEvent.PROP_NAME_eventStatus,
                        List.of(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING,
                                NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED)));
                staleQuery.addFilter(FilterBeans.lt(NopSysEvent.PROP_NAME_eventTime, staleBefore));
                dao().deleteByQuery(staleQuery);
            }

            QueryBean broadcastQuery = new QueryBean();
            broadcastQuery.addFilter(FilterBeans.lt(NopSysBroadcastEvent.PROP_NAME_eventTime, expireBefore));
            broadcastDao().deleteByQuery(broadcastQuery);
        } catch (Exception e) {
            LOG.error("nop.sys.message.cleanup-events-fail", e);
        }
    }

    @Override
    public void doStop() {
        if (checkBroadcastFuture != null)
            checkBroadcastFuture.cancel(false);
        if (checkNonBroadcastFuture != null)
            checkNonBroadcastFuture.cancel(false);
        if (cleanupFuture != null)
            cleanupFuture.cancel(false);
    }

    protected void processNonBroadcastEvent() {
        // BindScheduledExecutor的周期任务抛Throwable即永久停摆：fetchCandidates/claim/
        // ensureStartTimeInitialized的DB异常必须吞掉记日志，让下个周期继续（事件消费不能因一次抖动死亡）
        try {
            ensureNonBroadcastProcessor();
            nonBroadcastProcessor.process();
        } catch (Exception e) {
            LOG.error("nop.sys.message.process-non-broadcast-fail", e);
        }
    }

    private void ensureNonBroadcastProcessor() {
        NonBroadcastEventProcessor processor = nonBroadcastProcessor;
        if (processor == null) {
            synchronized (this) {
                processor = nonBroadcastProcessor;
                if (processor == null) {
                    processor = new NonBroadcastEventProcessor(
                            daoProvider,
                            localService::getNonBroadcastTopics,
                            event -> invokeDurableConsumers(event.getEventTopic(), event.toApiRequest(), null, false),
                            k -> getHostId(),
                            (event, exception) -> {
                                int count = event.getRetryTimes() != null ? event.getRetryTimes() : 0;
                                IRetryPolicy<SysDaoMessageService> policy = topicRetryPolicies
                                        .getOrDefault(event.getEventTopic(), retryPolicy);
                                return policy.getRetryDelay(exception, count, this);
                            },
                            fetchSize,
                            maxScanLoops,
                            leaseTimeout,
                            minProcessDelay,
                            assignedPartitions
                    );
                    nonBroadcastProcessor = processor;
                }
            }
        }
    }

    // nop-batch-sys 的 non-broadcast-consumer.batch.xml 经 XLang 调用（svc!.claimNonBroadcastEvents），
    // 经 AOP 代理进入故 REQUIRES_NEW 事务语义生效，非死代码
    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    public List<NopSysEvent> claimNonBroadcastEvents(List<NopSysEvent> events) {
        ensureNonBroadcastProcessor();
        return nonBroadcastProcessor.claim(events);
    }

    protected void processNonBroadcastEvent(NopSysEvent event) {
        ensureNonBroadcastProcessor();
        nonBroadcastProcessor.process(event);
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public long getEventDaoEstimatedMaxTime() {
        return dao().getDbEstimatedClock().getMaxCurrentTimeMillis();
    }

    public Timestamp getEstimatedNow() {
        return new Timestamp(getEventDaoEstimatedMaxTime());
    }

    public Set<String> getNonBroadcastTopics() {
        return localService.getNonBroadcastTopics();
    }

    public IDaoProvider getDaoProvider() {
        return daoProvider;
    }

    // nop-batch-sys 的 non-broadcast-consumer.batch.xml 经 XLang 调用（svc!.processClaimedNonBroadcastEvent）
    public void processClaimedNonBroadcastEvent(NopSysEvent event) {
        processNonBroadcastEvent(event);
    }

    protected void processBroadcastEvent() {
        // 同processNonBroadcastEvent：周期任务异常保护
        try {
            ensureBroadcastProcessor();
            broadcastProcessor.process();
        } catch (Exception e) {
            LOG.error("nop.sys.message.process-broadcast-fail", e);
        }
    }

    private void ensureBroadcastProcessor() {
        BroadcastEventProcessor processor = broadcastProcessor;
        if (processor == null) {
            synchronized (this) {
                processor = broadcastProcessor;
                if (processor == null) {
                    processor = new BroadcastEventProcessor(
                            daoProvider,
                            this::getBroadcastTopics,
                            this::dispatchBroadcastToSubscribers,
                            maxScanLoops,
                            fetchSize,
                            startGap
                    );
                    broadcastProcessor = processor;
                }
            }
        }
    }

    private void dispatchBroadcastToSubscribers(String topic, NopSysBroadcastEvent event) {
        List<SubscriptionState> subscriptions = getBroadcastSubscriptions(topic);
        if (subscriptions == null || subscriptions.isEmpty()) {
            LOG.debug("nop.message.ignore-broadcast-when-no-subscriber:topic={}", topic);
            return;
        }

        for (SubscriptionState subscription : subscriptions) {
            // 挂起的订阅者跳过投递（与IMessageSubscription.suspend语义一致）
            if (subscription.isSuspended())
                continue;
            try {
                Object ret = invokeConsumer(subscription.consumer, event.getEventTopic(),
                        fromBroadcastEvent(event), null, true);
                if (ret instanceof ConsumeLater) {
                    LOG.warn("nop.message.broadcast-consume-later-ignored:topic={}", topic);
                }
            } catch (Exception e) {
                LOG.error("nop.message.consume-broadcast-event-error:topic={}", topic, e);
            }
        }
    }

    protected Set<String> getBroadcastTopics() {
        return localService.getBroadcastTopics();
    }

    protected IOrmEntityDao<NopSysEvent> dao() {
        return (IOrmEntityDao<NopSysEvent>) daoProvider.daoFor(NopSysEvent.class);
    }

    protected IOrmEntityDao<NopSysBroadcastEvent> broadcastDao() {
        return (IOrmEntityDao<NopSysBroadcastEvent>) daoProvider.daoFor(NopSysBroadcastEvent.class);
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "sysdao", WARN_ONCE);
        try {
            saveMessage(topic, message, options == null ? 0 : options.getDelay());
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "sysdao", WARN_ONCE);
        try {
            for (TopicMessage message : messages) {
                saveMessage(message.getTopic(), message.getMessage(), options == null ? 0 : options.getDelay());
            }
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
        return FutureHelper.success(null);
    }

    protected ApiRequest<Map<String, Object>> fromSysEvent(NopSysEvent event) {
        return SysEventHelper.fromSysEvent(event);
    }

    protected ApiRequest<Map<String, Object>> fromBroadcastEvent(NopSysBroadcastEvent event) {
        return SysEventHelper.fromBroadcastEvent(event);
    }

    protected void toSysEvent(NopSysEvent event, String topic, Object message, long eventTime) {
        SysEventHelper.toSysEvent(event, topic, message, eventTime);
    }

    protected void toBroadcastEvent(NopSysBroadcastEvent event, String topic, Object message, long eventTime) {
        SysEventHelper.toBroadcastEvent(event, topic, message, eventTime);
    }

    protected void saveMessage(String topic, Object message, long delay) {
        if (topic != null && topic.startsWith(TOPIC_PREFIX_BROADCAST)) {
            saveBroadcastEvent(topic, message);
        } else {
            saveNonBroadcastEvent(topic, message, delay);
        }
    }

    protected void saveBroadcastEvent(String topic, Object message) {
        IEntityDao<NopSysBroadcastEvent> dao = broadcastDao();
        IEstimatedClock clock = dao.getDbEstimatedClock();

        NopSysBroadcastEvent event = dao.newEntity();
        toBroadcastEvent(event, topic, message, clock.getMaxCurrentTimeMillis());
        dao.saveEntityDirectly(event);
    }

    protected void saveNonBroadcastEvent(String topic, Object message, long delay) {
        IEntityDao<NopSysEvent> dao = dao();
        IEstimatedClock clock = dao.getDbEstimatedClock();

        long currentTime = clock.getMaxCurrentTimeMillis();
        NopSysEvent event = dao.newEntity();
        toSysEvent(event, topic, message, currentTime);
        if (delay > 0) {
            event.setScheduleTime(new Timestamp(currentTime + delay));
            event.setProcessTime(new Timestamp(currentTime + delay));
        }
        if (event.getPartitionIndex() == null) {
            event.setPartitionIndex(SysEventHelper.DEFAULT_PARTITION_INDEX);
        }
        dao.saveEntityDirectly(event);
    }

    protected List<SubscriptionState> getBroadcastSubscriptions(String topic) {
        List<SubscriptionState> states = durableSubscriptions.get(topic);
        if (states == null) {
            return Collections.emptyList();
        }
        return states;
    }

    protected String getHostId() {
        return AppConfig.hostId();
    }

    protected Object invokeDurableConsumers(String topic, Object message, MessageSendOptions options, boolean broadcast) {
        List<SubscriptionState> subscriptions = durableSubscriptions.get(topic);
        if (subscriptions == null || subscriptions.isEmpty()) {
            // 正常情况下topic已被从轮询集合移除，走到这里多为取消/订阅竞态：事件即将被置为
            // PROCESSED丢弃，必须用warn让丢弃可见而非debug静默
            LOG.warn("nop.message.ignore-message-when-no-consumer:topic={},message={}", topic, message);
            return null;
        }

        if (broadcast) {
            throw new NopSysDaoException("Broadcast durable path should invoke one consumer at a time");
        }

        // 队列语义只投递一个消费者；全部处于挂起状态时返回ConsumeLater让事件回WAITING，
        // 待resume后重投，而不是按无消费者丢弃成PROCESSED或走异常重试耗尽成FAILED
        SubscriptionState target = null;
        for (SubscriptionState subscription : subscriptions) {
            if (!subscription.isSuspended()) {
                target = subscription;
                break;
            }
        }
        if (target == null) {
            LOG.debug("nop.message.all-durable-subscribers-suspended:topic={}", topic);
            return new ConsumeLater(0);
        }
        return invokeConsumer(target.consumer, topic, message, options, false);
    }

    protected Object invokeConsumer(IMessageConsumer consumer, String topic, Object message,
                                    MessageSendOptions options, boolean allowReply) {
        DurableConsumeContext context = new DurableConsumeContext();
        Object ret = consumer.onMessage(topic, message, context);
        if (ret instanceof CompletionStage) {
            ret = waitForConsumeResult(topic, (CompletionStage<Object>) ret);
        }
        // D5(b-prime) signals must never be dispatched as ack-topic replies (WI6).
        if (ret instanceof TerminateConsume || ret instanceof PartialCommit) {
            return ret;
        }
        if (!allowReply && ret instanceof Acknowledge) {
            Object reply = ((Acknowledge) ret).getReplyMessage();
            send(getAckTopic(topic), reply, options);
            return null;
        }
        if (!allowReply && ret != null && !(ret instanceof ConsumeLater)) {
            send(getAckTopic(topic), ret, options);
            return null;
        }
        return ret;
    }

    /**
     * 有界分片等待消费结果（C8-001/裁定⑩）：无界 syncGet 会被挂死消费者永久阻塞轮询线程
     * （scheduleWithFixedDelay 后续轮询永不开始——全部事件消费静默停摆且健康检测不可见）。
     * 等待循环 = 「waitResult 切片 → 续租回调 → 判定继续/中止」：每切片 leaseTimeout/3，
     * 切片超时先执行认领租约续期（事件消费路径经 DispatchLeaseContext 注入——正常慢消费由
     * 续租保活不被接管[C2-102]），续租失败（已被接管）立即中止等待；总预算 consume-wait-timeout
     * 耗尽返回 ConsumeLater(0)（回 WAITING 重排，at-least-once 语义，禁静默当成功）。broadcast
     * 路径无续租回调，纯预算超时。挂死类失败模式=超时，不再存在永久阻塞。
     */
    private Object waitForConsumeResult(String topic, CompletionStage<Object> stage) {
        CompletableFuture<Object> future = stage.toCompletableFuture();
        long slice = Math.max(1, leaseTimeout / 3);
        BooleanSupplier keepAlive = DispatchLeaseContext.current();
        long waited = 0;
        while (true) {
            long waitMs = Math.min(slice, consumeWaitTimeout - waited);
            if (waitMs <= 0) {
                LOG.warn("nop.message.consume-wait-timeout:topic={},waited={}ms", topic, waited);
                return new ConsumeLater(0);
            }
            try {
                return FutureHelper.waitResult(future, waitMs);
            } catch (RuntimeException e) {
                if (future.isDone())
                    throw e; // 真实消费失败（ExecutionException适配），非切片超时
                if (Thread.currentThread().isInterrupted())
                    throw e; // 中断不吞：waitResult已恢复中断位，交由上层按消费失败处置
                waited += waitMs;
                if (keepAlive != null && !keepAlive.getAsBoolean()) {
                    // 租约已被其他节点接管：中止等待，本节点完成写将被fencing拒绝（终态归接管方）
                    throw new NopSysDaoException("event lease lost while waiting for consume result:topic=" + topic);
                }
            }
        }
    }

    public String getAckTopic(String topic) {
        return "ack-" + topic;
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        MessageOptionsValidator.validateSubscribe(CAPABILITIES, options, "sysdao", WARN_ONCE);
        SubscriptionState state = new SubscriptionState(topic, resolveSubscriberId(topic, options), listener, options);
        List<SubscriptionState> subscriptions = durableSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        // 队列语义下非广播消费只投递subscriptions.get(0)：同topic第二个及以后的持久订阅者永远收不到消息，
        // 按常见MQ直觉注册多消费者的使用者会被静默饿死，注册时显式告警
        if (subscriptions.size() >= 1 && !topic.startsWith(TOPIC_PREFIX_BROADCAST)) {
            LOG.warn("nop.sys.message.non-broadcast-topic-multi-subscriber:topic={},subscriberId={} "
                    + "(non-broadcast durable topic delivers to the first subscriber only)", topic, state.subscriberId);
        }
        subscriptions.add(state);
        IMessageSubscription subscription = localService.subscribe(topic, listener, options);
        return new DurableSubscription(subscription, state);
    }

    protected String resolveSubscriberId(String topic, MessageSubscribeOptions options) {
        String subscribeName = options == null ? null : options.getSubscribeName();
        if (!StringHelper.isEmpty(subscribeName)) {
            return subscribeName;
        }
        List<SubscriptionState> subscriptions = durableSubscriptions.get(topic);
        int index = subscriptions == null ? 0 : subscriptions.size();
        return topic + '#' + index;
    }

    protected final class DurableConsumeContext implements IMessageConsumeContext {
        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return SysDaoMessageService.this.sendAsync(topic, message, options);
        }
    }

    protected final class DurableSubscription implements IMessageSubscription {
        private final IMessageSubscription delegate;
        private final SubscriptionState state;

        protected DurableSubscription(IMessageSubscription delegate, SubscriptionState state) {
            this.delegate = delegate;
            this.state = state;
        }

        @Override
        public void cancel() {
            delegate.cancel();
            // 判空+摘键必须compute单步完成（I-02/C1-002）：与subscribe的computeIfAbsent同CHM
            // bin锁互斥。原「get→remove(state)→判空摘键」复合序列可被交错：T2新订阅在T1的
            // remove与判空之间注册时，T1摘键会把T2订阅整体带走且轮询集合残留topic——订阅者
            // 孤儿化，事件被warn后置PROCESSED静默丢弃。getConsumers条件移除必须位于compute
            // 内摘键成功分支：置于compute返回之后将与重订阅的注册重开交错窗
            // （锁序：durableSubscriptions bin → consumers bin 单向）
            durableSubscriptions.compute(state.topic, (topic, subscriptions) -> {
                if (subscriptions == null)
                    return null;
                subscriptions.remove(state);
                if (subscriptions.isEmpty()) {
                    // 最后一个订阅者取消后必须把topic从轮询集合移除：空topic残留会让轮询
                    // 查询持续包含该topic，且期间到达的事件被认领后按无消费者置PROCESSED丢弃
                    localService.getConsumers().remove(topic, new CopyOnWriteArrayList<>());
                    return null;
                }
                return subscriptions;
            });
        }

        @Override
        public boolean isSuspended() {
            return state.isSuspended();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public void suspend() {
            state.suspend();
            delegate.suspend();
        }

        @Override
        public void resume() {
            state.resume();
            delegate.resume();
        }
    }

    protected static final class SubscriptionState {
        private final String topic;
        private final String subscriberId;
        private final IMessageConsumer consumer;
        private final MessageSubscribeOptions options;
        // 多线程轮询分发读写，必须保证可见性
        private volatile boolean suspended;

        protected SubscriptionState(String topic, String subscriberId, IMessageConsumer consumer,
                                    MessageSubscribeOptions options) {
            this.topic = topic;
            this.subscriberId = subscriberId;
            this.consumer = consumer;
            this.options = options;
        }

        protected boolean isSuspended() {
            return suspended;
        }

        protected void suspend() {
            suspended = true;
        }

        protected void resume() {
            suspended = false;
        }
    }
}
