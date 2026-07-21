package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 通用 key-value DTO（共享给多个 BizModel 结果返回值的简单结构化键值对）。
 */
@DataBean
public class KeyValueDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String value;

    public KeyValueDTO() {
    }

    public KeyValueDTO(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
