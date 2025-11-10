package io.nop.markdown.dsl;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.JObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.simple.MarkdownDocumentParser;

public class MarkdownObjectParser extends AbstractResourceParser<Object>
        implements ITextResourceParser<Object> {

    public MarkdownObjectParser() {

    }

    @Override
    protected Object doParseResource(IResource resource) {
        SourceLocation loc = SourceLocation.fromPath(resource.getStdPath());
        return parseFromText(loc, resource.readText());
    }

    @Override
    public Object parseFromText(SourceLocation loc, String text) {
        MarkdownDocument doc = new MarkdownDocumentParser().parseFromText(loc, text);
        return parseFromDocument(doc);
    }

    public Object parseFromDocument(MarkdownDocument doc) {
        return parseObjectFromSection(doc.getRootSection());
    }

    public JObject parseObjectFromSection(MarkdownSection section) {
        // # [title](name)
        //   - name: value
        //   - name
        // ## 1. [displayName](name)
        // ## 2.
        return null;
    }

    void parseObjectSection(MarkdownSection section, JObject obj) {

    }
}
