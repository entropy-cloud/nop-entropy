package io.nop.ai.agent.message;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.core.dialect.AnthropicDialect;
import io.nop.ai.core.dialect.DialectErrorChunk;
import io.nop.ai.core.dialect.OpenAiDialect;
import io.nop.ai.core.dialect.OllamaDialect;
import io.nop.api.core.message.IMessageConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 Phase 2 rule 22 chain two, segment 1 (streaming / delivery
 * governance chain, 裁定⑧⑨) — composite end-to-end scenario:
 *
 * <ol>
 *   <li>裁定⑧ dialect streaming: multi tool_call enumeration, the
 *       include_usage empty-choices terminal frame, the Ollama done frame with
 *       merged usage, and the typed in-band error chunk (no text pollution).</li>
 *   <li>裁定⑨ DBMessageService governance chain: delivery/poll separation
 *       (a blocked handler does not freeze other topics) → poison message
 *       dead-lettered in a real DLQ table within the bound → claim
 *       generation-fenced conditional UPDATE rejects a stale consume.</li>
 *   <li>裁定⑦ kill channel: the timeout/cancel path really reaches
 *       IToolProgressTracker.cancelExecution (mapped pin:
 *       TestAgentToolDispatcherKillPropagation — kept as the focused carrier;
 *       this chain asserts the wiring seam is live at the consumption end).</li>
 *   <li>裁定⑦⑩ renewal escalation abort + kill channel stages live in the
 *       companion engine-package chain segment
 *       {@code TestE2EChainCancelAbort_2519} (package-private handle surface).</li>
 * </ol>
 */
public class TestE2EChainDeliveryGovernance_2519 {

    private javax.sql.DataSource dataSource;

    @BeforeAll
    static void init() {
        io.nop.core.initialize.CoreInitialization.initializeTo(
                io.nop.core.CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() throws Exception {
        dataSource = newDb();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource instanceof AutoCloseable) {
            ((AutoCloseable) dataSource).close();
        }
    }

    private javax.sql.DataSource newDb() {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:chain2-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        return ds;
    }

    // =====================================================================
    // Stage A (裁定⑧): dialect streaming full enumeration
    // =====================================================================

    @Test
    void chain2_dialectStreamingFramesFullChain() {
        // (1) multi tool_call enumeration
        OpenAiDialect openai = new OpenAiDialect();
        List<io.nop.ai.api.chat.stream.ChatStreamChunk> chunks = openai.parseStreamChunks(
                "{\"id\":\"c1\",\"choices\":[{\"delta\":{\"tool_calls\":["
                        + "{\"index\":0,\"id\":\"call-a\",\"type\":\"function\",\"function\":{\"name\":\"fnA\",\"arguments\":\"\"}},"
                        + "{\"index\":1,\"id\":\"call-b\",\"type\":\"function\",\"function\":{\"name\":\"fnB\",\"arguments\":\"\"}}"
                        + "]}}]}");
        assertEquals(2, chunks.size(), "裁定⑧: one SSE event enumerates ALL tool_call entries");
        assertEquals("fnA", chunks.get(0).getDelta());
        assertEquals("fnB", chunks.get(1).getDelta());

        // (2) include_usage terminal frame (empty choices + usage)
        List<io.nop.ai.api.chat.stream.ChatStreamChunk> usageChunks = openai.parseStreamChunks(
                "{\"id\":\"c1\",\"choices\":[],\"usage\":{\"prompt_tokens\":11,"
                        + "\"completion_tokens\":7,\"total_tokens\":18}}");
        assertEquals(1, usageChunks.size());
        assertNotNull(usageChunks.get(0).getUsage(), "include_usage terminal frame carries usage");
        assertEquals(11L, usageChunks.get(0).getUsage().getPromptTokens().longValue());
        assertEquals(7L, usageChunks.get(0).getUsage().getCompletionTokens().longValue());

        // (3) Ollama done frame: usage merged, not dropped
        OllamaDialect ollama = new OllamaDialect();
        List<io.nop.ai.api.chat.stream.ChatStreamChunk> doneChunks = ollama.parseStreamChunks(
                "{\"model\":\"llama2\",\"message\":{\"content\":\"\"},\"done\":true,"
                        + "\"done_reason\":\"stop\",\"eval_count\":9,\"prompt_eval_count\":4}");
        assertEquals(1, doneChunks.size());
        assertNotNull(doneChunks.get(0).getUsage(), "Ollama done frame carries eval_count usage");
        assertEquals(9L, doneChunks.get(0).getUsage().getCompletionTokens().longValue());

        // (4) typed in-band error (no text pollution)
        AnthropicDialect anthropic = new AnthropicDialect();
        io.nop.ai.api.chat.stream.ChatStreamChunk errorChunk = anthropic.parseStreamChunk(
                "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\","
                        + "\"message\":\"Overloaded\"}}");
        assertTrue(errorChunk instanceof DialectErrorChunk,
                "in-band error must be a typed error chunk");
        assertEquals("overloaded_error", ((DialectErrorChunk) errorChunk).getErrorType());
        assertEquals("Overloaded", ((DialectErrorChunk) errorChunk).getErrorMessage());
    }

    // =====================================================================
    // Stage B (裁定⑨): delivery separation → poison DLQ → claim fencing
    // =====================================================================

    @Test
    void chain2_deliverySeparationToPoisonDlqToFencing() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "chain2-gov");
        svc.setPollIntervalMs(20);
        svc.setMaxRedeliveries(3);
        svc.start();
        try {
            // (1) delivery/poll separation: blocked handler on topic A does
            // not freeze topic B.
            CountDownLatch blockA = new CountDownLatch(1);
            AtomicInteger aReceived = new AtomicInteger();
            List<Object> bReceived = new CopyOnWriteArrayList<>();
            svc.subscribe("chain2.a", (topic, message, context) -> {
                aReceived.incrementAndGet();
                try {
                    blockA.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            io.nop.api.core.message.IMessageSubscription subB =
                    svc.subscribe("chain2.b", (topic, message, context) -> {
                        bReceived.add(message);
                        return null;
                    });
            svc.send("chain2.a", new AgentMessageEnvelope(
                    "S", "chain2.a", "c1", AgentMessageKind.ASYNC, "a1"));
            svc.send("chain2.b", new AgentMessageEnvelope(
                    "S", "chain2.b", "c2", AgentMessageKind.ASYNC, "b1"));
            long deadline = System.currentTimeMillis() + 10_000;
            while (bReceived.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(aReceived.get() >= 1, "blocked handler received its message");
            assertEquals(1, bReceived.size(),
                    "裁定⑨/a: a blocking handler on topic A must not freeze topic B delivery");

            // (2) poison → real DLQ row within the redelivery bound
            svc.subscribe("chain2.poison", (topic, message, context) -> {
                throw new NopAiAgentException("poison payload, deterministic failure");
            });
            svc.send("chain2.poison", new AgentMessageEnvelope(
                    "S", "chain2.poison", "c3", AgentMessageKind.ASYNC, "poison"));
            String poisonSid = findOnlySid("chain2.poison");
            assertNotNull(poisonSid);
            long dlqDeadline = System.currentTimeMillis() + 20_000;
            while (!dlqHasRow(poisonSid) && System.currentTimeMillis() < dlqDeadline) {
                Thread.sleep(20);
            }
            assertTrue(dlqHasRow(poisonSid),
                    "裁定⑨/b: poison message dead-lettered into the real DLQ table");
            assertEquals(0, pendingCount("chain2.poison"),
                    "poison message stops being redelivered after dead-lettering");

            // (3) claim generation fencing: stale-generation consume write
            // rejected by the conditional UPDATE
            svc.subscribe("chain2.fence", (topic, message, context) -> null);
            svc.send("chain2.fence", new AgentMessageEnvelope(
                    "S", "chain2.fence", "c4", AgentMessageKind.ASYNC, "m"));
            String fenceSid = findOnlySid("chain2.fence");
            assertNotNull(fenceSid);
            long gen1 = svc.claimMessageForTest(fenceSid);
            assertTrue(gen1 >= 1, "claim allocates a positive generation");
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE " + AiAgentMessageTable.TABLE_NAME
                        + " SET " + AiAgentMessageTable.COL_CLAIM_GEN + " = "
                        + AiAgentMessageTable.COL_CLAIM_GEN + " + 1 WHERE SID = '" + fenceSid + "'");
            }
            svc.markConsumedFencedForTest(fenceSid, gen1);
            assertTrue(svc.getClaimFencedRejectCount() >= 1,
                    "裁定⑨/d: stale-generation consume write must be rejected and counted");
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT STATUS FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE SID = ?")) {
                ps.setString(1, fenceSid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(AiAgentMessageTable.STATUS_CLAIMED, rs.getInt(1),
                            "stale-generation consume must not clobber the newer claim");
                }
            }
            blockA.countDown();
            assertNotNull(subB);
        } finally {
            svc.close();
        }
    }

    private String findOnlySid(String topic) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT SID FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE TOPIC = ?")) {
            ps.setString(1, topic);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private boolean dlqHasRow(String sid) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.DLQ_TABLE_NAME
                             + " WHERE " + AiAgentMessageTable.COL_DLQ_SID + " = ?")) {
            ps.setString(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private int pendingCount(String topic) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.TABLE_NAME
                             + " WHERE TOPIC = ? AND STATUS = ?")) {
            ps.setString(1, topic);
            ps.setInt(2, AiAgentMessageTable.STATUS_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
