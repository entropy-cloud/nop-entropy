/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.common;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

public enum CodeVisibility {
    PUBLIC("public"), PROTECTED("protected"), PRIVATE("private");

    private String text;

    CodeVisibility(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @StaticFactoryMethod
    public static CodeVisibility fromText(String text) {
        if (StringHelper.isEmpty(text))
            return null;

        for (CodeVisibility value : values()) {
            if (value.getText().equals(text))
                return value;
        }
        return null;
    }
}
