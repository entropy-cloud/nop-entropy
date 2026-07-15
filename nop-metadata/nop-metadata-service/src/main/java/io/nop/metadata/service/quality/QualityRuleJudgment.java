package io.nop.metadata.service.quality;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条质量规则的执行判定结果（{@link MetaQualityRuleExecutor} 产出，BizModel 据此构建 NopMetaQualityResult 行）。
 *
 * <p>架构基线 §2.7.1（D3 判定语义）：
 * <ul>
 *   <li><b>PASS</b>：规则执行成功且判定通过</li>
 *   <li><b>FAIL</b>：规则执行成功但判定不通过（如 nullCount 超过阈值）</li>
 *   <li><b>ERROR</b>：规则配置错误或执行异常（如缺 timestampColumn、custom_sql 不返回单值、SQL 执行失败）</li>
 *   <li><b>SKIP</b>：该规则在当前环境/方言下不可执行（如 regex 在不支持 REGEXP 的方言、entityType=database 首版）</li>
 * </ul>
 *
 * <p>所有路径均显式填充 actualValue/expectedValue/message/details，不静默返回空、不伪造值。
 */
public class QualityRuleJudgment {

    /** 结果状态：PASS / FAIL / ERROR / SKIP（dict meta/quality-result-status） */
    private String status;
    /** 实际值（检测 SQL 返回值；SKIP 时为 null） */
    private Double actualValue;
    /** 期望值（阈值；无阈值语义时为 null） */
    private Double expectedValue;
    /** 结果描述（失败原因 / SKIP 原因 / 通过摘要） */
    private String message;
    /** 详情 JSON 内容（ruleType/tableName/column/threshold/params 等，便于审计） */
    private final Map<String, Object> details = new LinkedHashMap<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getActualValue() {
        return actualValue;
    }

    public void setActualValue(Double actualValue) {
        this.actualValue = actualValue;
    }

    public Double getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(Double expectedValue) {
        this.expectedValue = expectedValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
