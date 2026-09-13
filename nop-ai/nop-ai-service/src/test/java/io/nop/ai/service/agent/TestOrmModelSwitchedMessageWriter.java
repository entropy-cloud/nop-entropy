package io.nop.ai.service.agent;

import io.nop.ai.dao.entity.NopAiSession;
import io.nop.ai.dao.entity.NopAiSessionMessage;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IDaoProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 审计 AI-2 双写修复回归：OrmModelSwitchedMessageWriter 经 IEntityDao 管道写
 * nop_ai_session_message（与 ORM schema/时间戳管道同轨），字段完整可读回。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestOrmModelSwitchedMessageWriter extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void writeModelSwitchedPersistsThroughOrmPipeline() {
        // 先建宿主 session（消息表 FK 语义上属于会话）
        IEntityDao<NopAiSession> sessionDao = daoProvider.daoFor(NopAiSession.class);
        NopAiSession session = sessionDao.newEntity();
        session.setId("sess-orm-writer-1");
        session.setProjectId("default");
        session.setAgentName("test-agent");
        session.setStatus(0);
        sessionDao.saveEntityDirectly(session);
        if (true) {
            OrmModelSwitchedMessageWriter writer = new OrmModelSwitchedMessageWriter(daoProvider);
            writer.writeModelSwitched("sess-orm-writer-1", "gpt-small", "gpt-large",
                    "complexity-route", "high", 42L);

            IEntityDao<NopAiSessionMessage> messageDao = daoProvider.daoFor(NopAiSessionMessage.class);
            NopAiSessionMessage loaded = messageDao.getEntityById(
                    messageDao.findAll().stream()
                            .filter(m -> "sess-orm-writer-1".equals(m.getSessionId()) && m.getSeq() == 42L)
                            .map(NopAiSessionMessage::getId).findFirst().orElse(null));
            assertNotNull(loaded, "message must be persisted via ORM pipeline");
            assertEquals(80, loaded.getRole());
            assertEquals("model-switched: gpt-small -> gpt-large", loaded.getContent());
            assertNotNull(loaded.getMetadata());
            org.junit.jupiter.api.Assertions.assertTrue(loaded.getMetadata().contains("\"fromModel\":\"gpt-small\""));
            assertEquals("ai-agent", loaded.getCreatedBy());
            assertNotNull(loaded.getCreateTime(), "ORM 自动时间戳通道生效");
        }
        // autotest 事务默认回滚，无需手工清理
    }
}
