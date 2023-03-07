/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Option;
import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

@Locale("zh-CN")
public enum XDefOverride {
    @Option("remove")
    @Description("删除基类中的节点")
    REMOVE("remove"),

    @Option("replace")
    @Description("完全覆盖原有节点")
    REPLACE("replace"),

    @Option("prepend")
    @Description("合并属性，并前插子节点")
    PREPEND("prepend"),

    @Option("append")
    @Description("合并属性，并后插子节点")
    APPEND("append"),

    @Option("merge")
    @Description("合并属性，并按照标签名合并子节点")
    MERGE("merge"),

    @Option("merge-replace")
    @Description("合并属性，并覆盖子节点。可以通过设置属性为空字符串或者null来达到删除属性的效果")
    MERGE_REPLACE("merge-replace"),

    @Option("bounded-merge")
    @Description("只保留派生节点中定义过的子节点")
    BOUNDED_MERGE("bounded-merge"),

    @Option("merge-super")
    @Description("合并属性，嵌入super")
    MERGE_SUPER("merge-super");

    private String text;

    XDefOverride(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public String toString() {
        return text;
    }

    private static final Map<String, XDefOverride> textMap = new HashMap<String, XDefOverride>();

    static {
        for (XDefOverride value : XDefOverride.values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static XDefOverride fromText(String text) {
        return textMap.get(text);
    }
}