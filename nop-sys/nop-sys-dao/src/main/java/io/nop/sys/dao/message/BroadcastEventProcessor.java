package io.nop.sys.dao.message;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.sys.dao.entity.NopSysBroadcastEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class BroadcastEventProcessor {
    static final Logger LOG = LoggerFactory.getLogger(BroadcastEventProcessor.class);

    private final IDaoProvider daoProvider;
    private final Supplier<Set<String>> topicsProvider;
    private final BiConsumer<String, NopSysBroadcastEvent> dispatchCallback;

    private final int fetchSize;
    private final int startGap;

    private Timestamp startTime;
    private final Map<String, NopSysBroadcastEvent> lastEventMap = new ConcurrentHashMap<>();

    public BroadcastEventProcessor(IDaoProvider daoProvider,
                                   Supplier<Set<String>> topicsProvider,
                                   BiConsumer<String, NopSysBroadcastEvent> dispatchCallback,
                                   int fetchSize,
                                   int startGap) {
        this.daoProvider = daoProvider;
        this.topicsProvider = topicsProvider;
        this.dispatchCallback = dispatchCallback;
        this.fetchSize = fetchSize;
        this.startGap = startGap;
    }

    public void process() {
        ensureStartTimeInitialized();
        Set<String> topics = topicsProvider.get();
        for (String topic : topics) {
            try {
                processTopic(topic);
            } catch (Exception e) {
                LOG.error("nop.message.process-broadcast-topic-error:topic={}", topic, e);
            }
        }
    }

    private void ensureStartTimeInitialized() {
        if (startTime != null) {
            return;
        }
        IEstimatedClock clock = broadcastDao().getDbEstimatedClock();
        startTime = new Timestamp(clock.getMinCurrentTimeMillis() - startGap);
    }

    private void processTopic(String topic) {
        IOrmEntityDao<NopSysBroadcastEvent> dao = broadcastDao();
        long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();

        TreeBean filter = FilterBeans.and(
                FilterBeans.eq(NopSysBroadcastEvent.PROP_NAME_eventTopic, topic),
                FilterBeans.ge(NopSysBroadcastEvent.PROP_NAME_eventTime, startTime),
                FilterBeans.le(NopSysBroadcastEvent.PROP_NAME_eventTime, new Timestamp(now))
        );

        NopSysBroadcastEvent last = lastEventMap.get(topic);
        List<NopSysBroadcastEvent> batch = dao.findNext(last, filter, null, fetchSize);

        if (batch.isEmpty()) {
            return;
        }

        for (NopSysBroadcastEvent event : batch) {
            dispatchCallback.accept(topic, event);
        }

        lastEventMap.put(topic, batch.get(batch.size() - 1));
    }

    @SuppressWarnings("unchecked")
    private IOrmEntityDao<NopSysBroadcastEvent> broadcastDao() {
        return (IOrmEntityDao<NopSysBroadcastEvent>) daoProvider.daoFor(NopSysBroadcastEvent.class);
    }
}
