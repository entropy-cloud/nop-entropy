package io.nop.sys.dao.message;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.NopSysDaoException;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-01 子批③（C2-102，plan 2525 裁定①）：事件认领租约续期 + 完成写 fencing。
 *
 * <p>缺陷面：认领为一次性租约，消费超过 leaseTimeout 即被第二节点 CAS 接管，先完成方的
 * 完成写与接管方冲突。修复面：①等待循环按切片续租（renewLease，乐观锁版本 CAS 承载）；
 * ②完成写改条件写放弃语义（fencedComplete——0 行显式放弃日志，禁静默吞，终态归接管方）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestEventLeaseFencing_MA01 extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    private SysDaoMessageService service;

    @BeforeEach
    public void setUp() {
        service = new SysDaoMessageService();
        service.setDaoProvider(daoProvider);
        service.setAssignedPartitions(IntRangeSet.parse("0,32767"));
        service.setFetchSize(20);
        service.setMinProcessDelay(1);
        service.setLeaseTimeout(1000);
    }

    private static ApiRequest<Map<String, Object>> request(String id) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", id));
        return request;
    }

    private NonBroadcastEventProcessor newProcessor(String hostId, long leaseTimeout,
                                                    java.util.function.Function<NopSysEvent, Object> dispatch) {
        return new NonBroadcastEventProcessor(
                daoProvider,
                () -> Set.of("fencing-topic"),
                dispatch,
                k -> hostId,
                (event, exception) -> 0L,
                20,
                10,
                leaseTimeout,
                1,
                IntRangeSet.parse("0,32767"));
    }

    private NopSysEvent sendAndWaitForRow(String topic, String id) {
        service.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return null;
            }
        }, null);
        service.send(topic, request(id), null);
        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        assertEquals(1, events.size());
        return events.get(0);
    }

    /**
     * 续租钉：认领后续租成功且 DB 租约顺延（乐观锁版本条件承载，实体 version 同步前进）。
     */
    @Test
    public void testRenewLeaseExtendsClaimedLease() throws Exception {
        NopSysEvent event = sendAndWaitForRow("fencing-topic", "evt-renew");

        NonBroadcastEventProcessor proc = newProcessor("node-1", 1000, e -> null);
        List<NopSysEvent> claimed = proc.claim(List.of(event));
        assertEquals(1, claimed.size(), "claim CAS must succeed on a fresh WAITING row");

        java.sql.Timestamp before = claimed.get(0).getLeaseExpireTime();
        Long versionAtClaim = claimed.get(0).getVersion();
        // 同毫秒内续租值相等则实体不脏、update 被跳过——错开毫秒保证本钉驱动真实续约写
        Thread.sleep(5);
        assertTrue(proc.renewLease(claimed.get(0)), "renewal by the lease owner must succeed");

        NopSysEvent row = daoProvider.daoFor(NopSysEvent.class).getEntityById(claimed.get(0).getEventId());
        assertNotNull(row.getLeaseExpireTime());
        assertFalse(row.getLeaseExpireTime().before(before),
                "renewal must keep leaseExpireTime at claim level or forward");
        assertEquals(Long.valueOf(versionAtClaim + 1), row.getVersion(),
                "renewal must advance the row version via the CAS write");

        // 实体 version 已随续约回写：完成写（PROCESSED）仍能以最新版本条件命中
        proc.handleResult(claimed.get(0), "done", null);
        NopSysEvent after = daoProvider.daoFor(NopSysEvent.class).getEntityById(claimed.get(0).getEventId());
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED, after.getEventStatus(),
                "completion write after renewals must succeed — entity version stayed in sync");
    }

    /**
     * 接管钉：租约过期后第二节点接管（fresh 读 + claim CAS），原持有者续租被版本条件拒绝；
     * 失守后的完成/错误路径不得误入 retryDelayProvider 错误重排（已被接管 ≠ 消费失败——
     * 放弃写显式可见，终态归接管方）。
     */
    @Test
    public void testRenewLeaseRejectedAfterTakeover() throws Exception {
        NopSysEvent event = sendAndWaitForRow("fencing-topic", "evt-takeover");
        AtomicInteger retryDelayInvocations = new AtomicInteger();

        NonBroadcastEventProcessor proc1 = new NonBroadcastEventProcessor(
                daoProvider,
                () -> Set.of("fencing-topic"),
                e -> null,
                k -> "node-1",
                (event1, exception) -> {
                    retryDelayInvocations.incrementAndGet();
                    return 0L;
                },
                20, 10, 50, 1, IntRangeSet.parse("0,32767"));
        assertEquals(1, proc1.claim(List.of(event)).size(), "node-1 claim must succeed");

        // 租约（50ms）过期后 node-2 以 fresh 副本认领接管
        Thread.sleep(100);
        NopSysEvent fresh = daoProvider.daoFor(NopSysEvent.class).getEntityById(event.getEventId());
        NonBroadcastEventProcessor proc2 = newProcessor("node-2", 10000, e -> null);
        assertEquals(1, proc2.claim(List.of(fresh)).size(), "node-2 must take over the expired lease");

        // node-1 的过期副本续租：版本条件失配 → 0 行 → false（已被接管），实体转 readonly
        assertFalse(proc1.renewLease(event),
                "the deposed owner's renewal must be rejected by the version CAS");

        // 失守后中止等待的收尾（等待循环抛 lease-lost 异常 → 错误路径收尾）：
        // 必须按 fencing 放弃处置——retryDelayProvider 是消费失败通道，不得被接管放弃误触发
        proc1.handleResult(event, null, new NopSysDaoException("lease lost while waiting"));
        assertEquals(0, retryDelayInvocations.get(),
                "fenced abandonment after lease loss must not be routed through retryDelayProvider"
                        + " (taken-over is not a consume failure)");
        NopSysEvent row = daoProvider.daoFor(NopSysEvent.class).getEntityById(event.getEventId());
        assertEquals("node-2", row.getLeaseOwner(),
                "the takeover owner's lease info must survive the deposed owner's abandon");
    }

    /**
     * fencing 完成写钉：接管后原持有者的完成写必须放弃（显式放弃语义，事件终态归接管方，
     * retryDelayProvider 不被误触发——区别于超时放弃写的可重试语义）。
     */
    @Test
    public void testCompletionWriteAbandonedAfterTakeover() throws Exception {
        NopSysEvent event = sendAndWaitForRow("fencing-topic", "evt-fence");
        AtomicInteger retryDelayInvocations = new AtomicInteger();

        NonBroadcastEventProcessor proc1 = new NonBroadcastEventProcessor(
                daoProvider,
                () -> Set.of("fencing-topic"),
                e -> null,
                k -> "node-1",
                (event1, exception) -> {
                    retryDelayInvocations.incrementAndGet();
                    return 0L;
                },
                20, 10, 50, 1, IntRangeSet.parse("0,32767"));
        assertEquals(1, proc1.claim(List.of(event)).size());

        Thread.sleep(100);
        NopSysEvent fresh = daoProvider.daoFor(NopSysEvent.class).getEntityById(event.getEventId());
        NonBroadcastEventProcessor proc2 = newProcessor("node-2", 10000, e -> null);
        assertEquals(1, proc2.claim(List.of(fresh)).size(), "node-2 must take over");

        // node-1 慢消费完成，尝试写 PROCESSED——被 fencing 放弃：无异常逃逸、无终态改写、
        // 不走 retryDelayProvider 错误重排（那是消费失败语义；已被接管 ≠ 消费失败）
        proc1.handleResult(event, "done", null);

        NopSysEvent row = daoProvider.daoFor(NopSysEvent.class).getEntityById(event.getEventId());
        assertEquals("node-2", row.getLeaseOwner(),
                "the takeover owner's lease info must survive the deposed owner's completion write");
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED, row.getEventStatus(),
                "terminal state belongs to the takeover owner — the fenced write must not flip it");
        assertEquals(0, retryDelayInvocations.get(),
                "fenced abandonment is not a consume failure: retryDelayProvider must not be invoked");
    }

    /**
     * 全链钉（裁定⑩×裁定①协同）：慢消费等待期间第二节点接管 → 续租失守中止等待 →
     * 完成写 fencing 放弃 → 终态归接管方；轮询线程不被挂死。
     */
    @Test
    public void testWaitAbortsWhenLeaseLostMidConsumption() throws Exception {
        service.setConsumeWaitTimeout(10000);
        CompletableFuture<Object> never = new CompletableFuture<>();
        service.subscribe("fencing-topic", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return never;
            }
        }, null);
        service.send("fencing-topic", request("evt-chain"), null);
        service.setLeaseTimeout(300);

        Thread worker = new Thread(() -> service.processNonBroadcastEvent(), "i05-chain-worker");
        worker.start();

        // 等待轮询线程完成 claim（行进入 CLAIMED 且带 leaseOwner），再以 fresh 副本接管
        IEntityDao<NopSysEvent> dao = daoProvider.daoFor(NopSysEvent.class);
        Long eventId = dao.findAll().get(0).getEventId();
        NopSysEvent claimed = null;
        for (int i = 0; i < 100 && claimed == null; i++) {
            NopSysEvent row = dao.getEntityById(eventId);
            if (row.getEventStatus() != null
                    && row.getEventStatus() == NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED
                    && row.getLeaseOwner() != null) {
                claimed = row;
            } else {
                Thread.sleep(20);
            }
        }
        assertNotNull(claimed, "worker must claim the event");

        // 第二节点接管（fresh 读 + claim CAS，重试至成功——与轮询线程的切片续租存在合法竞争，
        // 任一次 fresh 认领成功即代表接管完成）
        NonBroadcastEventProcessor proc2 = newProcessor("node-2", 10000, e -> null);
        boolean taken = false;
        for (int i = 0; i < 50 && !taken; i++) {
            NopSysEvent fresh = dao.getEntityById(eventId);
            taken = proc2.claim(List.of(fresh)).size() == 1;
            if (!taken)
                Thread.sleep(20);
        }
        assertTrue(taken, "node-2 must take over mid-consumption");

        // 轮询线程下一次切片续租失守 → 中止等待 → fencing 放弃完成写
        worker.join(5000);
        assertFalse(worker.isAlive(), "the polling thread must abort once the lease is lost (no unbounded wait)");

        NopSysEvent row = daoProvider.daoFor(NopSysEvent.class).getEntityById(eventId);
        assertEquals("node-2", row.getLeaseOwner(), "terminal state must belong to the takeover owner");
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED, row.getEventStatus(),
                "the deposed node must not flip the terminal state");
    }
}
