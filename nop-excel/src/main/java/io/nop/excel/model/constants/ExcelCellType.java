/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model.constants;

public enum ExcelCellType {
    /**
     * Unknown type, used to represent a state prior to initialization or the lack of a concrete type. For internal use
     * only.
     */
    _NONE(-1, ""),

    /**
     * Numeric cell type (whole numbers, fractional numbers, dates)
     */
    NUMERIC(0, "Number"),

    /**
     * String (text) cell type
     */
    STRING(1, "String"),

    /**
     * Formula cell type
     */
    FORMULA(2, "Formula"),

    /**
     * Blank cell type
     */
    BLANK(3, ""),

    /**
     * Boolean cell type
     */
    BOOLEAN(4, "Boolean"),

    /**
     * Error cell type
     */
    ERROR(5, "Error"),

    // date 类型没有在Excel中定义
    DATE(6, "Date"),

    // Object类型为xpt的扩展
    COMPLEX_OBJECT(7, "Complex");

    private final int code;
    private final String text;

    ExcelCellType(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static ExcelCellType forInt(int code) {
        for (ExcelCellType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid CellType code: " + code);
    }

    public int getCode() {
        return code;
    }
}
