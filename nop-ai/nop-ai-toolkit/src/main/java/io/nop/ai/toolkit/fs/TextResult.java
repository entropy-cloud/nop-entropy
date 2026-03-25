package io.nop.ai.toolkit.fs;

public final class TextResult {
    private final String path;
    private final String content;
    private final boolean truncated;

    public TextResult(String path, String content, boolean truncated) {
        this.path = path;
        this.content = content;
        this.truncated = truncated;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
