package io.nop.rg.cli;

import io.nop.rg.core.coordinator.FileMatches;
import io.nop.rg.core.coordinator.LineMatch;

import java.io.PrintWriter;
import java.util.Map;

/**
 * 搜索结果输出（plan 2268 Phase 3：自 NopRgMain.call() 提取的输出职责，纯移动无逻辑变更）。
 *
 * <p>四种输出模式（优先级与原 if-else 链一致）：JSON（{@link JsonOutput}，rg --json 兼容）、
 * 文件名（-l）、逐文件计数（-c，格式 {@code path:count}）、逐行文本（默认，格式
 * {@code path:lineNumber:text}，恒带行号——与管道下 rg 的差异见 README）。
 */
public final class ResultPrinter {

    private ResultPrinter() {
    }

    public enum OutputMode {
        JSON, FILES_WITH_MATCHES, COUNT, TEXT
    }

    public static void print(PrintWriter out, Map<String, FileMatches> results, OutputMode mode) {
        switch (mode) {
            case JSON -> JsonOutput.writeMessages(out, results);
            case FILES_WITH_MATCHES -> {
                for (String file : results.keySet()) {
                    out.println(file);
                }
            }
            case COUNT -> {
                for (Map.Entry<String, FileMatches> entry : results.entrySet()) {
                    out.println(entry.getKey() + ":" + entry.getValue().lineCount());
                }
            }
            case TEXT -> {
                for (Map.Entry<String, FileMatches> entry : results.entrySet()) {
                    for (LineMatch line : entry.getValue().getLines()) {
                        out.println(entry.getKey() + ":" + line.getLineNumber() + ":" + line.getText());
                    }
                }
            }
        }
    }

    /**
     * 按命令行开关解析输出模式；优先级：--json &gt; -l &gt; -c &gt; 默认文本（与原 if-else 链一致）。
     */
    public static OutputMode resolveMode(boolean json, boolean filesWithMatches, boolean count) {
        if (json) {
            return OutputMode.JSON;
        }
        if (filesWithMatches) {
            return OutputMode.FILES_WITH_MATCHES;
        }
        return count ? OutputMode.COUNT : OutputMode.TEXT;
    }
}
