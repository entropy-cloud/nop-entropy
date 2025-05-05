package io.nop.markdown;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.markdown.simple.MarkdownDocument;

public interface IMarkdownTool extends IResourceObjectLoader<MarkdownDocument> {

    MarkdownDocument parseFromVirtualPath(String path);

    MarkdownDocument parseFromResource(IResource resource);

    MarkdownDocument parseFromText(SourceLocation loc, String text);

    void loadSectionExtForDocument(MarkdownDocument doc);
}