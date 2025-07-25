package io.nop.xlang.xdef;

import java.util.HashMap;
import java.util.Map;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.StaticFactoryMethod;

@Locale("zh-CN")
public enum XDefBodyType {
    @Description("解析为列表，如果指定了xdef:key-attr，则按照key检查唯一性") list,

    @Description("最多只允许一个子节点") union,

    @Description("解析为Map，子节点名作为key") map;

    private static final Map<String, XDefBodyType> textMap = new HashMap<>();

    static {
        for (XDefBodyType value : XDefBodyType.values()) {
            textMap.put(value.name(), value);
        }
    }

    @StaticFactoryMethod
    public static XDefBodyType fromText(String text) {
        return textMap.get(text);
    }
}
