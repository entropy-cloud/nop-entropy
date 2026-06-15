
package io.nop.ai.service.entity;

import io.nop.ai.biz.INopAiChatResponseBiz;
import io.nop.ai.dao.dto.ModelUsageSummary;
import io.nop.ai.dao.entity.NopAiChatResponse;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.dataset.BeanRowMapper;
import io.nop.core.lang.sql.SQL;
import io.nop.dataset.IRowMapper;
import io.nop.orm.IOrmTemplate;

import java.util.List;

import static io.nop.ai.service.NopAiErrors.ARG_SESSION_ID;
import static io.nop.ai.service.NopAiErrors.ERR_AI_SESSION_ID_REQUIRED;

@BizModel("NopAiChatResponse")
public class NopAiChatResponseBizModel extends CrudBizModel<NopAiChatResponse> implements INopAiChatResponseBiz {

    /**
     * 将 SQL 聚合结果行映射为 {@link ModelUsageSummary}。列名采用 snake_case，
     * 由 {@link BeanRowMapper} 按 camelCase 规则映射到 DTO 属性（H2 等数据库返回
     * 大写列名时，{@code StringHelper.camelCase} 会先转小写再处理）。
     */
    static final IRowMapper<ModelUsageSummary> ROW_MAPPER = BeanRowMapper.of(ModelUsageSummary.class, true);

    public NopAiChatResponseBizModel() {
        setEntityName(NopAiChatResponse.class.getName());
    }

    /**
     * 按模型维度聚合一 session 内的 token 用量（design §3.4 / plan 203 L2-20 + plan 204 L2-19）。
     *
     * <p>SQL: {@code GROUP BY model_id, ai_provider, ai_model}，{@code SUM(prompt_tokens)}、
     * {@code SUM(completion_tokens)}、{@code COUNT(*)}、{@code SUM(response_duration_ms)}，
     * {@code WHERE session_id = ?}。{@code model_id} 为 null 的行按 provider+model 独立成组，
     * 不会被丢弃或合并。
     *
     * <p>{@code estimatedCost} 不再恒为 {@code null}（plan 204 / L2-19 落地）：SQL 通过
     * {@code LEFT JOIN nop_ai_model m ON r.model_id = m.id} join 定价列，计算
     * {@code SUM(prompt_tokens * input_price_per_1m / 1000000
     * + completion_tokens * output_price_per_1m / 1000000)}。返回 {@code null} 的两种 graceful
     * degradation 情形（非错误）：(1) {@code model_id} 为 null（无匹配 {@code nop_ai_model} 行可 join，
     * LEFT JOIN 补 null）；(2) {@code model_id} 非 null 但对应 {@code nop_ai_model} 行的
     * {@code input_price_per_1m} 或 {@code output_price_per_1m} 为 null（SQL 中 null 参与乘法使整组
     * SUM 结果为 null）。
     *
     * @param sessionId 会话ID，不允许为空白（null / 空 / 纯空白将抛
     *                  {@link NopException}，不静默返回空列表）
     */
    @Override
    @BizQuery
    public List<ModelUsageSummary> summarizeByModel(@Name("sessionId") String sessionId, IServiceContext context) {
        if (StringHelper.isBlank(sessionId)) {
            throw new NopException(ERR_AI_SESSION_ID_REQUIRED).param(ARG_SESSION_ID, sessionId);
        }
        return orm().findAll(buildSummarySql(sessionId), ROW_MAPPER);
    }

    /**
     * 构造 per-model 聚合 SQL。package-private 以便同模块测试复用，避免 SQL 与测试逻辑漂移。
     *
     * <p>含 {@code LEFT JOIN nop_ai_model m ON r.model_id = m.id} 定价 join（plan 204 / L2-19），
     * 计算 {@code estimated_cost}。当 {@code model_id} 为 null 或定价列为 null 时，
     * LEFT JOIN / SQL null 传播使 {@code estimated_cost} 为 null（graceful degradation）。
     */
    static SQL buildSummarySql(String sessionId) {
        return SQL.begin()
                .append("SELECT r.model_id, r.ai_provider, r.ai_model, ")
                .append("SUM(r.prompt_tokens) AS total_prompt_tokens, ")
                .append("SUM(r.completion_tokens) AS total_completion_tokens, ")
                .append("COUNT(*) AS call_count, ")
                .append("SUM(r.response_duration_ms) AS total_duration_ms, ")
                .append("SUM(r.prompt_tokens * m.input_price_per_1m / 1000000 ")
                .append("+ r.completion_tokens * m.output_price_per_1m / 1000000) AS estimated_cost ")
                .append("FROM nop_ai_chat_response r ")
                .append("LEFT JOIN nop_ai_model m ON r.model_id = m.id ")
                .append("WHERE r.session_id = ").param0(sessionId)
                .append(" GROUP BY r.model_id, r.ai_provider, r.ai_model")
                .end();
    }
}
