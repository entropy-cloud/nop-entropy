package io.nop.rg.core.coordinator;

import java.util.List;

/**
 * 单行命中：行信息 + 行内命中列表 + 行文本（不含/含终止符两种形态，对齐 rg lines.text）。
 *
 * <p>plan 2268 Phase 2：自 {@link SearchCoordinator} 嵌套类提升为顶层（纯移动，行为不变）。
 */
public final class LineMatch {
    private final long lineNumber;
    private final long lineStart;
    private final long lineEnd;    // 含终止符
    private final long contentEnd; // 不含终止符
    private final List<Submatch> submatches;
    private final String text;     // 不含终止符
    private final String lineWithTerminator; // 含终止符（rg lines.text 形态）

    LineMatch(LineCursor.LineInfo info, List<Submatch> submatches, String content, String terminator) {
        this.lineNumber = info.lineNumber();
        this.lineStart = info.lineStart();
        this.lineEnd = info.lineEnd();
        this.contentEnd = info.contentEnd();
        this.submatches = submatches;
        this.text = content;
        this.lineWithTerminator = content + terminator;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    public long getLineStart() {
        return lineStart;
    }

    public long getLineEnd() {
        return lineEnd;
    }

    public long getContentEnd() {
        return contentEnd;
    }

    public List<Submatch> getSubmatches() {
        return submatches;
    }

    public String getText() {
        return text;
    }

    public String getLineWithTerminator() {
        return lineWithTerminator;
    }
}
