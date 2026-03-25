package io.nop.ai.toolkit.fs;

public final class SearchMatch {
    private final String filePath;
    private final int lineNumber;
    private final String line;
    private final String matchedText;
    private final boolean truncated;

    public SearchMatch(String filePath, int lineNumber, String line,
                       String matchedText, boolean truncated) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.line = line;
        this.matchedText = matchedText;
        this.truncated = truncated;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getLine() {
        return line;
    }

    public String getMatchedText() {
        return matchedText;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String toFormattedString() {
        return filePath + ":" + lineNumber + ": " + line;
    }
}
