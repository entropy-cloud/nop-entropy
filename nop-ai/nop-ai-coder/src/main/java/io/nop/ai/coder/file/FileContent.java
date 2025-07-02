package io.nop.ai.coder.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.markdown.simple.MarkdownSection;

@DataBean
public class FileContent {
    private final String path;
    private final String content;

    public FileContent(@JsonProperty("path") String path,
                       @JsonProperty("content") String content) {
        this.path = Guard.notEmpty(path, "path");
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public String getPath() {
        return path;
    }

    public XNode toNode() {
        XNode node = XNode.make("file");
        node.setAttr("path", getPath());
        node.content(new CDataText(getContent()));
        return node;
    }

    public MarkdownSection toMarkdown() {
        MarkdownSection section = new MarkdownSection();
        section.setLevel(2);
        section.setTitle("File: " + path);
        String lang = CodeLangMap.instance().getLanguageFromExtension(StringHelper.fileExt(path));
        if (lang == null)
            lang = "";
        section.setText("```" + lang + "\n" + content + "\n```");
        return section;
    }
}
