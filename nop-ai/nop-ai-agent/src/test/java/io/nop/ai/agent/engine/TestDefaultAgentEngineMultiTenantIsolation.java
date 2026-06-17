package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.PrincipalRole;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.DBSessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 232 (L4-multi-tenant-isolation) Phase 3 end-to-end test: the full
 * chain from {@code engine.execute} entry point → {@code Principal.tenantId}
 * captured in the synchronous phase → thread-local tenant context set inside
 * the {@code supplyAsync} worker lambda → {@link DBSessionStore} (built with
 * {@link ThreadLocalTenantResolver}) resolves the tenant on the worker thread
 * and injects the {@code WHERE} / writes {@code TENANT_ID}.
 *
 * <p>Minimum Rules #22 (Anti-Hollow) and #23 (Wiring): this proves the
 * tenant context actually propagates across the {@code supplyAsync} thread
 * boundary (standard {@code ThreadLocal} does not), and that the store really
 * consumes it at runtime — not just that the types exist.
 */
public class TestDefaultAgentEngineMultiTenantIsolation {

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
        String dbUrl = "jdbc:h2:mem:test-engine-mt-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    /**
     * End-to-end: tenant-A executes a session via {@code engine.execute} with
     * {@code Principal.tenantId=tenant-A}. The session is persisted with
     * {@code TENANT_ID=tenant-A}. Switching to tenant-B context, the same
     * store cannot read the session (cross-tenant invisibility + cache
     * bypass). Tenant-A context still reads it.
     */
    @Test
    void engineExecuteIsolatesSessionByTenant() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource, ThreadLocalTenantResolver.INSTANCE);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("tenant-A done"))),
                toolManagerReturning("ok"),
                store);

        String sessionId = "mt-engine-1";
        Principal principalA = new Principal(PrincipalRole.USER, null, "tenant-A");
        AgentMessageRequest request = new AgentMessageRequest(
                "test-react-agent", "hi", sessionId, null, null, principalA);

        engine.execute(request).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Anti-Hollow: the persisted row carries tenant-A's TENANT_ID — proving
        // the supplyAsync worker thread observed the tenant context.
        assertEquals("tenant-A", readTenantId(sessionId),
                "engine.execute must persist the session with Principal.tenantId");

        // Tenant-A context reads its own session.
        ThreadLocalTenantResolver.set("tenant-A");
        AgentSession seenByA = store.get(sessionId);
        assertNotNull(seenByA, "tenant-A must read its own session");
        assertEquals(AgentExecStatus.completed, seenByA.getStatus());

        // Tenant-B context cannot see tenant-A's session (cache bypass + WHERE).
        // This is the cross-tenant invisibility assertion.
        ThreadLocalTenantResolver.set("tenant-B");
        AgentSession seenByB = store.get(sessionId);
        assertNull(seenByB, "tenant-B must NOT see tenant-A's session (engine→tenant isolation)");
        assertEquals(0, store.listAllSessions().size(),
                "tenant-B listAllSessions must exclude tenant-A's sessions");

        // Null tenant context (backward compatible / single tenant) sees all.
        ThreadLocalTenantResolver.set(null);
        assertNotNull(store.get(sessionId),
                "null tenant context must see all sessions (backward compatible)");
    }

    /**
     * Two tenants each execute their own session via the engine. Each tenant
     * only sees its own session; cross-tenant reads are empty. Verifies
     * isolation holds across multiple concurrent tenants in the same shared DB.
     */
    @Test
    void twoTenantsEachSeeOnlyTheirOwnSessions() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource, ThreadLocalTenantResolver.INSTANCE);

        Principal principalA = new Principal(PrincipalRole.USER, null, "tenant-A");
        Principal principalB = new Principal(PrincipalRole.USER, null, "tenant-B");

        DefaultAgentEngine engineA = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("A"))),
                toolManagerReturning("ok"), store);
        DefaultAgentEngine engineB = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("B"))),
                toolManagerReturning("ok"), store);

        engineA.execute(new AgentMessageRequest("test-react-agent", "hi", "sess-A", null, null, principalA))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        engineB.execute(new AgentMessageRequest("test-react-agent", "hi", "sess-B", null, null, principalB))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        ThreadLocalTenantResolver.set("tenant-A");
        assertNotNull(store.get("sess-A"));
        assertNull(store.get("sess-B"), "tenant-A must not see tenant-B's session");
        assertEquals(1, store.listAllSessions().size());

        ThreadLocalTenantResolver.set("tenant-B");
        assertNull(store.get("sess-A"), "tenant-B must not see tenant-A's session");
        assertNotNull(store.get("sess-B"));
        assertEquals(1, store.listAllSessions().size());
    }

    /**
     * Wiring (Minimum Rules #23): an engine built with the default
     * {@link DBSessionStore} (no tenant resolver) and a null-Principal request
     * writes rows with a null {@code TENANT_ID} — the backward-compatible
     * path is byte-identical to the pre-tenant behaviour.
     */
    @Test
    void nullPrincipalWritesNullTenantId() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("done"))),
                toolManagerReturning("ok"), store);

        String sessionId = "mt-null-principal";
        engine.execute(new AgentMessageRequest("test-react-agent", "hi", sessionId, null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertNull(readTenantId(sessionId),
                "null Principal must write a null TENANT_ID (backward compatible)");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String readTenantId(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + AiAgentSessionTable.COL_TENANT_ID
                             + " FROM " + AiAgentSessionTable.TABLE_NAME
                             + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    static final class ScriptedChatService implements IChatService {
        final List<ChatResponse> scripted;
        final AtomicInteger idx = new AtomicInteger(0);

        ScriptedChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int i = idx.getAndIncrement();
            if (i >= scripted.size()) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("(auto-final)");
                return ChatResponse.success(msg);
            }
            return scripted.get(i);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    static IToolManager toolManagerReturning(String output) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, output));
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                return model;
            }
        };
    }

    static ChatResponse finalResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }
}
