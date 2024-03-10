/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.model;

import io.nop.api.core.annotations.core.Option;
import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * @author canonical_entropy@163.com
 */
public enum WfTransitionToType {
    @Option("to-assigned")
    TO_ASSIGNED("to-assigned"),

    @Option("to-empty")
    TO_EMPTY("to-empty"),

    @Option("to-end")
    TO_END("to-end"),

    @Option("to-step")
    TO_STEP("to-step");

    private final String text;

    WfTransitionToType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    static Map<String, WfTransitionToType> textMap = new HashMap<>();

    static {
        for (WfTransitionToType value : values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static WfTransitionToType fromText(String text) {
        return textMap.get(text);
    }
}