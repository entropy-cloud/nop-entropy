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
 * Excel数据验证类型枚举
 * 对应workbook.xdef中dataValidation元素的type属性
 */
public enum ExcelDataValidationType {
    /**
     * 无验证
     */
    NONE("none"),
    
    /**
     * 整数验证
     */
    WHOLE("whole"),
    
    /**
     * 小数验证
     */
    DECIMAL("decimal"),
    
    /**
     * 列表验证
     */
    LIST("list"),
    
    /**
     * 日期验证
     */
    DATE("date"),
    
    /**
     * 时间验证
     */
    TIME("time"),
    
    /**
     * 文本长度验证
     */
    TEXT_LENGTH("textLength"),
    
    /**
     * 自定义验证
     */
    CUSTOM("custom");

    private final String text;

    ExcelDataValidationType(String text) {
        this.text = text;
    }

    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    private static final Map<String, ExcelDataValidationType> textMap = new HashMap<String, ExcelDataValidationType>();

    static {
        for (ExcelDataValidationType value : values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static ExcelDataValidationType fromText(String text) {
        if (text == null)
            return null;

        return textMap.get(text);
    }
}