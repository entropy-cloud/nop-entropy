/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.api.source;

import io.nop.api.core.json.IJsonString;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;

import java.io.IOException;
import java.io.Writer;

public class SourceTextTemplateOutput implements ITextTemplateOutput, IJsonString, IWithSourceCode {
    private final String source;
    private final ITextTemplateOutput action;

    public SourceTextTemplateOutput(String source, ITextTemplateOutput action) {
        this.source = source;
        this.action = action;
    }

    public String getSource() {
        return source;
    }

    public String toString() {
        return source;
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {
        action.generateToWriter(out, context);
    }
}