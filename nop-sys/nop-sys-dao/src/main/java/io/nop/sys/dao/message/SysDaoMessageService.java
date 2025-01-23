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
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.message.core.local.LocalMessageService;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;

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

public class SysDaoMessageService extends LifeCycleSupport implements IMessageService {
    private IDaoProvider daoProvider;

    private IScheduledExecutor timer;

    private IThreadPoolExecutor executor;

    private Duration checkInterval = Duration.of(500, ChronoUnit.MILLIS);

    private int fetchSize = 100;

    private int startGap = 5000;

    private Timestamp startTime;

    private Future<?> checkFuture;

    private NopSysEvent lastBroadcastEvent;

    private LocalMessageService localService = new LocalMessageService() {
        @Override
        public void send(String topic, Object message, MessageSendOptions options) {
            SysDaoMessageService.this.send(topic, message, options);
        }
    };

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

        checkFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::processBroadcastEvent,
                checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void doStop() {
        if (checkFuture != null)
            checkFuture.cancel(false);
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
        query.addOrderField(NopSysEvent.PROP_NAME_eventId, true);

        List<NopSysEvent> list = dao.findNext(lastBroadcastEvent, query.getFilter(), query.getOrderBy(), fetchSize);
        if (!list.isEmpty()) {
            lastBroadcastEvent = list.get(list.size() - 1);
        }
        return list;
    }

    protected Set<String> getBroadcastTopics() {
        return localService.getBroadcastTopics();
    }

    protected IEntityDao<NopSysEvent> dao() {
        return daoProvider.daoFor(NopSysEvent.class);
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
