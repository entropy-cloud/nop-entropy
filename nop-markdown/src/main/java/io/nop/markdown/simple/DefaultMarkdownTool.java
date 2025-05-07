package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.markdown.IMarkdownTool;

import java.util.List;

public class DefaultMarkdownTool implements IMarkdownTool {
    @Override
    public MarkdownDocumentExt loadDocumentExt(MarkdownDocument doc) {
        String path = doc.resourcePath();
        if (path == null)
            return null;

        IResource resource = ResourceHelper.resolveRelativePathResource(path);
        IResource sectionsDir = ResourceHelper.getSibling(resource, "sections");

        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(sectionsDir.getStdPath());
        if (children == null)
            return new MarkdownDocumentExt();

        MarkdownDocumentExt ext = new MarkdownDocumentExt();
        for (IResource child : children) {
            String fileName = child.getName();
            if (fileName.endsWith(".md")) {
                String sectionNo = StringHelper.fileNameNoExt(fileName);
                if (StringHelper.isNumberedPrefix(sectionNo)) {
                    MarkdownDocument sectionDoc = parseFromResource(child);
                    ext.addSection(sectionNo, sectionDoc.getRootSection());
                }
            }
        }
        return ext;
    }

    @Override
    public MarkdownDocument parseFromVirtualPath(String path) {
        return new MarkdownDocumentParser().parseFromVirtualPath(path);
    }

    @Override
    public MarkdownDocument parseFromResource(IResource resource) {
        return new MarkdownDocumentParser().parseFromResource(resource);
    }

    @Override
    public MarkdownDocument loadObjectFromPath(String path) {
        return parseFromVirtualPath(path);
    }

    @Override
    public MarkdownDocument parseFromText(SourceLocation loc, String text) {
        return new MarkdownDocumentParser().parseFromText(loc, text);
    }
}