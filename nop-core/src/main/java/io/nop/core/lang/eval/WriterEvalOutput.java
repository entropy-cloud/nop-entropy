/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;

import java.io.IOException;
import java.io.Writer;

import static io.nop.core.CoreErrors.ERR_EVAL_OUTPUT_TEXT_FAIL;

public class WriterEvalOutput implements IEvalOutput {
    private final Writer out;

    public WriterEvalOutput(Writer out) {
        this.out = out;
    }

    @Override
    public void comment(String comment) {

    }

    public Writer getWriter() {
        return out;
    }

    @Override
    public void value(SourceLocation loc, Object text) {
        if (text != null) {
            try {
                out.write(text.toString());
            } catch (IOException e) {
                throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
            }
        }
    }

    @Override
    public void text(SourceLocation loc, String text) {
        if (text != null) {
            try {
                out.write(text);
            } catch (IOException e) {
                throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
            }
        }
    }
}
