package io.nop.ai.coder.file;

import io.nop.commons.text.CDataText;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class FileContentHelper {
    public static XNode buildFileNode(FileContent content) {
        XNode node = XNode.make("file");
        node.setAttr("path", content.getPath());
        node.content(new CDataText(content.getContent()));
        return node;
    }

    public static XNode buildFilesNode(List<FileContent> contents) {
        XNode node = XNode.make("files");
        if (contents.isEmpty())
            return node;

        for (FileContent content : contents) {
            node.appendChild(buildFileNode(content));
        }
        return node;
    }

    public static FileContent parseFileContent(XNode node) {
        String path = node.attrText("path");
        String content = node.contentText();
        return new FileContent(path, content);
    }

    public static List<FileContent> parseFileContents(XNode node) {
        List<FileContent> ret = new ArrayList<>(node.getChildCount());
        for (XNode child : node.getChildren()) {
            ret.add(parseFileContent(child));
        }
        return ret;
    }
}
