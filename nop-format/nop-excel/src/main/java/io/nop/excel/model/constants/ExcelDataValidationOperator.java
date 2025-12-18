/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * Excel数据验证运算符枚举
 * 对应workbook.xdef中dataValidation元素的operator属性
 */
public enum ExcelDataValidationOperator {
    /**
     * 在指定范围内
     */
    BETWEEN("between"),
    
    /**
     * 不在指定范围内
     */
    NOT_BETWEEN("notBetween"),
    
    /**
     * 等于指定值
     */
    EQUAL("equal"),
    
    /**
     * 不等于指定值
     */
    NOT_EQUAL("notEqual"),
    
    /**
     * 小于指定值
     */
    LESS_THAN("lessThan"),
    
    /**
     * 小于或等于指定值
     */
    LESS_THAN_OR_EQUAL("lessThanOrEqual"),
    
    /**
     * 大于指定值
     */
    GREATER_THAN("greaterThan"),
    
    /**
     * 大于或等于指定值
     */
    GREATER_THAN_OR_EQUAL("greaterThanOrEqual");

    private final String text;

    ExcelDataValidationOperator(String text) {
        this.text = text;
    }

    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    private static final Map<String, ExcelDataValidationOperator> textMap = new HashMap<String, ExcelDataValidationOperator>();

    static {
        for (ExcelDataValidationOperator value : values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static ExcelDataValidationOperator fromText(String text) {
        if (text == null)
            return null;

        return textMap.get(text);
    }
}