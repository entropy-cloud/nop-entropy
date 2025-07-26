package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.markdown.IMarkdownTool;
import io.nop.markdown.MarkdownConstants;

import java.util.List;

public class DefaultMarkdownTool implements IMarkdownTool {

    @Override
    public void loadChildSections(MarkdownSection parent, int depth) {
        if (depth <= 0)
            return;

        String path = parent.resourceStdPath();
        if (path == null || !path.endsWith(".md"))
            return;

        String dir;
        if (path.endsWith("/index.md")) {
            dir = StringHelper.filePath(path);
        } else {
            // 去除.md后缀作为目录名
            dir = path.substring(0, path.length() - 3);
        }

        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(dir);
        if (children == null || children.isEmpty())
            return;

        for (IResource child : children) {
            String fileName = child.getName();
            if (!fileName.startsWith(MarkdownConstants.SECTION_PREFIX))
                continue;

            if (child.isDirectory()) {
                String indexPath = child.getStdPath() + "/index.md";
                String sectionNo = fileName.substring(MarkdownConstants.SECTION_PREFIX.length());
                MarkdownDocument sectionDoc = parseFromResource(VirtualFileSystem.instance().getResource(indexPath));
                if (sectionDoc.getRootSection() != null) {
                    MarkdownSection section = sectionDoc.getRootSection();
                    section.setSectionNo(sectionNo);
                    section.setLevel(parent.getLevel() + 1);
                    loadChildSections(section, depth - 1);
                    parent.mergeChild(section);
                }
            } else if (fileName.endsWith(".md")) {
                String sectionNo = fileName.substring(MarkdownConstants.SECTION_PREFIX.length(), fileName.length() - 3);
                MarkdownDocument sectionDoc = parseFromResource(child);
                if (sectionDoc.getRootSection() != null) {
                    MarkdownSection section = sectionDoc.getRootSection();
                    section.setSectionNo(sectionNo);
                    section.setLevel(parent.getLevel() + 1);
                    parent.mergeChild(section);
                }
            }
        }
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