package io.nop.ai.coder.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;

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

}
