/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml.json;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于控制XNode节点如何转换为json对象
 */
@Locale("zh-CN")
public enum XNodeJsonType {
    @Description("节点名作为key, 节点的内容作为value")
    value,

    @Description("节点名作为key, attribute作为对象属性. 不允许内容节点。子节点都是map类型")
    map,

    @Description("节点名作为key, 节点不应该具有属性，children转换为列表")
    list,

    @Description("节点名作为key。只有一个子节点")
    union;

    static final Map<String, XNodeJsonType> s_textMap = new HashMap<>();

    static {
        for (XNodeJsonType value : values()) {
            s_textMap.put(value.name(), value);
        }
    }

    public static XNodeJsonType fromText(String text) {
        XNodeJsonType type = s_textMap.get(text);
        return type;
    }
}