package io.nop.ai.core.file;

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

import static io.nop.ai.core.AiCoreErrors.ERR_AI_FILE_CONTENT_NO_PATH;

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
    private final boolean hasMoreData;

    @JsonCreator
    public FileContent(@JsonProperty("path") String path,
                       @JsonProperty("content") String content,
                       @JsonProperty("description") String description,
                       @JsonProperty("offset") long offset,
                       @JsonProperty("limit") int limit,
                       @JsonProperty("hasMoreData") boolean hasMoreData
    ) {
        this.path = Guard.notEmpty(path, "path");
        this.description = description;
        this.content = content == null ? "" : content;
        this.offset = offset;
        this.limit = limit;
        this.hasMoreData = hasMoreData;
    }

    public FileContent(String path, String content, String description) {
        this(path, content, description, 0, 0, false);
    }

    public FileContent(String path, String content) {
        this(path, content, null);
    }


    public static FileContent fromNode(XNode node) {
        String path = node.attrText("path");
        if (StringHelper.isEmpty(path))
            throw new NopException(ERR_AI_FILE_CONTENT_NO_PATH);
        String description = node.attrText("description");
        String content = node.contentText();
        long offset = node.attrLong("offset", 0L);
        int limit = node.attrInt("limit", 0);
        boolean hasMoreData = node.attrBoolean("hasMoreData", false);
        return new FileContent(path, content, description, offset, limit, hasMoreData);
    }

    public FileContent withLimit(int newLimit) {
        if (newLimit <= 0)
            return this;

        // 如果当前已经有limit且新limit更大或相等，不需要改变
        if (this.limit > 0 && this.limit <= newLimit)
            return this;

        if (content.isEmpty()) {
            return new FileContent(path, content, description, offset, 0, false);
        }

        if (content.length() <= newLimit) {
            // 当前内容不超过新limit，不需要截取
            return new FileContent(path, content, description, offset, newLimit, false);
        }

        // 需要截取内容
        String truncatedContent = content.substring(0, newLimit);
        return new FileContent(path, truncatedContent, description, offset, newLimit, true);
    }

    public boolean isHasMoreData() {
        return hasMoreData;
    }

    public long getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    @JsonIgnore
    public int getLength() {
        return content.length();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * 获取内容在原始文件中的结束位置
     */
    @JsonIgnore
    public long getEndOffset() {
        return offset + getLength();
    }

    /**
     * 判断是否包含指定位置的内容
     */
    @JsonIgnore
    public boolean containsPosition(long position) {
        return position >= offset && position < getEndOffset();
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
        if (hasMoreData || limit > 0 || offset > 0)
            node.setAttr("hasMoreData", true);
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
        if (hasMoreData)
            sb.append("HasMoreData: true\n");
        sb.append("\n```").append(lang).append("\n").append(content).append("\n```");

        section.setText(sb.toString());
        return section;
    }
}