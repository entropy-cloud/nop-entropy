package io.nop.ai.coder.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.utils.CodeLangMap;
import io.nop.core.lang.xml.XNode;
import io.nop.markdown.simple.MarkdownSection;

@DataBean
public class FileContent {
    private final String path;
    private final String description;
    private final String content;

    public FileContent(@JsonProperty("path") String path,
                       @JsonProperty("description") String description,
                       @JsonProperty("content") String content) {
        this.path = Guard.notEmpty(path, "path");
        this.description = description;
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
        if (getDescription() != null)
            node.setAttr("description", getDescription());
        node.content(new CDataText(getContent()));
        return node;
    }

    public String getDescription() {
        return description;
    }

    public MarkdownSection toMarkdown() {
        MarkdownSection section = new MarkdownSection();
        section.setLevel(2);
        section.setTitle("File: " + path);
        String lang = CodeLangMap.instance().getLanguageFromExtension(StringHelper.fileExt(path));
        if (lang == null)
            lang = "";
        StringBuilder sb = new StringBuilder();
        if (getDescription() != null) {
            sb.append("Description: ").append(getDescription()).append("\n\n");
        }
        sb.append("```").append(lang).append("\n").append(content).append("\n```");

        section.setText(sb.toString());
        return section;
    }
}
