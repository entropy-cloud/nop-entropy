package io.nop.sys.dao.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
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
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.message.core.local.LocalMessageService;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SysDaoMessageService extends LifeCycleSupport implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(SysDaoMessageService.class);

    private IDaoProvider daoProvider;

    private IScheduledExecutor timer;

    private IThreadPoolExecutor executor;

    private Duration checkInterval = Duration.of(500, ChronoUnit.MILLIS);

    private int fetchSize = 100;

    private int startGap = 5000;

    private IRetryPolicy<SysDaoMessageService> retryPolicy = new RetryPolicy<>();

    private Timestamp startTime;

    private Future<?> checkBroadcastFuture;
    private Future<?> checkNonBroadcastFuture;

    private NopSysEvent lastBroadcastEvent;

    private long minProcessDelay = 10000;

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

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public void setStartGap(int startGap) {
        this.startGap = startGap;
    }

    public void setCheckInterval(Duration checkInterval) {
        this.checkInterval = checkInterval;
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
                checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);

        checkNonBroadcastFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::processNonBroadcastEvent,
                checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void doStop() {
        if (checkBroadcastFuture != null)
            checkBroadcastFuture.cancel(false);
        if (checkNonBroadcastFuture != null)
            checkNonBroadcastFuture.cancel(false);
    }

    protected void processNonBroadcastEvent() {
        do {
            List<NopSysEvent> events = fetchNonBroadcastEvents();
            if (events.isEmpty())
                break;

            List<NopSysEvent> updated = updateScheduleTime(events);
            for (NopSysEvent event : updated) {
                processEvent(event);
            }
        } while (true);
    }

    public List<NopSysEvent> updateScheduleTime(List<NopSysEvent> events) {
        IOrmEntityDao<NopSysEvent> dao = dao();
        IEstimatedClock clock = dao.getDbEstimatedClock();

        for (NopSysEvent event : events) {
            long delay = getRetryDelay(null, event);
            // 最少需要等待一段时间，避免重复执行
            delay = Math.max(minProcessDelay, delay);
            event.setScheduleTime(new Timestamp(clock.getMaxCurrentTimeMillis() + delay));
            event.setProcessTime(new Timestamp(clock.getMaxCurrentTimeMillis()));
        }

        // 并发更新时可能只有部分记录会成功。通过version字段实现乐观锁
        return dao.tryUpdateManyWithVersionCheck(events);
    }

    protected void processEvent(NopSysEvent event) {
        doProcessEvent(event, e -> {
            localService.invokeMessageListener(e.getEventTopic(), e.toApiRequest(), null);
        });
    }

    public void doProcessEvent(NopSysEvent event, Consumer<NopSysEvent> action) {
        IEntityDao<NopSysEvent> dao = dao();
        try {
            action.accept(event);
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED);
            dao.updateEntityDirectly(event);
        } catch (Exception e) {
            LOG.error("nop.err.sys.process-event-fail:", e);
            try {
                handleProcessEventError(dao, event, e);
            } catch (Exception e2) {
                LOG.error("nop.err.sys.handle-process-event-error-fail:", e2);
            }
        }
    }

    protected void handleProcessEventError(IEntityDao<NopSysEvent> dao, NopSysEvent event, Throwable exception) {
        long delay = getRetryDelay(exception, event);
        if (delay < 0) {
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_FAILED);
        } else {
            event.setScheduleTime(new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis() + delay));
            event.incRetryTimes();
        }
        dao.updateEntityDirectly(event);
    }

    protected long getRetryDelay(Throwable exception, NopSysEvent event) {
        int count = event.getRetryTimes() == null ? 0 : event.getRetryTimes();
        return retryPolicy.getRetryDelay(exception, count, this);
    }

    protected void processBroadcastEvent() {
        do {
            List<NopSysEvent> events = fetchBroadcastEvents();
            if (events.isEmpty())
                break;

            for (NopSysEvent event : events) {
                ApiRequest<Map<String, Object>> request = fromSysEvent(event);
                localService.invokeMessageListener(event.getEventTopic(), request, null);
            }
        } while (true);
    }

    protected List<NopSysEvent> fetchBroadcastEvents() {
        Set<String> topics = getBroadcastTopics();
        if (topics.isEmpty())
            return Collections.emptyList();

        // 按照eventId从小到大处理
        IEntityDao<NopSysEvent> dao = dao();
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(NopSysEvent.PROP_NAME_eventTopic, topics));
        query.addFilter(FilterBeans.gt(NopSysEvent.PROP_NAME_eventTime, startTime));
        query.setFilter(FilterBeans.eq(NopSysEvent.PROP_NAME_isBroadcast, true));
        query.addOrderField(NopSysEvent.PROP_NAME_eventId, false);

        List<NopSysEvent> list = dao.findNext(lastBroadcastEvent, query.getFilter(), query.getOrderBy(), fetchSize);
        if (!list.isEmpty()) {
            lastBroadcastEvent = list.get(list.size() - 1);
        }
        return list;
    }

    protected List<NopSysEvent> fetchNonBroadcastEvents() {
        Set<String> topics = localService.getNonBroadcastTopics();
        if (topics.isEmpty())
            return Collections.emptyList();

        // 按照eventId从小到大处理
        IEntityDao<NopSysEvent> dao = dao();
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(NopSysEvent.PROP_NAME_eventTopic, topics));
        query.setFilter(FilterBeans.eq(NopSysEvent.PROP_NAME_isBroadcast, false));
        query.addFilter(FilterBeans.eq(NopSysEvent.PROP_NAME_eventStatus, NopSysDaoConstants.SYS_EVENT_STATUS_WAITING));
        query.addOrderField(NopSysEvent.PROP_NAME_processTime, false);
        query.setLimit(fetchSize);

        List<NopSysEvent> list = dao.findPageByQuery(query);
        return list;
    }

    protected Set<String> getBroadcastTopics() {
        return localService.getBroadcastTopics();
    }

    protected IOrmEntityDao<NopSysEvent> dao() {
        return (IOrmEntityDao<NopSysEvent>) daoProvider.daoFor(NopSysEvent.class);
    }


    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        IEntityDao<NopSysEvent> dao = dao();
        IEstimatedClock clock = dao.getDbEstimatedClock();

        NopSysEvent event = dao.newEntity();
        event.setPartitionIndex(MathHelper.random().nextInt(Short.MAX_VALUE));
        toSysEvent(event, topic, message, clock.getMaxCurrentTimeMillis());

        try {
            dao.saveEntityDirectly(event);
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
        IEntityDao<NopSysEvent> dao = dao();
        IEstimatedClock clock = dao.getDbEstimatedClock();

        long currentTime = clock.getMaxCurrentTimeMillis();
        try {
            for (TopicMessage message : messages) {
                NopSysEvent event = dao.newEntity();
                event.setPartitionIndex(MathHelper.random().nextInt(Short.MAX_VALUE));
                toSysEvent(event, message.getTopic(), message.getMessage(), currentTime);
                dao.saveEntityDirectly(event);
            }
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
        return FutureHelper.success(null);
    }

    protected ApiRequest<Map<String, Object>> fromSysEvent(NopSysEvent event) {
        return SysEventHelper.fromSysEvent(event);
    }

    protected void toSysEvent(NopSysEvent event, String topic, Object message, long eventTime) {
        SysEventHelper.toSysEvent(event, topic, message, eventTime);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return localService.subscribe(topic, listener, options);
    }
}
