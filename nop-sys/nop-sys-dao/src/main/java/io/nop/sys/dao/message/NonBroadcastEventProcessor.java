package io.nop.sys.dao.message;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.TerminateConsume;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Set<Integer> processedPartitions = new HashSet<>();
        for (int i = 0; i < maxScanLoops; i++) {
            List<NopSysEvent> events = fetchCandidates();
            events.removeIf(e -> processedPartitions.contains(e.getPartitionIndex()));
            if (events.isEmpty())
                return;

            List<NopSysEvent> claimed = claim(events);
            if (claimed.isEmpty())
                continue;

            for (NopSysEvent event : claimed) {
                process(event);
                processedPartitions.add(event.getPartitionIndex());
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
                FilterBeans.in(NopSysEvent.PROP_NAME_eventStatus,
                        List.of(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING,
                                NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED)),
                FilterBeans.le(NopSysEvent.PROP_NAME_scheduleTime, new Timestamp(now)),
                buildPartitionFilter()
        );

        List<NopSysEvent> candidates = dao.findNext(null, filter, null, fetchSize * 4);

        Map<Integer, NopSysEvent> heads = new LinkedHashMap<>();
        for (NopSysEvent event : candidates) {
            Integer partitionIndex = event.getPartitionIndex();
            if (heads.containsKey(partitionIndex))
                continue;

            if (event.getEventStatus() == NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED
                    && event.getLeaseExpireTime() != null
                    && event.getLeaseExpireTime().getTime() > now) {
                heads.put(partitionIndex, null);
            } else {
                heads.put(partitionIndex, event);
            }
        }

        List<NopSysEvent> result = new ArrayList<>();
        for (NopSysEvent event : heads.values()) {
            if (event != null)
                result.add(event);
        }
        return result;
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
        // 续租回调以线程绑定上下文提供给invokeConsumer的等待循环（C2-102）：等待切片耗尽即续租，
        // 正常慢消费由续租保活不被接管；出口finally清除防executor线程复用泄漏
        DispatchLeaseContext.install(() -> renewLease(event));
        try {
            Object ret = dispatchCallback.apply(event);
            handleResult(event, ret, null);
        } catch (Exception e) {
            handleResult(event, null, e);
        } finally {
            DispatchLeaseContext.clear();
        }
    }

    /**
     * 认领租约按当前leaseTimeout顺延一个周期。条件更新由ORM乐观锁版本条件承载（认领后被其他节点
     * 接管claim必然bump version，本续约0行→readonly→false），返回false即已被接管，等待方应立即
     * 中止消费（完成写会被fencing拒绝）。updateEntityDirectly自带独立session，不落入消费回调事务。
     */
    public boolean renewLease(NopSysEvent event) {
        if (event.orm_readonly())
            return false; // 此前续租已失守：readonly 实体禁止属性写入（setter 抛 entity-is-readonly）
        IOrmEntityDao<NopSysEvent> dao = eventDao();
        long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();
        event.setLeaseExpireTime(new Timestamp(now + leaseTimeout));
        return dao.tryUpdateWithVersionCheck(event);
    }

    public void handleResult(NopSysEvent event, Object ret, Throwable err) {
        // 只读实体（等待期续租失守已被判失去租约）禁止再触碰任何 setter——ORM 对 readonly
        // 实体的属性写入直接抛 entity-is-readonly。此处提前放弃：事件终态归接管方（fencing 语义）
        if (event.orm_readonly()) {
            LOG.warn("nop.sys.event.complete-abandon-lease-taken:eventId={}", event.getEventId());
            return;
        }

        IEntityDao<NopSysEvent> dao = eventDao();
        try {
            if (err != null) {
                handleProcessEventError(dao, event, err);
                return;
            }

            if (ret instanceof TerminateConsume) {
                // D5(b-prime) termination signal: poison message reaches its failure
                // endpoint — the event is marked processed and never rescheduled (WI6).
                event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED);
                event.setLeaseOwner(null);
                event.setLeaseExpireTime(null);
            } else if (ret instanceof ConsumeLater) {
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
            fencedComplete(event);
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
        fencedComplete(event);
    }

    /**
     * 完成写fencing（C2-102）：更新由ORM乐观锁版本条件承载（认领时版本）。租约被其他节点接管后
     * version已bump，本写0行→orm_readonly——放弃写并显式日志（禁静默吞），事件终态归接管方。
     * 与「超时放弃写」（ConsumeLater回WAITING可重试）区分：本路径为已被接管，写不再生效。
     */
    private void fencedComplete(NopSysEvent event) {
        if (event.orm_readonly()) {
            // renewLease失败中止等待后实体已readonly：失去租约在等待期已判定
            LOG.warn("nop.sys.event.complete-abandon-lease-taken:eventId={}", event.getEventId());
            return;
        }
        event.orm_disableVersionCheckError(true);
        eventDao().updateEntityDirectly(event);
        if (event.orm_readonly())
            LOG.warn("nop.sys.event.complete-abandon-lease-taken:eventId={}", event.getEventId());
    }

    private TreeBean buildPartitionFilter() {
        if (assignedPartitions == null || assignedPartitions.isEmpty()) {
            return null;
        }
        return FilterBeans.inRanges(NopSysEvent.PROP_NAME_partitionIndex, assignedPartitions);
    }

    @SuppressWarnings("unchecked")
    private IOrmEntityDao<NopSysEvent> eventDao() {
        return (IOrmEntityDao<NopSysEvent>) daoProvider.daoFor(NopSysEvent.class);
    }
}
