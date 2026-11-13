package io.nop.ai.core.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 3.1-MA-20（plan 2555 裁定 H / R1-M3 修订）：模块内新建的有界读取 helper。
 * nop-commons FileHelper 仅提供全量 readText/readLines，无有界读取原语，
 * 故在 nop-ai-core 模块内落一个最小实现，供 LocalFileOperator 的读取面
 * （readText offset/limit、分页 readLines 行数上界、grepFiles 每文件读取上界）使用。
 *
 * <p>offset/limit 语义与既有 readFileContent 契约一致：offset 为解码后字符偏移，
 * limit 为返回字符数上界。所有读取面均有界，禁止整文件无界载入。</p>
 */
final class BoundedFileReader {

    private BoundedFileReader() {
    }

    /**
     * 读取 {@code [offset, offset + limit)} 字符区间；limit<=0 表示从 offset 读到文件尾。
     * 只触碰有界区间内的解码输入，不做整文件装载。
     */
    static String readText(File file, long offset, int limit) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            if (offset > 0) {
                long skipped = reader.skip(offset);
                if (skipped < offset)
                    return "";
            }
            if (limit <= 0)
                return readRest(reader, Integer.MAX_VALUE);
            return readRest(reader, limit);
        }
    }

    /**
     * 从 startLine 行（0 基）开始流式读取至多 maxLines 行；maxLines<=0 表示不设行数上界
     * （读到底）。逐行流式，不预先装载全文件。
     */
    static List<String> readLines(File file, int startLine, int maxLines) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            for (int i = 0; i < startLine; i++) {
                if (reader.readLine() == null)
                    return result;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
                if (maxLines > 0 && result.size() >= maxLines)
                    break;
            }
        }
        return result;
    }

    private static String readRest(InputStreamReader reader, int maxChars) throws IOException {
        StringBuilder sb = new StringBuilder(Math.min(maxChars, 8 * 1024));
        char[] buf = new char[4096];
        int remaining = maxChars;
        while (remaining > 0) {
            int toRead = Math.min(buf.length, remaining);
            int n = reader.read(buf, 0, toRead);
            if (n < 0)
                break;
            sb.append(buf, 0, n);
            remaining -= n;
        }
        return sb.toString();
    }
}
