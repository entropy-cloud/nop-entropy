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
 * Excel数据验证错误样式枚举
 * 对应workbook.xdef中dataValidation元素的errorStyle属性
 */
public enum ExcelDataValidationErrorStyle {
    /**
     * 停止样式 - 阻止用户输入无效数据
     */
    STOP("stop"),
    
    /**
     * 警告样式 - 显示警告但允许用户继续
     */
    WARNING("warning"),
    
    /**
     * 信息样式 - 显示信息提示
     */
    INFORMATION("information");

    private final String text;

    ExcelDataValidationErrorStyle(String text) {
        this.text = text;
    }

    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    private static final Map<String, ExcelDataValidationErrorStyle> textMap = new HashMap<String, ExcelDataValidationErrorStyle>();

    static {
        for (ExcelDataValidationErrorStyle value : values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static ExcelDataValidationErrorStyle fromText(String text) {
        if (text == null)
            return null;

        return textMap.get(text);
    }
}