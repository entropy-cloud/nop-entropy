/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

import java.io.Serializable;

/**
 * 比较时采用指针相等
 */
public final class Symbol implements Serializable {
    private final String text;

    public Symbol(String text) {
        this.text = text;
    }

    public static Symbol of(String text) {
        return new Symbol(text);
    }

    public String getText() {
        return text;
    }

    public String toString() {
        return "Symbol[" + text + "]";
    }
}