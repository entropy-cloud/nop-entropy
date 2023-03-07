/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval;

import io.nop.api.core.util.SourceLocation;

public class StringBuilderEvalOutput implements IEvalOutput {
    private final StringBuilder out;

    public StringBuilderEvalOutput(StringBuilder out) {
        this.out = out;
    }

    public StringBuilderEvalOutput() {
        this(new StringBuilder());
    }

    public String getOutput() {
        return out.toString();
    }

    @Override
    public void comment(String comment) {

    }

    @Override
    public void value(SourceLocation loc, Object text) {
        if (text != null) {
            out.append(text);
        }
    }

    @Override
    public void text(SourceLocation loc, String text) {
        if (text != null) {
            out.append(text);
        }
    }
}
