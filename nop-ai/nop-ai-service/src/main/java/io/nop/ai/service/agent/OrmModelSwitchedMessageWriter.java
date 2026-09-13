package io.nop.ai.service.agent;

import io.nop.ai.core.agent.IModelSwitchedMessageWriter;
import io.nop.ai.dao.entity.NopAiSessionMessage;
import io.nop.ai.api.exceptions.NopAiException;
import jakarta.inject.Inject;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IDaoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ORM 管道的 model-switched 审计消息写入器（审计 AI-2 双写修复：nop-ai-service 可依赖
 * nop-ai-dao，经 {@link IEntityDao} 写 {@code nop_ai_session_message}——与 ORM 的
 * schema 管理/逻辑删除/时间戳管道同轨）。
 * <p>
 * 生产装配（ORM 部署）应使用本实现；{@code DbModelSwitchedMessageWriter}（raw JDBC）
 * 已弃用，仅限 embedded 无 ORM 部署。
 */
public class OrmModelSwitchedMessageWriter implements IModelSwitchedMessageWriter {

    public static final int ROLE_MODEL_SWITCHED = 80;

    private static final Logger LOG = LoggerFactory.getLogger(OrmModelSwitchedMessageWriter.class);

    private final IDaoProvider daoProvider;

    @Inject
    public OrmModelSwitchedMessageWriter(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public void writeModelSwitched(String sessionId, String fromModel, String toModel,
                                   String routingReason, String complexity, long seq) {
        if (sessionId == null) {
            throw new NopAiException("OrmModelSwitchedMessageWriter.writeModelSwitched: sessionId must not be null");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fromModel", fromModel);
        metadata.put("toModel", toModel);
        metadata.put("routingReason", routingReason);
        metadata.put("complexity", complexity);

        NopAiSessionMessage message = daoProvider.daoFor(NopAiSessionMessage.class).newEntity();
        message.setId(StringHelper.generateUUID());
        message.setSessionId(sessionId);
        message.setRole(ROLE_MODEL_SWITCHED);
        message.setSeq(seq);
        message.setContent("model-switched: " + fromModel + " -> " + toModel);
        message.setMetadata(JsonTool.stringify(metadata));
        message.setVersion(0);
        message.setCreatedBy("ai-agent");
        message.setUpdatedBy("ai-agent");
        message.setCreateTime(CoreMetrics.currentTimestamp());
        message.setUpdateTime(CoreMetrics.currentTimestamp());
        daoProvider.daoFor(NopAiSessionMessage.class).saveEntityDirectly(message);

        LOG.debug("OrmModelSwitchedMessageWriter: persisted model-switched message for session '{}' "
                + "(from={}, to={}, seq={})", sessionId, fromModel, toModel, seq);
    }
}
