package io.nop.code.core.incremental;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 指纹清单持久化。将文件指纹列表序列化为JSON数组保存到磁盘，用于增量检测的状态持久化。
 */
public class ManifestStore {

    public void save(Path manifestFile, List<FileFingerprint> fingerprints) throws IOException {
        Path parent = manifestFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < fingerprints.size(); i++) {
            FileFingerprint fp = fingerprints.get(i);
            sb.append("  {");
            sb.append("\"filePath\":").append(escapeJson(fp.getFilePath())).append(',');
            sb.append("\"contentHash\":").append(escapeJson(fp.getContentHash())).append(',');
            sb.append("\"lastModified\":").append(fp.getLastModified()).append(',');
            sb.append("\"fileSize\":").append(fp.getFileSize());
            sb.append('}');
            if (i < fingerprints.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append(']');

        try (BufferedWriter writer = Files.newBufferedWriter(manifestFile)) {
            writer.write(sb.toString());
        }
    }

    public List<FileFingerprint> load(Path manifestFile) throws IOException {
        if (!Files.exists(manifestFile)) {
            return new ArrayList<>();
        }

        String content;
        try (BufferedReader reader = Files.newBufferedReader(manifestFile)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            content = sb.toString();
        }

        return parseJsonArray(content);
    }

    private List<FileFingerprint> parseJsonArray(String json) {
        List<FileFingerprint> result = new ArrayList<>();

        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return result;
        }

        // 去掉外层的 [ ]
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) {
            return result;
        }

        // 按 }, { 分割对象
        // 简单解析：找到每个 { ... } 块
        int i = 0;
        while (i < inner.length()) {
            int start = inner.indexOf('{', i);
            if (start < 0) break;

            int end = findMatchingBrace(inner, start);
            if (end < 0) break;

            String obj = inner.substring(start + 1, end);
            result.add(parseFingerprint(obj));
            i = end + 1;
        }

        return result;
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < s.length()) {
                    i++; // skip escaped char
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private FileFingerprint parseFingerprint(String obj) {
        FileFingerprint fp = new FileFingerprint();

        fp.setFilePath(extractStringValue(obj, "filePath"));
        fp.setContentHash(extractStringValue(obj, "contentHash"));
        fp.setLastModified(extractLongValue(obj, "lastModified"));
        fp.setFileSize(extractLongValue(obj, "fileSize"));

        return fp;
    }

    private String extractStringValue(String obj, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = obj.indexOf(pattern);
        if (start < 0) return null;

        start += pattern.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == '\\' && i + 1 < obj.length()) {
                char next = obj.charAt(i + 1);
                switch (next) {
                    case '"':
                        sb.append('"');
                        i++;
                        break;
                    case '\\':
                        sb.append('\\');
                        i++;
                        break;
                    case 'n':
                        sb.append('\n');
                        i++;
                        break;
                    case 't':
                        sb.append('\t');
                        i++;
                        break;
                    case 'r':
                        sb.append('\r');
                        i++;
                        break;
                    default:
                        sb.append(c);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private long extractLongValue(String obj, String key) {
        String pattern = "\"" + key + "\":";
        int start = obj.indexOf(pattern);
        if (start < 0) return 0L;

        start += pattern.length();
        int end = start;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) {
            end++;
        }
        if (end == start) return 0L;

        return Long.parseLong(obj.substring(start, end));
    }

    private String escapeJson(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
