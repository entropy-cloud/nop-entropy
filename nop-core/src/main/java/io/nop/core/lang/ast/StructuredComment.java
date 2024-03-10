/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.ast;

import java.io.Serializable;
import java.util.Map;

/**
 * 类似javadoc格式的注释
 */
public class StructuredComment implements Serializable {
    private static final long serialVersionUID = -6052625825046539857L;

    private String text;
    private Map<String, String> subComments;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, String> getSubComments() {
        return subComments;
    }

    public void setSubComments(Map<String, String> subComments) {
        this.subComments = subComments;
    }
}