package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavScreenBiz;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import io.nop.dao.txn.ITransactionTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CL-002 发布版本号行锁分配红⑤钉（plan 2526 裁定⑦）。
 *
 * <p>barrier 双线程并发 publish 同一目标 × 多轮 → 断言 snapshotVersion 无重复（版本轴唯一性——
 * 回滚/最新读取主键语义载体）。缺陷态（无行锁）两线程在 barrier 窗口内同读 max(version)+1
 * → 重复版本（压力构造红，plan 预声明形态）；修复态行锁使同目标发布串行化。</p>
 */
public class TestNopDatavPublishVersionLock_CL002 extends AbstractNopDatavTest {

    private static final int ROUNDS = 6;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavScreenBiz screenBiz;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Test
    public void testConcurrentPublishDashboardVersionsUnique() throws Exception {
        String dashboardId = seedDashboard("dash-cl002");
        IServiceContext ctx = ownerContext("alice");

        List<Long> versions = new CopyOnWriteArrayList<>();
        runConcurrent(2, () -> {
            for (int i = 0; i < ROUNDS; i++) {
                transactionTemplate.runInTransaction(txn -> {
                    dashboardBiz.publishDashboard(dashboardId, ctx);
                    return null;
                });
            }
        }, versions, dashboardId, true);

        assertVersionsUnique(versions, "dashboard");
    }

    @Test
    public void testConcurrentPublishScreenVersionsUnique() throws Exception {
        String screenId = seedScreen("screen-cl002");
        IServiceContext ctx = ownerContext("alice");

        List<Long> versions = new CopyOnWriteArrayList<>();
        runConcurrent(2, () -> {
            for (int i = 0; i < ROUNDS; i++) {
                transactionTemplate.runInTransaction(txn -> {
                    screenBiz.publishScreen(screenId, ctx);
                    return null;
                });
            }
        }, versions, screenId, false);

        assertVersionsUnique(versions, "screen");
    }

    /**
     * barrier 对齐双线程入口（publish 在事务内执行，行锁持有至事务提交）。
     */
    private void runConcurrent(int threads, Runnable work, List<Long> versions,
                               String targetId, boolean dashboard) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Throwable> workerFailures = new CopyOnWriteArrayList<>();
        List<Thread> workers = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    work.run();
                } catch (Throwable e) {
                    workerFailures.add(e);
                }
            }, "cl002-publish-" + t);
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join(60_000);
            assertTrue(!worker.isAlive(), "publish worker must finish in time");
        }
        assertTrue(workerFailures.isEmpty(), "publish workers must not fail: "
                + workerFailures.stream().map(e -> e + "/cause=" + e.getCause()).toList());

        if (dashboard) {
            List<io.nop.datav.dao.entity.NopDatavDashboardSnapshot> snapshots =
                    daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavDashboardSnapshot.class).findAll();
            for (io.nop.datav.dao.entity.NopDatavDashboardSnapshot snapshot : snapshots) {
                if (targetId.equals(snapshot.getDashboardId())) {
                    versions.add(snapshot.getSnapshotVersion());
                }
            }
        } else {
            List<NopDatavScreenSnapshot> snapshots =
                    daoProvider.daoFor(NopDatavScreenSnapshot.class).findAll();
            for (NopDatavScreenSnapshot snapshot : snapshots) {
                if (targetId.equals(snapshot.getScreenId())) {
                    versions.add(snapshot.getSnapshotVersion());
                }
            }
        }
    }

    private void assertVersionsUnique(List<Long> versions, String kind) {
        assertEquals(2 * ROUNDS, versions.size(), kind + ": all publishes must produce a snapshot");
        Set<Long> distinct = new HashSet<>(versions);
        assertEquals(versions.size(), distinct.size(),
                kind + " snapshotVersion must be unique under concurrent publish, got: " + versions);
    }

    // ==================== Helpers ====================

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private String seedDashboard(String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d.getDashboardId();
    }

    private String seedScreen(String name) {
        long now = System.currentTimeMillis();
        NopDatavScreen sc = new NopDatavScreen();
        sc.setScreenId(name + "-id");
        sc.setScreenName(name);
        sc.setDisplayName(name);
        sc.setScreenWidth(1920);
        sc.setScreenHeight(1080);
        sc.setAdaptorMode(10);
        sc.setPublishStatus(0);
        sc.setVersion(0L);
        sc.setCreatedBy("alice");
        sc.setCreateTime(new Timestamp(now));
        sc.setUpdatedBy("alice");
        sc.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavScreen.class).saveEntityDirectly(sc);
        return sc.getScreenId();
    }
}
