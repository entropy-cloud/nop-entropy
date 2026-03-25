package io.nop.ai.toolkit.fs;

public final class Line {
    private final int lineNumber;
    private final String content;
    private final boolean truncated;

    public Line(int lineNumber, String content, boolean truncated) {
        this.lineNumber = lineNumber;
        this.content = content;
        this.truncated = truncated;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getContent() {
        return content;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
