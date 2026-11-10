package io.nop.ai.service.agent;

import io.nop.ai.api.exceptions.NopAiException;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-C3-029（plan 2556 裁定 C）structural pin（nop-ai-service 兄弟站点）：
 * {@link OrmModelSwitchedMessageWriter} 的内部 throw 语义保持 fail-loud
 * （saveEntityDirectly 失败原样抛出，不在 writer 内吞掉）——隔离发生在调用点
 * （nop-ai-agent {@code ReActAgentExecutor.isolateDbSideEffect} 包裹每一次
 * writeModelSwitched / usageRecorder.record 调用，主流程不因审计写失败而中断）。
 * 本钉固定 writer 侧契约的另一半：异常不得被 writer 静默降级，须以
 * {@link NopAiException} 形态逃逸到调用点供隔离层捕获。
 */
public class TestOrmModelSwitchedMessageWriterIsolation {

    /** daoFor always fails — simulates a DB write failure in the ORM pipeline. */
    static final class FailingDaoProvider implements IDaoProvider {
        @Override
        public Set<String> getEntityNames() {
            return Set.of();
        }

        @Override
        public String normalizeEntityName(String entityName) {
            return entityName;
        }

        @Override
        public boolean hasDao(String entityName) {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
            throw new IllegalStateException("simulated DB failure in ORM pipeline");
        }

        @Override
        public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
            throw new IllegalStateException("simulated DB failure in ORM pipeline");
        }
    }

    @Test
    public void writerStaysFailLoudOnDbFailure_isolationLivesAtCallSite() {
        OrmModelSwitchedMessageWriter writer = new OrmModelSwitchedMessageWriter(new FailingDaoProvider());

        NopAiException ex = assertThrows(NopAiException.class,
                () -> writer.writeModelSwitched("sess-orm-isolation", "gpt-small", "gpt-large",
                        "complexity-route", "high", 1L),
                "the writer must stay fail-loud (no silent swallow inside the writer) — "
                        + "isolation happens at the nop-ai-agent ReActAgentExecutor call site "
                        + "(3.1-C3-029 裁定 C: call-site isolation is the chosen mechanism)");
        assertTrue(ex.getMessage().contains("DB write failed"),
                "the normalized failure must identify the DB write. Got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("ReActAgentExecutor.isolateDbSideEffect"),
                "the failure must point at the call-site isolation contract. Got: " + ex.getMessage());
    }

    @Test
    public void writerRejectsNullSessionIdFailLoud() {
        OrmModelSwitchedMessageWriter writer = new OrmModelSwitchedMessageWriter(new FailingDaoProvider());
        NopAiException ex = assertThrows(NopAiException.class,
                () -> writer.writeModelSwitched(null, "a", "b", null, null, 1L));
        assertTrue(ex.getMessage().contains("sessionId must not be null"));
    }
}
