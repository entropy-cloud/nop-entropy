package io.nop.ai.coder.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ITextSerializable;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.utils.CodeLangMap;
import io.nop.core.lang.xml.XNode;
import io.nop.markdown.simple.MarkdownSection;

import java.util.List;

import static io.nop.ai.coder.AiCoderErrors.ERR_FILE_CONTENT_NO_PATH;

@DataBean
public class FileContent implements ITextSerializable {
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";

    public static final String TAG_FILE = "file";
    public static final String TAG_FILES = "files";

    private final String path;
    private final String description;
    private final String content;
    private final long offset;
    private final int limit;

    @JsonCreator
    public FileContent(@JsonProperty("path") String path,
                       @JsonProperty("content") String content,
                       @JsonProperty("description") String description,
                       @JsonProperty("offset") long offset,
                       @JsonProperty("limit") int limit
    ) {
        this.path = Guard.notEmpty(path, "path");
        this.description = description;
        this.content = content;
        this.offset = offset;
        this.limit = limit;
    }

    public FileContent(String path, String content, String description) {
        this(path, content, description, 0, 0);
    }

    public FileContent(String path, String content) {
        this(path, content, null);
    }


    public static FileContent fromNode(XNode node) {
        String path = node.attrText("path");
        if (StringHelper.isEmpty(path))
            throw new NopException(ERR_FILE_CONTENT_NO_PATH);
        String description = node.attrText("description");
        String content = node.contentText();
        long offset = node.attrLong("offset", 0L);
        int limit = node.attrInt("limit", 0);
        return new FileContent(path, content, description, offset, limit);
    }

    public long getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    @JsonIgnore
    public boolean isFileNotFound() {
        return FILE_NOT_FOUND.equals(getDescription());
    }

    @Override
    public String serializeToString() {
        return toNode().serializeToString();
    }

    public String getContent() {
        return content;
    }

    public String getPath() {
        return path;
    }

    public XNode toNode() {
        XNode node = XNode.make(TAG_FILE);
        node.setAttr("path", getPath());
        if (getDescription() != null)
            node.setAttr("description", getDescription());
        if (limit > 0 || offset > 0) {
            node.setAttr("offset", offset);
            if (limit > 0)
                node.setAttr("limit", limit);
        }
        node.content(new CDataText(getContent()));
        return node;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getLines() {
        return StringHelper.toStringList(StringHelper.splitToLines(getContent()));
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
            sb.append("Description: ").append(getDescription()).append("\n");
        }
        if (limit > 0 || offset > 0) {
            sb.append("Offset: ").append(offset).append(" , Limit: ").append(limit).append("\n");
        }
        sb.append("```").append(lang).append("\n").append(content).append("\n```");

        section.setText(sb.toString());
        return section;
    }
}