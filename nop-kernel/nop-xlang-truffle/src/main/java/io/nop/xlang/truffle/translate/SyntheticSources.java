package io.nop.xlang.truffle.translate;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 合成 Source 与 SourceSection 回映射（设计 truffle 02 §三/§四）：XLang 无 parser，
 * Source 按 SourceLocation（路径 + 行号/列号）合成——内容为按行列定位的单字符占位，
 * section 的 startLine/startColumn 即回映射到的源位置。
 */
public final class SyntheticSources {

    private SyntheticSources() {
    }

    /**
     * SourceLocation → 合成 SourceSection（loc 为 null 或无 path 时返回 null，不虚构位置）。
     */
    public static SourceSection sectionOf(SourceLocation loc) {
        if (loc == null || loc.getPath() == null)
            return null;
        int line = Math.max(loc.getLine(), 1);
        int col = Math.max(loc.getCol(), 1);
        StringBuilder content = new StringBuilder(line + col);
        for (int i = 1; i < line; i++)
            content.append('\n');
        for (int i = 1; i < col; i++)
            content.append(' ');
        content.append('^');
        Source source = Source.newBuilder(XLangLanguage.ID, content, loc.getPath()).build();
        return source.createSection(content.length() - 1, 1);
    }

    /**
     * SourceSection → 回映射的 SourceLocation（对拍第三层断言的回映射口径：path + line + col）。
     */
    public static SourceLocation toSourceLocation(SourceSection section) {
        if (section == null)
            return null;
        String path = section.getSource().getPath();
        if (path == null)
            path = section.getSource().getName();
        return SourceLocation.fromLine(path, section.getStartLine(), section.getStartColumn());
    }
}
