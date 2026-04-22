/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Option;
import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

@Locale("zh-CN")
public enum ObjRelationWriteMode {
    @Option("inline")
    @Label("内联保存")
    INLINE("inline"),

    @Option("link")
    @Label("仅同步关联")
    LINK("link"),

    @Option("biz")
    @Label("走目标Biz")
    BIZ("biz");

    private final String text;

    ObjRelationWriteMode(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

    private static final Map<String, ObjRelationWriteMode> MODE_MAP = new HashMap<>();

    static {
        for (ObjRelationWriteMode mode : ObjRelationWriteMode.values()) {
            MODE_MAP.put(mode.text, mode);
        }
    }

    @StaticFactoryMethod
    public static ObjRelationWriteMode fromText(String text) {
        return MODE_MAP.get(text);
    }
}
