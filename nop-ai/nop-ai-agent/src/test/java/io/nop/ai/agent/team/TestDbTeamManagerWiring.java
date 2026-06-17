package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.tool.TeamStatusExecutor;
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
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.api.core.json.JSON;
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
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 230 Phase 2 drop-in wiring + end-to-end tests for {@link DbTeamManager}
 * (Minimum Rules #22 Anti-Hollow + #23 Wiring Verification).
 *
 * <p>Verifies the full runtime call chain:
 * <pre>
 *   DefaultAgentEngine.setTeamManager(DbTeamManager)
 *     → engine.getTeamManager() holds the SAME DbTeamManager instance
 *     → AgentToolExecuteContext.getTeamManager() exposes it to tools
 *     → TeamStatusExecutor.executeAsync() resolves the team via DB
 *     → DefaultTeamAclChecker.checkAccess() resolves the role via DB
 * </pre>
 *
 * <p>Cross-instance E2E: two engines each with their own {@link DbTeamManager}
 * pointing at the SAME shared H2 DB — engine A creates a team + binds a
 * session, then engine B's {@code team-status} tool observes the team,
 * members, and task count created by engine A (proving cross-process sharing
 * through the engine → tool → context → DbTeamManager → DB chain).
 *
 * <p>See plan 230 (L4-team-db-persistence) Phase 2.
 */
public class TestDbTeamManagerWiring {

    private DataSource sharedDataSource;

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
        // A single shared H2 in-memory DB backs both engines' DbTeamManager
        // instances — simulating one DB shared by two processes.
        String dbUrl = "jdbc:h2:mem:test-team-wiring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        sharedDataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (sharedDataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) sharedDataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    // ========================================================================
    // Wiring Verification (#23): engine holds the SAME DbTeamManager instance
    // ========================================================================

    @Test
    void engineHoldsInjectedDbTeamManagerInstance() {
        DefaultAgentEngine engine = newEngine();
        DbTeamManager dbMgr = new DbTeamManager(sharedDataSource);
        engine.setTeamManager(dbMgr);

        // The exact instance injected via setTeamManager must be the one the
        // engine exposes — proving drop-in wiring (design 裁定 7).
        assertSame(dbMgr, engine.getTeamManager(),
                "DefaultAgentEngine.getTeamManager() must return the injected DbTeamManager");
    }

    @Test
    void setTeamManagerWithNullFallsBackToNoOp() {
        DefaultAgentEngine engine = newEngine();
        engine.setTeamManager(null);
        assertTrue(engine.getTeamManager() instanceof NoOpTeamManager,
                "null setTeamManager must fall back to NoOp (zero regression)");
    }

    // ========================================================================
    // Drop-in ACL: DefaultTeamAclChecker resolves role via DbTeamManager
    // ========================================================================

    @Test
    void defaultTeamAclCheckerResolvesRoleViaDbTeamManager() {
        DbTeamManager dbMgr = new DbTeamManager(sharedDataSource);
        DefaultAgentEngine engine = newEngine();
        engine.setTeamManager(dbMgr);
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(engine.getTeamManager());

        TeamSpec spec = new TeamSpec("WiringTeam", "acl drop-in", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = dbMgr.createTeam(spec);
        dbMgr.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "actor-lead");
        dbMgr.bindMemberSession(team.getTeamId(), "worker", "worker-sess", "actor-worker");

        // LEAD passes everything; MEMBER passes READ but not ADMIN.
        TeamAclDecision leadDecision = checker.checkAccess(
                team.getTeamId(), "lead-sess", "team-status", "view");
        assertTrue(leadDecision.isAllowed(),
                "LEAD must be allowed via DB-resolved role");
        assertEquals(MemberRole.LEAD, leadDecision.getResolvedRole());

        TeamAclDecision memberDenied = checker.checkAccess(
                team.getTeamId(), "worker-sess", "team-task-update", "abandon-unclaimed");
        assertFalse(memberDenied.isAllowed(),
                "MEMBER must be denied abandon-unclaimed (ADMIN-only) via DB-resolved role");
        assertEquals(MemberRole.MEMBER, memberDenied.getResolvedRole());

        // A session not bound to any member is denied with null role — proving
        // the checker reads the live DB snapshot (no caching).
        TeamAclDecision stranger = checker.checkAccess(
                team.getTeamId(), "stranger-sess", "team-status", "view");
        assertFalse(stranger.isAllowed());
    }

    // ========================================================================
    // Wiring + end-to-end: team-status tool resolves team via DbTeamManager
    // ========================================================================

    @Test
    void teamStatusExecutorResolvesTeamViaContextDbTeamManager() throws Exception {
        DefaultAgentEngine engine = newEngine();
        DbTeamManager dbMgr = new DbTeamManager(sharedDataSource);
        engine.setTeamManager(dbMgr);
        // Wire a task store too so taskCount is meaningful.
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();

        TeamSpec spec = new TeamSpec("ToolTeam", "tool e2e", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = dbMgr.createTeam(spec);
        dbMgr.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "actor-lead");
        // Add a task so taskCount > 0.
        taskStore.createTask(team.getTeamId(), "a task", null,
                Collections.emptyList(), "lead-sess");

        // Build the SAME context shape the engine builds at tool-execution time,
        // holding the DbTeamManager wired into the engine (wiring #23).
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, NoOpAgentMessenger.noOp(), "lead-sess", "test-agent",
                null, null, null, null,
                engine.getTeamManager(), taskStore,
                new DefaultTeamAclChecker(engine.getTeamManager()));

        TeamStatusExecutor executor = new TeamStatusExecutor();
        AiToolCall call = new AiToolCall();
        call.setToolName(TeamStatusExecutor.TOOL_NAME);
        call.setId(1);

        AiToolCallResult result = executor.executeAsync(call, ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "team-status must succeed when DbTeamManager is wired");
        assertNotNull(result.getOutput());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(team.getTeamId(), body.get("teamId"),
                "team-status must resolve the team via DbTeamManager (DB read)");
        assertEquals("ToolTeam", body.get("teamName"));
        assertEquals("ACTIVE", body.get("status"));
        assertEquals(2, ((List<?>) body.get("members")).size(),
                "team-status must report the members from the DB snapshot");
        assertEquals(1, body.get("taskCount"),
                "team-status must report the task count from the task store");
    }

    // ========================================================================
    // Cross-instance end-to-end (#22): engine B's team-status sees engine A's team
    // ========================================================================

    @Test
    void crossInstanceTeamStatusViaSharedDb() throws Exception {
        // Two independent engines, each with its own DbTeamManager pointing at
        // the SAME shared H2 DB (simulating two processes).
        DefaultAgentEngine engineA = newEngine();
        DbTeamManager mgrA = new DbTeamManager(sharedDataSource);
        engineA.setTeamManager(mgrA);

        DefaultAgentEngine engineB = newEngine();
        DbTeamManager mgrB = new DbTeamManager(sharedDataSource);
        engineB.setTeamManager(mgrB);

        // Engine A creates a team + binds a session (writes go to the shared DB).
        TeamSpec spec = new TeamSpec("SharedTeam", "cross-engine", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgrA.createTeam(spec);
        mgrA.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "actor-lead");

        // Engine B's team-status tool observes engine A's team — entry point
        // (setTeamManager) → exit point (team-status JSON reflecting the
        // cross-instance team).
        AgentToolExecuteContext ctxB = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engineB, NoOpAgentMessenger.noOp(), "lead-sess", "test-agent",
                null, null, null, null,
                engineB.getTeamManager(), new InMemoryTeamTaskStore(),
                new DefaultTeamAclChecker(engineB.getTeamManager()));

        TeamStatusExecutor executor = new TeamStatusExecutor();
        AiToolCall call = new AiToolCall();
        call.setToolName(TeamStatusExecutor.TOOL_NAME);
        call.setId(1);

        AiToolCallResult result = executor.executeAsync(call, ctxB)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "engine B's team-status must succeed on engine A's team (shared DB)");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(team.getTeamId(), body.get("teamId"),
                "engine B must see the team created by engine A via the shared DB");
        assertEquals("SharedTeam", body.get("teamName"));
        assertEquals("ACTIVE", body.get("status"),
                "engine B must see the ACTIVE status activated by engine A");
        assertEquals(2, ((List<?>) body.get("members")).size());

        // After engine A disbands, engine B's reverse-lookup no longer resolves
        // an ACTIVE team — the team row is DISBANDED in the shared DB.
        mgrA.disbandTeam(team.getTeamId());
        Team fromB = mgrB.getTeam(team.getTeamId()).orElseThrow();
        assertEquals(TeamStatus.DISBANDED, fromB.getStatus(),
                "disband by engine A must be visible to engine B");
    }

    // ========================================================================
    // Helpers — build a minimal DefaultAgentEngine (no real LLM needed; the
    // wiring tests never execute an agent, only read the wired teamManager).
    // ========================================================================

    private DefaultAgentEngine newEngine() {
        IChatService chat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse();
            }

            private ChatResponse buildResponse() {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        IToolManager tools = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), ""));
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
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
        return new DefaultAgentEngine(chat, tools);
    }
}
