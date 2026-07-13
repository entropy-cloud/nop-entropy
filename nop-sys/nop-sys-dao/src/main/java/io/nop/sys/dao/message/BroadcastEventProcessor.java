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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class BroadcastEventProcessor {
    static final Logger LOG = LoggerFactory.getLogger(BroadcastEventProcessor.class);

    private final IDaoProvider daoProvider;
    private final Supplier<Set<String>> topicsProvider;
    private final BiConsumer<String, NopSysBroadcastEvent> dispatchCallback;

    private final int fetchSize;
    private final int startGap;
    private final int maxScanLoops;

    private Timestamp startTime;
    private NopSysBroadcastEvent lastEvent;

    public BroadcastEventProcessor(IDaoProvider daoProvider,
                                   Supplier<Set<String>> topicsProvider,
                                   BiConsumer<String, NopSysBroadcastEvent> dispatchCallback,
                                   int maxScanLoops,
                                   int fetchSize,
                                   int startGap) {
        this.daoProvider = daoProvider;
        this.topicsProvider = topicsProvider;
        this.dispatchCallback = dispatchCallback;
        this.maxScanLoops = maxScanLoops;
        this.fetchSize = fetchSize;
        this.startGap = startGap;
    }

    public void process() {
        ensureStartTimeInitialized();
        Set<String> topics = topicsProvider.get();
        if (topics.isEmpty())
            return;

        IOrmEntityDao<NopSysBroadcastEvent> dao = broadcastDao();

        for (int i = 0; i < maxScanLoops; i++) {
            long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();

            TreeBean filter = FilterBeans.and(
                    FilterBeans.in(NopSysBroadcastEvent.PROP_NAME_eventTopic, topics),
                    FilterBeans.ge(NopSysBroadcastEvent.PROP_NAME_eventTime, startTime),
                    FilterBeans.le(NopSysBroadcastEvent.PROP_NAME_eventTime, new Timestamp(now))
            );

            List<NopSysBroadcastEvent> batch = dao.findNext(lastEvent, filter, null, fetchSize);
            if (batch.isEmpty())
                return;

            for (NopSysBroadcastEvent event : batch) {
                dispatchCallback.accept(event.getEventTopic(), event);
            }

            lastEvent = batch.get(batch.size() - 1);
        }
    }

    private void ensureStartTimeInitialized() {
        if (startTime != null)
            return;
        IEstimatedClock clock = broadcastDao().getDbEstimatedClock();
        startTime = new Timestamp(clock.getMinCurrentTimeMillis() - startGap);
    }

    @SuppressWarnings("unchecked")
    private IOrmEntityDao<NopSysBroadcastEvent> broadcastDao() {
        return (IOrmEntityDao<NopSysBroadcastEvent>) daoProvider.daoFor(NopSysBroadcastEvent.class);
    }
}
