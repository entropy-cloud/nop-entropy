package io.nop.sys.dao.message;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class NonBroadcastEventProcessor {
    static final Logger LOG = LoggerFactory.getLogger(NonBroadcastEventProcessor.class);

    private final IDaoProvider daoProvider;
    private final Supplier<Set<String>> topicsProvider;
    private final Function<NopSysEvent, Object> dispatchCallback;
    private final Function<String, String> hostIdProvider;
    private final BiFunction<NopSysEvent, Throwable, Long> retryDelayProvider;

    private final int fetchSize;
    private final int maxScanLoops;
    private final long leaseTimeout;
    private final long minProcessDelay;
    private final IntRangeSet assignedPartitions;

    public NonBroadcastEventProcessor(IDaoProvider daoProvider,
                                      Supplier<Set<String>> topicsProvider,
                                      Function<NopSysEvent, Object> dispatchCallback,
                                      Function<String, String> hostIdProvider,
                                      BiFunction<NopSysEvent, Throwable, Long> retryDelayProvider,
                                      int fetchSize,
                                      int maxScanLoops,
                                      long leaseTimeout,
                                      long minProcessDelay,
                                      IntRangeSet assignedPartitions) {
        this.daoProvider = daoProvider;
        this.topicsProvider = topicsProvider;
        this.dispatchCallback = dispatchCallback;
        this.hostIdProvider = hostIdProvider;
        this.retryDelayProvider = retryDelayProvider;
        this.fetchSize = fetchSize;
        this.maxScanLoops = maxScanLoops;
        this.leaseTimeout = leaseTimeout;
        this.minProcessDelay = minProcessDelay;
        this.assignedPartitions = assignedPartitions;
    }

    public void process() {
        for (int i = 0; i < maxScanLoops; i++) {
            List<NopSysEvent> events = fetchCandidates();
            if (events.isEmpty())
                return;

            List<NopSysEvent> claimed = claim(events);
            if (claimed.isEmpty())
                continue;

            for (NopSysEvent event : claimed) {
                process(event);
            }
        }
    }

    public List<NopSysEvent> fetchCandidates() {
        Set<String> topics = topicsProvider.get();
        if (topics.isEmpty())
            return Collections.emptyList();

        IOrmEntityDao<NopSysEvent> dao = eventDao();
        long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();

        TreeBean filter = FilterBeans.and(
                FilterBeans.in(NopSysEvent.PROP_NAME_eventTopic, topics),
                FilterBeans.eq(NopSysEvent.PROP_NAME_isBroadcast, false),
                FilterBeans.or(
                        FilterBeans.and(
                                FilterBeans.eq(NopSysEvent.PROP_NAME_eventStatus,
                                        NopSysDaoConstants.SYS_EVENT_STATUS_WAITING),
                                FilterBeans.le(NopSysEvent.PROP_NAME_scheduleTime, new Timestamp(now))
                        ),
                        FilterBeans.and(
                                FilterBeans.eq(NopSysEvent.PROP_NAME_eventStatus,
                                        NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED),
                                FilterBeans.le(NopSysEvent.PROP_NAME_leaseExpireTime, new Timestamp(now))
                        )
                ),
                buildPartitionFilter()
        );

        return dao.findNext(null, filter, null, fetchSize);
    }

    public List<NopSysEvent> claim(List<NopSysEvent> events) {
        IOrmEntityDao<NopSysEvent> dao = eventDao();
        IEstimatedClock clock = dao.getDbEstimatedClock();
        long now = clock.getMaxCurrentTimeMillis();

        for (NopSysEvent event : events) {
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED);
            event.setLeaseOwner(hostIdProvider.apply(null));
            event.setLeaseExpireTime(new Timestamp(now + leaseTimeout));
            event.setProcessTime(new Timestamp(now));
        }

        return dao.tryUpdateManyWithVersionCheck(events);
    }

    public void process(NopSysEvent event) {
        try {
            Object ret = dispatchCallback.apply(event);
            handleResult(event, ret, null);
        } catch (Exception e) {
            handleResult(event, null, e);
        }
    }

    public void handleResult(NopSysEvent event, Object ret, Throwable err) {
        IEntityDao<NopSysEvent> dao = eventDao();
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

    private void handleProcessEventError(IEntityDao<NopSysEvent> dao, NopSysEvent event, Throwable exception) {
        Long delay = retryDelayProvider.apply(event, exception);
        long effectiveDelay = delay != null ? delay : -1;
        if (effectiveDelay < 0) {
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_FAILED);
        } else {
            event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING);
            event.setLeaseOwner(null);
            event.setLeaseExpireTime(null);
            event.setScheduleTime(new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis() + effectiveDelay));
            event.incRetryTimes();
        }
        dao.updateEntityDirectly(event);
    }

    private TreeBean buildPartitionFilter() {
        if (assignedPartitions == null || assignedPartitions.isEmpty()) {
            return null;
        }
        List<TreeBean> rangeFilters = new ArrayList<>();
        for (IntRangeBean range : assignedPartitions.getRanges()) {
            rangeFilters.add(FilterBeans.between(NopSysEvent.PROP_NAME_partitionIndex,
                    range.getOffset(), range.getLast()));
        }
        return FilterBeans.or(rangeFilters);
    }

    @SuppressWarnings("unchecked")
    private IOrmEntityDao<NopSysEvent> eventDao() {
        return (IOrmEntityDao<NopSysEvent>) daoProvider.daoFor(NopSysEvent.class);
    }
}
