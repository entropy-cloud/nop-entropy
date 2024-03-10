/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml.handler;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;

import java.io.IOException;

import static io.nop.core.CoreErrors.ERR_EVAL_OUTPUT_TEXT_FAIL;

public class CollectTextHandler extends XNodeHandlerAdapter {
    private final Appendable out;

    public CollectTextHandler(Appendable out) {
        this.out = out;
    }

    @Override
    public void value(SourceLocation loc, Object value) {
        if (value == null)
            return;

        try {
            out.append(value.toString());
        } catch (IOException e) {
            throw new NopEvalException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
        }
    }

    @Override
    public void text(SourceLocation loc, String text) {
        if (text == null)
            return;

        try {
            out.append(text);
        } catch (IOException e) {
            throw new NopEvalException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
        }
    }
}