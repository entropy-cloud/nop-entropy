package io.nop.commons.text;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;

import java.io.IOException;
import java.util.Objects;

@DataBean
public class SourceCodeBlock implements ISourceLocationGetter {
    private final SourceLocation loc;
    private final String lang;
    private final String source;

    public SourceCodeBlock(@JsonProperty("location") SourceLocation loc,
                           @JsonProperty("lang") String lang,
                           @JsonProperty("source") String source) {
        this.loc = loc;
        this.lang = lang;
        this.source = source == null ? "" : source;
    }

    public static SourceCodeBlock build(SourceLocation loc, String lang, String source) {
        return new SourceCodeBlock(loc, lang, source);
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    public String getLang() {
        return lang;
    }

    public String getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceCodeBlock)) return false;
        SourceCodeBlock that = (SourceCodeBlock) o;
        return Objects.equals(loc, that.loc) &&
                Objects.equals(lang, that.lang) &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loc, lang, source);
    }

    public String toString() {
        return toMarkdown();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        try {
            writeAsMarkdown(sb);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }

        return sb.toString();
    }

    public void writeAsMarkdown(Appendable out) throws IOException {
        writeAsMarkdown(lang, source, out);
    }

    public static void writeAsMarkdown(String lang, String source, Appendable out) throws IOException {
        // 智能 fence 选择策略
        String fence = determineFence(source);
        String language = lang != null ? lang : "";
        out.append(fence).append(language).append("\n");
        out.append(source);
        out.append('\n').append(fence).append("\n");
    }

    /**
     * 智能确定使用哪种 fence 和反引号数量
     * 策略：检查源代码中是否包含 ```，如果包含则使用更多反引号
     */
    public static String determineFence(String source) {

        if (source == null || source.isEmpty()) {
            return "```"; // 默认使用三个反引号
        }

        // 检查源代码中是否包含 ``` 围栏
        if (source.contains("```")) {
            // 计算源代码中连续反引号的最大数量
            int maxBackticks = findMaxConsecutiveBackticks(source);

            return "`".repeat(maxBackticks + 1);
        }

        // 默认情况使用标准围栏
        return "```";
    }

    /**
     * 查找字符串中连续反引号的最大数量
     */
    private static int findMaxConsecutiveBackticks(String text) {
        int maxCount = 0;
        int currentCount = 0;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                currentCount++;
                maxCount = Math.max(maxCount, currentCount);
            } else {
                currentCount = 0;
            }
        }

        return maxCount;
    }
}
