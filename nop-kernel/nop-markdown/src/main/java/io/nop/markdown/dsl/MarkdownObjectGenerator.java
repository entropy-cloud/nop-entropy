package io.nop.markdown.dsl;

import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;

import java.io.IOException;
import java.io.Writer;

public class MarkdownObjectGenerator implements ITextTemplateOutput {
    private final Object obj;

    public MarkdownObjectGenerator(Object obj) {
        this.obj = obj;
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {

    }
}
