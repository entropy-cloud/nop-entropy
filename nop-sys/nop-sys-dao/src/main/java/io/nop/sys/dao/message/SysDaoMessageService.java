package io.nop.sys.dao.message;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.TopicMessage;
import io.nop.api.core.time.IEstimatedClock;
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
import io.nop.sys.dao.NopSysDaoException;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysBroadcastEvent;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class SysDaoMessageService extends LifeCycleSupport implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(SysDaoMessageService.class);

    private IDaoProvider daoProvider;

    private IScheduledExecutor timer;

    private IThreadPoolExecutor executor;

    private Duration checkBroadcastEventInterval = Duration.of(500, ChronoUnit.MILLIS);

    private Duration checkSimpleEventInterval = Duration.of(500, ChronoUnit.MILLIS);

    private int fetchSize = 100;

    private int startGap = 5000;

    private IRetryPolicy<SysDaoMessageService> retryPolicy = new RetryPolicy<>();

    private Timestamp startTime;

    private Future<?> checkBroadcastFuture;
    private Future<?> checkNonBroadcastFuture;

    private BroadcastEventProcessor broadcastProcessor;

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

    public void setMinProcessDelay(long minProcessDelay) {
        this.minProcessDelay = minProcessDelay;
    }

    public void setLeaseTimeout(long leaseTimeout) {
        this.leaseTimeout = leaseTimeout;
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

        IEstimatedClock clock = dao().getDbEstimatedClock();
        startTime = new Timestamp(clock.getMinCurrentTimeMillis() - startGap);

        checkBroadcastFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::processBroadcastEvent,
                checkBroadcastEventInterval.toMillis(), checkBroadcastEventInterval.toMillis(), TimeUnit.MILLISECONDS);

        checkNonBroadcastFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::processNonBroadcastEvent,
                checkSimpleEventInterval.toMillis(), checkSimpleEventInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void doStop() {
        if (checkBroadcastFuture != null)
            checkBroadcastFuture.cancel(false);
        if (checkNonBroadcastFuture != null)
            checkNonBroadcastFuture.cancel(false);
    }

    protected void processNonBroadcastEvent() {
        List<NopSysEvent> events = fetchNonBroadcastEvents();
        if (events.isEmpty()) {
            return;
        }

        List<NopSysEvent> claimed = claimNonBroadcastEvents(events);
        if (claimed.isEmpty()) {
            return;
        }

        for (NopSysEvent event : claimed) {
            processNonBroadcastEvent(event);
        }
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    public List<NopSysEvent> claimNonBroadcastEvents(List<NopSysEvent> events) {
        IOrmEntityDao<NopSysEvent> dao = dao();
        IEstimatedClock clock = dao.getDbEstimatedClock();
        long now = clock.getMaxCurrentTimeMillis();

        for (NopSysEvent event : events) {
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED);
            event.setLeaseOwner(getHostId());
            event.setLeaseExpireTime(new Timestamp(now + leaseTimeout));
            event.setProcessTime(new Timestamp(now));
        }

        return dao.tryUpdateManyWithVersionCheck(events);
    }

    protected void processNonBroadcastEvent(NopSysEvent event) {
        try {
            Object ret = invokeDurableConsumers(event.getEventTopic(), event.toApiRequest(), null, false);
            handleNonBroadcastProcessResult(event, ret, null);
        } catch (Exception e) {
            handleNonBroadcastProcessResult(event, null, e);
        }
    }

    protected void handleNonBroadcastProcessResult(NopSysEvent event, Object ret, Exception err) {
        IEntityDao<NopSysEvent> dao = dao();
        try {
            if (err != null) {
                handleProcessEventError(dao, event, err);
                return;
            }

            if (ret instanceof ConsumeLater) {
                long delay = Math.max(minProcessDelay, ((ConsumeLater) ret).getDelay());
                event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING);
                event.setLeaseOwner(null);
                event.setLeaseExpireTime(null);
                event.setScheduleTime(new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis() + delay));
                event.incRetryTimes();
            } else {
                event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED);
                event.setLeaseOwner(null);
                event.setLeaseExpireTime(null);
            }
            dao.updateEntityDirectly(event);
        } catch (Exception e) {
            LOG.error("nop.err.sys.process-event-fail", e);
            try {
                handleProcessEventError(dao, event, e);
            } catch (Exception e2) {
                LOG.error("nop.err.sys.handle-process-event-error-fail", e2);
            }
        }
    }

    protected void handleProcessEventError(IEntityDao<NopSysEvent> dao, NopSysEvent event, Throwable exception) {
        long delay = getRetryDelay(exception, event);
        if (delay < 0) {
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_FAILED);
        } else {
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING);
            event.setLeaseOwner(null);
            event.setLeaseExpireTime(null);
            event.setScheduleTime(new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis() + delay));
            event.incRetryTimes();
        }
        dao.updateEntityDirectly(event);
    }

    protected long getRetryDelay(Throwable exception, NopSysEvent event) {
        int count = event.getRetryTimes() == null ? 0 : event.getRetryTimes();
        return retryPolicy.getRetryDelay(exception, count, this);
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public List<NopSysEvent> fetchExecutableNonBroadcastEvents(int batchSize) {
        return fetchNonBroadcastEvents(batchSize);
    }

    public void processClaimedNonBroadcastEvent(NopSysEvent event) {
        processNonBroadcastEvent(event);
    }

    public <R, T> T runInNewTransaction(Function<R, T> fn, R request, TransactionPropagation propagation) {
        return fn.apply(request);
    }

    protected void processBroadcastEvent() {
        ensureBroadcastProcessor();
        broadcastProcessor.process();
    }

    private void ensureBroadcastProcessor() {
        if (broadcastProcessor == null) {
            broadcastProcessor = new BroadcastEventProcessor(
                    daoProvider,
                    this::getBroadcastTopics,
                    this::dispatchBroadcastToSubscribers,
                    fetchSize,
                    startGap
            );
        }
    }

    private void dispatchBroadcastToSubscribers(String topic, NopSysBroadcastEvent event) {
        List<SubscriptionState> subscriptions = getBroadcastSubscriptions(topic);
        if (subscriptions == null || subscriptions.isEmpty()) {
            LOG.debug("nop.message.ignore-broadcast-when-no-subscriber:topic={}", topic);
            return;
        }

        for (SubscriptionState subscription : subscriptions) {
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

    protected List<NopSysEvent> fetchNonBroadcastEvents() {
        return fetchNonBroadcastEvents(fetchSize * 4);
    }

    protected List<NopSysEvent> fetchNonBroadcastEvents(int batchSize) {
        ensureStartTimeInitialized();
        Set<String> topics = localService.getNonBroadcastTopics();
        if (topics.isEmpty())
            return Collections.emptyList();

        IEntityDao<NopSysEvent> dao = dao();
        long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();
        Map<Integer, NopSysEvent> heads = new LinkedHashMap<>();
        long offset = 0;
        while (countExecutableHeads(heads) < fetchSize) {
            QueryBean query = new QueryBean();
            query.setOffset(offset);
            query.setLimit(batchSize);
            query.addFilter(FilterBeans.in(NopSysEvent.PROP_NAME_eventTopic, topics));
            query.addFilter(FilterBeans.eq(NopSysEvent.PROP_NAME_isBroadcast, false));
            query.addFilter(FilterBeans.in(NopSysEvent.PROP_NAME_eventStatus,
                    List.of(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED)));
            query.addFilter(FilterBeans.le(NopSysEvent.PROP_NAME_scheduleTime, new Timestamp(now)));
            addPartitionFilter(query, assignedPartitions, NopSysEvent.PROP_NAME_partitionIndex);
            query.addOrderField(NopSysEvent.PROP_NAME_partitionIndex, false);
            query.addOrderField(NopSysEvent.PROP_NAME_processTime, false);
            query.addOrderField(NopSysEvent.PROP_NAME_eventId, false);

            List<NopSysEvent> candidates = dao.findPageByQuery(query);
            if (candidates.isEmpty()) {
                break;
            }

            for (NopSysEvent event : candidates) {
                Integer partitionIndex = event.getPartitionIndex();
                if (heads.containsKey(partitionIndex)) {
                    continue;
                }

                if (event.getEventStatus() == NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED && !isExpiredLease(event, now)) {
                    heads.put(partitionIndex, null);
                    continue;
                }

                heads.put(partitionIndex, event);
                if (countExecutableHeads(heads) >= fetchSize) {
                    break;
                }
            }

            if (candidates.size() < batchSize || countExecutableHeads(heads) >= fetchSize) {
                break;
            }
            offset += candidates.size();
        }

        List<NopSysEvent> result = new ArrayList<>();
        for (NopSysEvent event : heads.values()) {
            if (event != null) {
                result.add(event);
            }
        }
        return result;
    }

    protected boolean isExpiredLease(NopSysEvent event, long now) {
        Timestamp leaseExpireTime = event.getLeaseExpireTime();
        return leaseExpireTime == null || leaseExpireTime.getTime() <= now;
    }

    protected int countExecutableHeads(Map<Integer, NopSysEvent> heads) {
        int count = 0;
        for (NopSysEvent event : heads.values()) {
            if (event != null) {
                count++;
            }
        }
        return count;
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

    protected void ensureStartTimeInitialized() {
        if (startTime != null) {
            return;
        }
        IEstimatedClock clock = dao().getDbEstimatedClock();
        startTime = new Timestamp(clock.getMinCurrentTimeMillis() - startGap);
    }


    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        try {
            saveMessage(topic, message, options == null ? 0 : options.getDelay());
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
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
        if (topic != null && topic.startsWith("bro-")) {
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
            LOG.debug("nop.message.ignore-message-when-no-consumer:topic={},message={}", topic, message);
            return null;
        }

        if (broadcast) {
            throw new NopSysDaoException("Broadcast durable path should invoke one consumer at a time");
        }

        return invokeConsumer(subscriptions.get(0).consumer, topic, message, options, false);
    }

    protected Object invokeConsumer(IMessageConsumer consumer, String topic, Object message,
                                    MessageSendOptions options, boolean allowReply) {
        DurableConsumeContext context = new DurableConsumeContext();
        Object ret = consumer.onMessage(topic, message, context);
        if (ret instanceof CompletionStage) {
            ret = FutureHelper.syncGet((CompletionStage<Object>) ret);
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

    public String getAckTopic(String topic) {
        return "ack-" + topic;
    }

    protected void addPartitionFilter(QueryBean query, IntRangeSet partitions, String partitionProp) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }

        List<TreeBean> rangeFilters = new ArrayList<>();
        for (IntRangeBean range : partitions.getRanges()) {
            rangeFilters.add(FilterBeans.between(partitionProp, range.getOffset(), range.getLast()));
        }
        query.addFilter(FilterBeans.or(rangeFilters));
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        SubscriptionState state = new SubscriptionState(topic, resolveSubscriberId(topic, options), listener, options);
        durableSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(state);
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
            List<SubscriptionState> subscriptions = durableSubscriptions.get(state.topic);
            if (subscriptions != null) {
                subscriptions.remove(state);
            }
        }

        @Override
        public boolean isSuspended() {
            return delegate.isSuspended();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public void suspend() {
            delegate.suspend();
        }

        @Override
        public void resume() {
            delegate.resume();
        }
    }

    protected static final class SubscriptionState {
        private final String topic;
        private final String subscriberId;
        private final IMessageConsumer consumer;
        private final MessageSubscribeOptions options;

        protected SubscriptionState(String topic, String subscriberId, IMessageConsumer consumer,
                                    MessageSubscribeOptions options) {
            this.topic = topic;
            this.subscriberId = subscriberId;
            this.consumer = consumer;
            this.options = options;
        }
    }
}
