package io.nop.datav.service.entity;

import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;
import io.nop.datav.service.chatbi.ChatBiSessionManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CL-010 会话历史加载侧 LIMIT 红⑨钉（plan 2526 裁定⑩）。
 *
 * <p>种子超界消息（2×maxTurns+1 之上）→ 有界加载恰返回最近 N 条且升序语义保持；
 * 裁剪从「全量加载后」前移到「加载集有界」。接线（chatToQuery 消费有界通道）由
 * loadRecentMessages 通道化 + 变异锚定承载（chatToQuery 需 LLM loop 不可直驱）。</p>
 */
public class TestNopDatavChatBiHistoryLimit_CL010 extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ChatBiSessionManager sessionManager;

    @Test
    public void testLoadRecentMessagesBoundedAndAscending() {
        String sessionId = seedSessionWithMessages("session-cl010", 30);

        int limit = sessionManager.recentMessageLimitForInjection();
        assertTrue(limit >= 3, "derived limit must be at least one turn pair + tolerance");

        List<NopDatavChatMessage> recent = sessionManager.loadRecentMessages(sessionId, limit);

        assertEquals(Math.min(limit, 30), recent.size(),
                "bounded load returns at most the derived limit");
        for (int i = 1; i < recent.size(); i++) {
            assertTrue(recent.get(i - 1).getSeq() < recent.get(i).getSeq(),
                    "ascending seq semantics preserved after latest-N selection");
        }
        assertEquals(30L, recent.get(recent.size() - 1).getSeq().longValue(),
                "returns the MOST RECENT messages (highest seq at tail)");
        assertEquals(30, daoMessageCount(sessionId), "full history untouched by bounded load");
    }

    @Test
    public void testLoadMessagesStaysFullForUserVisiblePath() {
        seedSessionWithMessages("session-cl010-full", 25);
        assertEquals(25, sessionManager.loadMessages("session-cl010-full").size(),
                "getChatSessionHistory user-visible path keeps full return (declared envelope)");
    }

    /**
     * 接线钉（rule 23，沿 plan 2525 MA01 源码结构钉形态）：chatToQuery 注入路径必须消费有界加载
     * 通道 loadRecentMessages（chatToQuery 需 LLM loop 不可直驱，接线由源码结构钉承载）——
     * 变异（调用点回退 loadMessages 全量）下本钉红。
     */
    @Test
    public void testChatToQueryWiresBoundedLoad() throws Exception {
        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java");
        String code = java.nio.file.Files.readString(source);
        assertTrue(code.contains("sessionManager.loadRecentMessages("),
                "chatToQuery must consume the bounded load channel (red-9 wiring)");
    }

    // ==================== Helpers ====================

    private long daoMessageCount(String sessionId) {
        var q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq("sessionId", sessionId));
        return daoProvider.daoFor(NopDatavChatMessage.class).countByQuery(q);
    }

    private String seedSessionWithMessages(String sessionId, int messageCount) {
        long now = System.currentTimeMillis();
        NopDatavChatSession session = new NopDatavChatSession();
        session.setSessionId(sessionId);
        session.setSessionTitle("cl010");
        session.setUserName("alice");
        session.setVersion(0L);
        session.setCreatedBy("alice");
        session.setCreateTime(new Timestamp(now));
        session.setUpdatedBy("alice");
        session.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavChatSession.class).saveEntityDirectly(session);

        for (int seq = 1; seq <= messageCount; seq++) {
            NopDatavChatMessage msg = new NopDatavChatMessage();
            msg.setMessageId(sessionId + "-m" + seq);
            msg.setSessionId(sessionId);
            msg.setSeq(seq);
            msg.setRole(seq % 2 == 1 ? "user" : "assistant");
            msg.setContent("message " + seq);
            msg.setResultJson(null);
            msg.setVersion(0L);
            msg.setCreatedBy("alice");
            msg.setCreateTime(new Timestamp(now + seq));
            msg.setUpdatedBy("alice");
            msg.setUpdateTime(new Timestamp(now + seq));
            daoProvider.daoFor(NopDatavChatMessage.class).saveEntityDirectly(msg);
        }
        return sessionId;
    }
}
