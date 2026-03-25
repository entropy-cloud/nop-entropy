package io.nop.ai.toolkit.fs;

import java.util.List;

public final class LineResult {
    private final String path;
    private final int totalLines;
    private final int fromLine;
    private final int toLine;
    private final List<Line> lines;

    public LineResult(String path, int totalLines, int fromLine, int toLine, List<Line> lines) {
        this.path = path;
        this.totalLines = totalLines;
        this.fromLine = fromLine;
        this.toLine = toLine;
        this.lines = lines;
    }

    public String getPath() {
        return path;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public int getFromLine() {
        return fromLine;
    }

    public int getToLine() {
        return toLine;
    }

    public List<Line> getLines() {
        return lines;
    }

    public String toLineNumberedContent() {
        StringBuilder sb = new StringBuilder();
        for (Line line : lines) {
            sb.append(line.getLineNumber()).append(": ").append(line.getContent()).append("\n");
        }
        return sb.toString();
    }
}
