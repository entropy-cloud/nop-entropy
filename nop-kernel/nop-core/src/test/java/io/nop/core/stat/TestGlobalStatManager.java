package io.nop.core.stat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGlobalStatManager {

    @AfterEach
    public void cleanup() {
        // GlobalStatManager 是 JVM 级单例，测试后清空避免污染其他测试
        GlobalStatManager.instance().clear();
    }

    @Test
    public void testCreateStatIdIsMonotonic() {
        long id1 = GlobalStatManager.createStatId();
        long id2 = GlobalStatManager.createStatId();
        assertTrue(id2 > id1, "stat id should strictly increase");
    }

    @Test
    public void testGetJdbcSqlStatIsCachedBySql() {
        GlobalStatManager mgr = GlobalStatManager.instance();
        JdbcSqlStat stat1 = mgr.getJdbcSqlStat("select * from a");
        JdbcSqlStat stat2 = mgr.getJdbcSqlStat("select * from a");
        assertSame(stat1, stat2, "same sql should map to the same stat instance");

        JdbcSqlStat stat3 = mgr.getJdbcSqlStat("select * from b");
        assertNotEquals(stat1.getId(), stat3.getId());
    }

    @Test
    public void testGetAllJdbcSqlStatSortedBySql() {
        GlobalStatManager mgr = GlobalStatManager.instance();
        JdbcSqlStat b = mgr.getJdbcSqlStat("select b");
        JdbcSqlStat a = mgr.getJdbcSqlStat("select a");
        b.incrementExecuteSuccessCount();
        a.incrementExecuteSuccessCount();

        List<JdbcSqlStatValue> values = mgr.getAllJdbcSqlStat(false);
        assertEquals(2, values.size());
        assertEquals("select a", values.get(0).getSql());
        assertEquals("select b", values.get(1).getSql());
    }

    @Test
    public void testGetAllJdbcSqlStatOrderByAvgTimeDesc() {
        GlobalStatManager mgr = GlobalStatManager.instance();
        JdbcSqlStat fast = mgr.getJdbcSqlStat("fast-sql");
        JdbcSqlStat slow = mgr.getJdbcSqlStat("slow-sql");

        fast.incrementExecuteSuccessCount();
        fast.addExecuteTime(1_000_000L); // 1ms
        slow.incrementExecuteSuccessCount();
        slow.addExecuteTime(100_000_000L); // 100ms

        List<JdbcSqlStatValue> values = mgr.getAllJdbcSqlStat(true);
        assertEquals(2, values.size());
        // 回归覆盖 wi1#4（plan 2306 项 11）：orderByAvgTime=true 必须按平均时间降序，
        // 最慢的排在最前
        assertEquals("slow-sql", values.get(0).getSql());
        assertEquals("fast-sql", values.get(1).getSql());
    }

    @Test
    public void testRpcStatsOrderByAvgTimeDesc() {
        GlobalStatManager mgr = GlobalStatManager.instance();
        RpcClientStat fastClient = mgr.getRpcClientStat("Svc", "fast");
        fastClient.incrementExecuteSuccessCount();
        fastClient.addExecuteTime(1_000_000L);
        RpcClientStat slowClient = mgr.getRpcClientStat("Svc", "slow");
        slowClient.incrementExecuteSuccessCount();
        slowClient.addExecuteTime(100_000_000L);

        assertEquals("Svc:slow", mgr.getAllRpcClientStats(true).get(0).getFullServiceName(),
                "rpc client stats must sort by avg time descending");

        RpcServerStat fastServer = mgr.getRpcServerStat("fast-op");
        fastServer.incrementExecuteSuccessCount();
        fastServer.addExecuteTime(1_000_000L);
        RpcServerStat slowServer = mgr.getRpcServerStat("slow-op");
        slowServer.incrementExecuteSuccessCount();
        slowServer.addExecuteTime(100_000_000L);

        assertEquals("slow-op", mgr.getAllRpcServerStats(true).get(0).getOperationName(),
                "rpc server stats must sort by avg time descending");
    }

    @Test
    public void testClearRemovesAllStats() {
        GlobalStatManager mgr = GlobalStatManager.instance();
        mgr.getJdbcSqlStat("to-be-cleared");
        mgr.getRpcClientStat("svc", "action");
        mgr.getRpcServerStat("op");
        assertEquals(1, mgr.getAllJdbcSqlStat(false).size());
        assertEquals(1, mgr.getAllRpcClientStats(false).size());
        assertEquals(1, mgr.getAllRpcServerStats(false).size());

        mgr.clear();
        assertEquals(0, mgr.getAllJdbcSqlStat(false).size());
        assertEquals(0, mgr.getAllRpcClientStats(false).size());
        assertEquals(0, mgr.getAllRpcServerStats(false).size());
    }

    @Test
    public void testRpcStatsCachedByServiceAndOperation() {
        GlobalStatManager mgr = GlobalStatManager.instance();
        RpcClientStat client1 = mgr.getRpcClientStat("MyService", "myAction");
        RpcClientStat client2 = mgr.getRpcClientStat("MyService", "myAction");
        assertSame(client1, client2);
        RpcClientStat other = mgr.getRpcClientStat("MyService", "otherAction");
        assertNotEquals(client1.getFullServiceName(), other.getFullServiceName());

        RpcServerStat server1 = mgr.getRpcServerStat("MyService__myAction");
        RpcServerStat server2 = mgr.getRpcServerStat("MyService__myAction");
        assertSame(server1, server2);
    }
}
