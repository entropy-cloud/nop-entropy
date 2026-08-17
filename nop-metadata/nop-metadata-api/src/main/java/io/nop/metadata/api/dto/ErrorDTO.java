package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 通用错误 DTO（共享给多个 BizModel 结果返回值）。
 *
 * <p>对应原 {@code Map<String,Object>} 形态：{@code {code, message, detail}}。
 *
 * <p>P2-20（plan 2026-08-16-0549-2）：增补 {@code source}/{@code refType}/{@code refValue} 承接字段，
 * 使 checkpoint 执行错误条目（executor/scheduler/autoScore 三族 Map 键）可无损类型化承接——
 * {@code source} 为错误来源判别器（execution/resolution/autoScore/scheduler），{@code refType}/{@code refValue}
 * 承接解析期缺失引用（ruleId/tableId）。{@code code} 沿"错误所涉标识符"惯例（qualityRuleId/metaTableId），
 * {@code detail} 承接辅助信息（ruleName），见 {@code NopMetaQualityRuleBizModel} 既有先例。
 */
@DataBean
public class ErrorDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private String message;
    private String detail;
    /** 错误来源判别器（P2-20）：execution（规则执行异常）/ resolution（引用解析失败）/ autoScore（评分失败）/ scheduler（调度入口失败）。 */
    private String source;
    /** 解析期缺失引用类型（P2-20）：ruleId / tableId（仅 source=resolution 条目使用）。 */
    private String refType;
    /** 解析期缺失引用值（P2-20）：缺失的 ruleId/tableId 实际值（仅 source=resolution 条目使用）。 */
    private String refValue;

    public ErrorDTO() {
    }

    public ErrorDTO(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ErrorDTO(String code, String message, String detail) {
        this.code = code;
        this.message = message;
        this.detail = detail;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRefValue() {
        return refValue;
    }

    public void setRefValue(String refValue) {
        this.refValue = refValue;
    }
}
