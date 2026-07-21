package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 通用错误 DTO（共享给多个 BizModel 结果返回值）。
 *
 * <p>对应原 {@code Map<String,Object>} 形态：{@code {code, message, detail}}。
 */
@DataBean
public class ErrorDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private String message;
    private String detail;

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
}
