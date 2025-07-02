package io.nop.ai.coder.file;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;
import io.nop.markdown.simple.MarkdownSection;

import java.util.ArrayList;
import java.util.List;

@DataBean
public class FileContents {
    private List<FileContent> files;

    public List<FileContent> getFiles() {
        return files;
    }

    public void setFiles(List<FileContent> files) {
        this.files = files;
    }

    public XNode toNode() {
        XNode node = XNode.make("files");
        if (files == null || files.isEmpty())
            return node;

        for (FileContent content : files) {
            node.appendChild(content.toNode());
        }
        return node;
    }

    public MarkdownSection toMarkdown() {
        MarkdownSection section = new MarkdownSection();
        section.setLevel(1);
        section.setTitle("File Contents");

        if (files != null && !files.isEmpty()) {
            List<MarkdownSection> children = new ArrayList<>(files.size());
            for (FileContent content : files) {
                children.add(content.toMarkdown());
            }
            section.setChildren(children);
        }

        return section;
    }
}
