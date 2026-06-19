package io.nop.ai.agent.security;

import io.nop.ai.agent.message.AiAgentMessageTable;
import io.nop.ai.agent.message.DBMessageService;
import io.nop.ai.agent.reliability.AiAgentCheckpointTable;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.DBCheckpointManager;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.runtime.lock.DbSessionTakeoverLock;
import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.DBSessionStore;
import io.nop.ai.agent.team.AiAgentTeamTable;
import io.nop.ai.agent.team.DbTeamManager;
import io.nop.ai.agent.team.DbTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 232 (L4-multi-tenant-isolation) Phase 2 focused tests: each core DB
 * store injects the tenant {@code WHERE} when a tenant context is active, so
 * data written under one tenant is invisible to another, and null tenant
 * context keeps SQL byte-identical to the original (backward compatible).
 *
 * <p>Satisfies the Phase 2 exit criteria:
 * <ul>
 *   <li>Tenant 隔离 focused test (core stores)</li>
 *   <li>Backward compatibility focused test (null tenant context)</li>
 *   <li>接线验证 — stores actually call {@link ITenantResolver#resolveTenantId()}
 *       at runtime and inject the tenant (verified by cross-tenant reads,
 *       not by type inspection)</li>
 * </ul>
 */
public class TestMultiTenantDbIsolation {

    private DataSource dataSource;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        String dbUrl = "jdbc:h2:mem:test-mt-iso-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalTenantResolver.clear();
    }

    private void tenant(String t) {
        ThreadLocalTenantResolver.set(t);
    }

    // ========================================================================
    // DBSessionStore: WHERE injection + cache bypass + MERGE KEY
    // ========================================================================

    @Test
    void sessionStoreIsolatesByTenant() {
        DBSessionStore store = new DBSessionStore(dataSource, ThreadLocalTenantResolver.INSTANCE);

        tenant("tenant-A");
        AgentSession a = AgentSession.create("shared-sid", "agent");
        a.touch();
        store.save(a);

        // tenant-B uses the SAME sessionId — cache bypass + tenant WHERE must
        // prevent leakage (cannot see tenant-A's row).
        tenant("tenant-B");
        assertNull(store.get("shared-sid"),
                "tenant-B must not see tenant-A's session (cache bypass + WHERE)");
        assertEquals(0, store.listAllSessions().size());

        // tenant-A still sees its own row.
        tenant("tenant-A");
        AgentSession read = store.get("shared-sid");
        assertNotNull(read, "tenant-A must see its own session");
        assertEquals("agent", read.getAgentName());
        assertEquals(1, store.listAllSessions().size());
    }

    @Test
    void sessionStoreNullTenantSeesAll() {
        // Two stores sharing one DB: one built with the tenant resolver, one
        // without. The null-tenant path must see every row (legacy data).
        DBSessionStore tenantStore = new DBSessionStore(dataSource, ThreadLocalTenantResolver.INSTANCE);

        tenant("tenant-A");
        tenantStore.save(AgentSession.create("s-a", "agent"));

        // Null tenant context → no WHERE injection → all rows visible.
        tenant(null);
        DBSessionStore legacyStore = new DBSessionStore(dataSource);
        assertNotNull(legacyStore.get("s-a"),
                "null tenant context (backward compatible) must see all rows");
    }

    // ========================================================================
    // DbTeamManager / DbTeamTaskStore
    // ========================================================================

    private TeamSpec spec(String name) {
        return new TeamSpec(name, null, "lead-agent",
                List.of(new TeamMemberSpec("lead-agent", "model", MemberRole.LEAD)), 1);
    }

    @Test
    void teamManagerIsolatesByTenant() {
        DbTeamManager mgr = new DbTeamManager(dataSource, ThreadLocalTenantResolver.INSTANCE);

        tenant("tenant-A");
        Team teamA = mgr.createTeam(spec("team-A"));
        String teamAId = teamA.getTeamId();

        tenant("tenant-B");
        Team teamB = mgr.createTeam(spec("team-B"));
        String teamBId = teamB.getTeamId();

        // tenant-A sees only team-A.
        tenant("tenant-A");
        assertTrue(mgr.getTeam(teamAId).isPresent());
        assertFalse(mgr.getTeam(teamBId).isPresent(),
                "tenant-A must not see tenant-B's team");
        assertEquals(1, mgr.getActiveTeams().size());

        // tenant-B sees only team-B.
        tenant("tenant-B");
        assertFalse(mgr.getTeam(teamAId).isPresent());
        assertTrue(mgr.getTeam(teamBId).isPresent());
        assertEquals(1, mgr.getActiveTeams().size());

        // null tenant sees both (backward compatible).
        tenant(null);
        assertEquals(2, mgr.getActiveTeams().size());
    }

    @Test
    void teamTaskStoreIsolatesByTenant() {
        // Two tenants create a task each under the same team id; cross-tenant
        // reads must return empty.
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource, ThreadLocalTenantResolver.INSTANCE);

        tenant("tenant-A");
        TeamTask taskA = store.createTask("team-1", "subject-A", null, List.of(), "creator");
        String taskAId = taskA.getTaskId();

        tenant("tenant-B");
        TeamTask taskB = store.createTask("team-1", "subject-B", null, List.of(), "creator");
        String taskBId = taskB.getTaskId();

        tenant("tenant-A");
        assertTrue(store.getTask(taskAId).isPresent());
        assertFalse(store.getTask(taskBId).isPresent(), "tenant-A must not see tenant-B's task");

        tenant("tenant-B");
        assertFalse(store.getTask(taskAId).isPresent());
        assertTrue(store.getTask(taskBId).isPresent());

        tenant(null);
        assertEquals(2, store.getTasksByTeam("team-1").size());
    }

    // ========================================================================
    // DBMessageService
    // ========================================================================

    @Test
    void messageServiceIsolatesByTenant() throws Exception {
        // The message service writes TENANT_ID on send (sendAsync runs on the
        // caller's thread, so it observes the thread-local tenant). Each
        // tenant's message row carries its own TENANT_ID — verified by direct
        // SQL (Anti-Hollow). The findPending/claimMessage SQL also appends the
        // tenant WHERE when the calling thread has a tenant context.
        //
        // Note: the background delivery poller thread does NOT inherit the
        // caller's ThreadLocal tenant (standard ThreadLocal does not cross
        // threads) — per-tenant poller wiring is a deeper successor concern
        // (mirrors the engine supplyAsync treatment) and is explicitly out of
        // this foundational slice's scope. This test therefore verifies the
        // store-level INSERT/WHERE injection, not the poller delivery path.
        DBMessageService svc = new DBMessageService(dataSource, "consumer-A", ThreadLocalTenantResolver.INSTANCE);
        svc.start();
        try {
            tenant("tenant-A");
            svc.sendAsync("topic-1", "payload-A", null).toCompletableFuture().get();

            tenant("tenant-B");
            svc.sendAsync("topic-1", "payload-B", null).toCompletableFuture().get();

            // Anti-Hollow: each tenant's message row carries its own TENANT_ID.
            assertEquals(1, countMessageRows("tenant-A"),
                    "tenant-A sendAsync must persist a row with TENANT_ID=tenant-A");
            assertEquals(1, countMessageRows("tenant-B"),
                    "tenant-B sendAsync must persist a row with TENANT_ID=tenant-B");
            // No row leaked a null/mismatched tenant.
            assertEquals(0, countMessageRows(null));
        } finally {
            svc.close();
        }
    }

    private int countMessageRows(String tenant) throws Exception {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.TABLE_NAME
                             + " WHERE " + AiAgentMessageTable.COL_TENANT_ID
                             + (tenant == null ? " IS NULL" : " = ?"))) {
            if (tenant != null) {
                ps.setString(1, tenant);
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ========================================================================
    // DBDenialLedger
    // ========================================================================

    @Test
    void denialLedgerIsolatesByTenant() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 3, ThreadLocalTenantResolver.INSTANCE);

        tenant("tenant-A");
        DenialRecord recA = DenialRecord.of("s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "test deny", "test-rule", System.currentTimeMillis());
        ledger.recordDenial(recA);
        assertEquals(1, ledger.getDenialCount("s1"));

        tenant("tenant-B");
        DenialRecord recB = DenialRecord.of("s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "test deny", "test-rule", System.currentTimeMillis());
        ledger.recordDenial(recB);
        // tenant-B's count is isolated (sees only its own denial).
        assertEquals(1, ledger.getDenialCount("s1"),
                "tenant-B denial count must not include tenant-A's denial");

        tenant("tenant-A");
        assertEquals(1, ledger.getDenialCount("s1"));
    }

    /**
     * Plan 270 finding 13-12: {@code reset} must be tenant-scoped so a resume
     * under tenant-A cannot clear tenant-B's denial records for the same
     * sessionId. With the tenant context set (as resumeSession now does),
     * {@code reset}'s DELETE includes {@code AND TENANT_ID = ?}; only the
     * calling tenant's rows are removed.
     */
    @Test
    void denialLedgerResetIsolatesByTenant() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 5, ThreadLocalTenantResolver.INSTANCE);

        // Two tenants each have a denial for the SAME sessionId.
        tenant("tenant-A");
        DenialRecord recA = DenialRecord.of("shared-sid", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "deny", "rule", System.currentTimeMillis());
        ledger.recordDenial(recA);
        assertEquals(1, ledger.getDenialCount("shared-sid"));

        tenant("tenant-B");
        DenialRecord recB = DenialRecord.of("shared-sid", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "deny", "rule", System.currentTimeMillis());
        ledger.recordDenial(recB);
        assertEquals(1, ledger.getDenialCount("shared-sid"));

        // tenant-A resumes (resets) — must clear ONLY tenant-A's row.
        tenant("tenant-A");
        ledger.reset("shared-sid");
        assertEquals(0, ledger.getDenialCount("shared-sid"),
                "tenant-A reset must clear tenant-A's denial for the shared sessionId");

        // tenant-B's denial survives the tenant-A reset.
        tenant("tenant-B");
        assertEquals(1, ledger.getDenialCount("shared-sid"),
                "tenant-B's denial must NOT be cleared by tenant-A's reset (cross-tenant isolation)");
    }

    // ========================================================================
    // DBCheckpointManager
    // ========================================================================

    @Test
    void checkpointManagerIsolatesByTenant() {
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource, ThreadLocalTenantResolver.INSTANCE);

        tenant("tenant-A");
        Checkpoint cpA = Checkpoint.of("s1", "wm-A", 1, System.currentTimeMillis(),
                CheckpointType.TOOL_EXECUTION, "tool", "call-1", null, null, 1, 10L);
        mgr.saveCheckpoint(cpA);

        tenant("tenant-B");
        Checkpoint cpB = Checkpoint.of("s1", "wm-B", 1, System.currentTimeMillis(),
                CheckpointType.TOOL_EXECUTION, "tool", "call-2", null, null, 1, 10L);
        mgr.saveCheckpoint(cpB);

        tenant("tenant-A");
        assertNotNull(mgr.getCheckpoint("wm-A"), "tenant-A sees its own checkpoint");
        assertNull(mgr.getCheckpoint("wm-B"), "tenant-A must not see tenant-B's checkpoint");
        assertNotNull(mgr.getLatestCheckpoint("s1"),
                "tenant-A getLatestCheckpoint(s1) returns its own");

        tenant("tenant-B");
        assertNull(mgr.getCheckpoint("wm-A"));
        assertNotNull(mgr.getCheckpoint("wm-B"));
    }

    // ========================================================================
    // DbSessionTakeoverLock
    // ========================================================================

    @Test
    void sessionTakeoverLockIsolatesByTenant() {
        // The lock table PK is (SESSION_ID); realistic deployments use
        // distinct sessionIds per tenant (engine generates UUIDs). Tenant
        // isolation here means: a tenant cannot observe or release another
        // tenant's lease via the tenant-scoped WHERE.
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource, ThreadLocalTenantResolver.INSTANCE);

        tenant("tenant-A");
        assertTrue(lock.tryAcquire("s-A", "owner-A", 60000L),
                "tenant-A acquires a fresh lease for its own session");
        assertTrue(lock.isHeld("s-A"));

        // tenant-B cannot see tenant-A's active lease (tenant-scoped isHeld).
        tenant("tenant-B");
        assertFalse(lock.isHeld("s-A"),
                "tenant-B isHeld must not observe tenant-A's lease");

        // tenant-B cannot release tenant-A's lease (tenant-scoped DELETE).
        assertFalse(lock.release("s-A", "owner-A"),
                "tenant-B release must not delete tenant-A's lease row");

        // tenant-A's lease is untouched by tenant-B's failed release/isHeld.
        tenant("tenant-A");
        assertTrue(lock.isHeld("s-A"));
        assertTrue(lock.release("s-A", "owner-A"));
    }

    // ========================================================================
    // Backward compatibility: column presence + null-tenant SQL unchanged
    // ========================================================================

    @Test
    void nullTenantStoresBehaveIdenticallyToSingleTenant() {
        // All stores with their default (NullTenantResolver) constructors must
        // behave exactly as before — this is the zero-regression guarantee.
        DBSessionStore sessions = new DBSessionStore(dataSource);
        DbTeamManager teams = new DbTeamManager(dataSource);
        DbTeamTaskStore tasks = new DbTeamTaskStore(dataSource);

        sessions.save(AgentSession.create("s1", "agent"));
        assertNotNull(sessions.get("s1"));
        assertEquals(1, sessions.listAllSessions().size());

        Team t = teams.createTeam(spec("t1"));
        assertTrue(teams.getTeam(t.getTeamId()).isPresent());

        TeamTask task = tasks.createTask(t.getTeamId(), "subj", null, List.of(), "creator");
        assertTrue(tasks.getTask(task.getTaskId()).isPresent());
    }
}
