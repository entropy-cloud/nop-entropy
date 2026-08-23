
package io.nop.ai.service.entity;

import io.nop.ai.biz.INopAiChatResponseBiz;
import io.nop.ai.dao.dto.ModelUsageSummary;
import io.nop.ai.dao.entity.NopAiChatResponse;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
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
     * <p>EQL: {@code GROUP BY modelId, aiProvider, aiModel}，{@code SUM(promptTokens)}、
     * {@code SUM(completionTokens)}、{@code COUNT(*)}、{@code SUM(responseDurationMs)}，
     * {@code WHERE sessionId = ?}。{@code modelId} 为 null 的行按 provider+model 独立成组，
     * 不会被丢弃或合并。
     *
     * <p>{@code estimatedCost} 不再恒为 {@code null}（plan 204 / L2-19 落地）：EQL 通过
     * {@code LEFT JOIN NopAiModel m ON r.modelId = m.id} join 定价列，计算
     * {@code SUM(promptTokens * inputPricePer1m / 1000000
     * + completionTokens * outputPricePer1m / 1000000)}。返回 {@code null} 的两种 graceful
     * degradation 情形（非错误）：(1) {@code modelId} 为 null（无匹配 {@code NopAiModel} 行可 join，
     * LEFT JOIN 补 null）；(2) {@code modelId} 非 null 但对应 {@code NopAiModel} 行的
     * {@code inputPricePer1m} 或 {@code outputPricePer1m} 为 null（SQL 中 null 参与乘法使整组
     * SUM 结果为 null）。
     *
     * @param sessionId 会话ID，不允许为空白（null / 空 / 纯空白将抛
     *                  {@link NopException}，不静默返回空列表）
     */
    @Override
    @BizQuery
    @Auth(permissions = "NopAiChatResponse:query")
    public List<ModelUsageSummary> summarizeByModel(@Name("sessionId") String sessionId, IServiceContext context) {
        if (StringHelper.isBlank(sessionId)) {
            throw new NopException(ERR_AI_SESSION_ID_REQUIRED).param(ARG_SESSION_ID, sessionId);
        }
        return orm().findAll(buildSummarySql(sessionId), ROW_MAPPER);
    }

    /**
     * 构造 per-model 聚合 EQL（plan 2255：实体短名 + 属性名，经 {@code orm()} EQL 编译路径执行）。
     * package-private 以便同模块测试复用，避免 EQL 与测试逻辑漂移。
     *
     * <p>含 {@code LEFT JOIN NopAiModel m ON r.modelId = m.id} 定价 join（plan 204 / L2-19），
     * 计算 {@code estimated_cost}。当 {@code modelId} 为 null 或定价列为 null 时，
     * LEFT JOIN / SQL null 传播使 {@code estimated_cost} 为 null（graceful degradation）。
     *
     * <p>投影别名保持 snake_case：{@code ROW_MAPPER} 的 {@code StringHelper.camelCase(key,'_',false)}
     * 会先整体小写，camelCase 别名或无别名投影将因属性查找大小写敏感而失败。
     *
     * <p>EQL 算术表达式必须显式括号：EQL 编译器对 {@code + - * /} 的结合优先级与标准 SQL 不同
     * （{@code a*b/c + d*e/c} 会被编译为 {@code ((a*b)/(c+d*e))/c}），混合运算不括号会静默改变语义。
     */
    static SQL buildSummarySql(String sessionId) {
        return SQL.begin()
                .append("select r.modelId as model_id, r.aiProvider as ai_provider, r.aiModel as ai_model, ")
                .append("sum(r.promptTokens) as total_prompt_tokens, ")
                .append("sum(r.completionTokens) as total_completion_tokens, ")
                .append("count(*) as call_count, ")
                .append("sum(r.responseDurationMs) as total_duration_ms, ")
                .append("sum(((r.promptTokens * m.inputPricePer1m) / 1000000) ")
                .append("+ ((r.completionTokens * m.outputPricePer1m) / 1000000)) as estimated_cost ")
                .append("from NopAiChatResponse r ")
                .append("left join NopAiModel m on r.modelId = m.id ")
                .append("where r.sessionId = ").param0(sessionId)
                .append(" group by r.modelId, r.aiProvider, r.aiModel")
                .end();
    }
}
