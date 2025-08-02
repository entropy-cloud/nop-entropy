package io.nop.ai.coder.file;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.markdown.simple.MarkdownSection;

import java.util.ArrayList;
import java.util.List;

@DataBean
public class FileContents {
    private List<FileContent> files;

    public static FileContents empty() {
        return new FileContents();
    }

    public static FileContents fromText(String text){
        XNode node = XNodeParser.instance().parseFromText(null, text);
        return fromNode(node);
    }

    public static FileContents fromNode(XNode node) {
        FileContents contents = new FileContents();
        if(node.getTagName().equals(FileContent.TAG_FILE)){
            contents.addFile(FileContent.fromNode(node));
            return contents;
        }
        for (XNode child : node.getChildren()) {
            contents.addFile(FileContent.fromNode(child));
        }
        return contents;
    }

    public List<FileContent> getFiles() {
        return files;
    }

    public void setFiles(List<FileContent> files) {
        this.files = files;
    }

    public boolean isEmpty() {
        return files == null || files.isEmpty();
    }

    public int size() {
        return files == null ? 0 : files.size();
    }

    public void addFile(FileContent file) {
        if (files == null)
            files = new ArrayList<>();
        files.add(file);
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
