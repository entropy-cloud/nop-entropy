package io.nop.record_mapping.md;

import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.record_mapping.model.RecordMappingConfig;

import java.io.IOException;
import java.io.Writer;

public class MappingBasedMarkdownGenerator implements ITextTemplateOutput {
    private final RecordMappingConfig mapping;
    private final Object obj;

    public MappingBasedMarkdownGenerator(RecordMappingConfig mapping, Object obj) {
        this.mapping = mapping;
        this.obj = obj;
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {

    }
}
