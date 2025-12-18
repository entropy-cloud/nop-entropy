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
 * Excel数据验证输入法模式枚举
 * 对应workbook.xdef中dataValidation元素的imeMode属性
 * 主要用于东亚语言输入法控制
 */
public enum ExcelDataValidationImeMode {
    /**
     * 不控制输入法
     */
    NO_CONTROL("noControl"),
    
    /**
     * 开启输入法
     */
    ON("on"),
    
    /**
     * 关闭输入法
     */
    OFF("off"),
    
    /**
     * 禁用输入法
     */
    DISABLED("disabled"),
    
    /**
     * 平假名模式
     */
    HIRAGANA("hiragana"),
    
    /**
     * 全角片假名模式
     */
    FULL_KATAKANA("fullKatakana"),
    
    /**
     * 半角片假名模式
     */
    HALF_KATAKANA("halfKatakana"),
    
    /**
     * 全角字母模式
     */
    FULL_ALPHA("fullAlpha"),
    
    /**
     * 半角字母模式
     */
    HALF_ALPHA("halfAlpha"),
    
    /**
     * 全角韩文模式
     */
    FULL_HANGUL("fullHangul"),
    
    /**
     * 半角韩文模式
     */
    HALF_HANGUL("halfHangul");

    private final String text;

    ExcelDataValidationImeMode(String text) {
        this.text = text;
    }

    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    private static final Map<String, ExcelDataValidationImeMode> textMap = new HashMap<String, ExcelDataValidationImeMode>();

    static {
        for (ExcelDataValidationImeMode value : values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static ExcelDataValidationImeMode fromText(String text) {
        if (text == null)
            return null;

        return textMap.get(text);
    }
}