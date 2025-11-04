/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.tpl;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public class TextTemplateOutputAdapter implements ITextTemplateOutput {
    private IEvalAction beforeGen;
    private ITextTemplateOutput output;
    private IEvalAction afterGen;

    public TextTemplateOutputAdapter(IEvalAction beforeGen, ITextTemplateOutput output, IEvalAction afterGen) {
        this.beforeGen = beforeGen;
        this.output = output;
        this.afterGen = afterGen;
    }

    @Override
    public byte[] generateBytes(IEvalContext context) {
        beforeGen(context);
        byte[] ret = output.generateBytes(context);
        afterGen(context);
        return ret;
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {
        beforeGen(context);
        output.generateToWriter(out, context);
        afterGen(context);
    }

    @Override
    public String generateText(IEvalContext context) {
        beforeGen(context);
        String ret = output.generateText(context);
        afterGen(context);
        return ret;
    }

    @Override
    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        beforeGen(context);
        output.generateToStream(os, context);
        afterGen(context);
    }

    @Override
    public void generateToResource(IResource resource, IEvalContext context) {
        beforeGen(context);
        output.generateToResource(resource, context);
        afterGen(context);
    }

    protected void beforeGen(IEvalContext context) {
        if (beforeGen != null)
            beforeGen.invoke(context);
    }

    protected void afterGen(IEvalContext context) {
        if (afterGen != null)
            afterGen.invoke(context);
    }

    public IEvalAction getBeforeGen() {
        return beforeGen;
    }

    public void setBeforeGen(IEvalAction beforeGen) {
        this.beforeGen = beforeGen;
    }

    public ITextTemplateOutput getOutput() {
        return output;
    }

    public void setOutput(ITextTemplateOutput output) {
        this.output = output;
    }

    public IEvalAction getAfterGen() {
        return afterGen;
    }

    public void setAfterGen(IEvalAction afterGen) {
        this.afterGen = afterGen;
    }
}
