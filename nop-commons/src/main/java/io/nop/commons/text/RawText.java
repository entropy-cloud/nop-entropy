/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text;

import java.io.Serializable;

/**
 * 包装类，表示此段文本不需要进行转义
 */
public final class RawText implements Serializable {
    private static final long serialVersionUID = 5689941180001528397L;
    private final String text;

    public RawText(String text) {
        this.text = text;
    }

    public String toString() {
        return "RawText[" + text + "]";
    }

    public String getText() {
        return text;
    }
}