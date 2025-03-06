package io.nop.ai.core.mcp;

import io.nop.ai.core.api.support.Media;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class CallToolResult {
    private String content;
    private List<Media> media;

    public static CallToolResult fromText(String text) {
        CallToolResult ret = new CallToolResult();
        ret.setContent(text);
        return ret;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Media> getMedia() {
        return media;
    }

    public void setMedia(List<Media> media) {
        this.media = media;
    }
}
