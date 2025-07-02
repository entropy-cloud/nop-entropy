package io.nop.ai.coder.file;

import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class FileContentParser {

    public static FileContent parseFileContent(XNode node) {
        String path = node.attrText("path");
        String content = node.contentText();
        return new FileContent(path, content);
    }

    public static FileContents parseFileContents(XNode node) {
        FileContents ret = new FileContents();
        List<FileContent> list = new ArrayList<>(node.getChildCount());
        for (XNode child : node.getChildren()) {
            list.add(parseFileContent(child));
        }
        ret.setFiles(list);
        return ret;
    }
}
