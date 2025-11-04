package io.nop.markdown;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownSection;

public interface IMarkdownTool extends IResourceObjectLoader<MarkdownDocument> {

    MarkdownDocument parseFromVirtualPath(String path);

    MarkdownDocument parseFromResource(IResource resource);

    MarkdownDocument parseFromText(SourceLocation loc, String text);

    /**
     * 从文件同一目录的section-xxx.md文件中加载扩展信息
     */
    void loadChildSections(MarkdownSection parent, int depth);
}