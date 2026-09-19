package io.nop.rg.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * 基准 corpus 生成器（固定随机种子，逐字节可再生——迭代循环跨轮对比要求 corpus 完全一致）。
 * corpus 为真实文本（非稀疏/全零——两侧二进制嗅探都会跳过全零文件）。
 */
public final class CorpusUtil {

    private CorpusUtil() {
    }

    /**
     * 生成固定种子的伪随机文本行数据。
     */
    public static byte[] textBytes(long sizeBytes, long seed) {
        Random random = new Random(seed);
        StringBuilder line = new StringBuilder();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int) Math.min(sizeBytes, 1 << 22));
        String[] words = {"needle", "alpha", "beta", "gamma", "delta", "value", "index", "node", "path", "data"};
        long written = 0;
        int lineNo = 0;
        while (written < sizeBytes) {
            line.setLength(0);
            line.append("line ").append(lineNo++).append(':');
            int wordCount = 3 + random.nextInt(6);
            for (int i = 0; i < wordCount && written + line.length() + 1 < sizeBytes; i++) {
                line.append(' ').append(words[random.nextInt(words.length)]);
            }
            // 约 1/6 的行包含搜索目标（保证有命中，同时密度不至于全行命中）
            if (random.nextInt(6) == 0) {
                line.append(" needle");
            }
            line.append('\n');
            byte[] bytes = line.toString().getBytes(StandardCharsets.UTF_8);
            if (written + bytes.length > sizeBytes) {
                break;
            }
            out.writeBytes(bytes);
            written += bytes.length;
        }
        return out.toByteArray();
    }

    /**
     * 生成 corpus 文件（已存在且尺寸一致时直接复用）。
     */
    public static Path ensureFile(Path dir, String name, long sizeBytes, long seed) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(name);
            if (Files.exists(file) && Files.size(file) == sizeBytes) {
                return file;
            }
            // 分块写入，避免大尺寸下单次内存占用
            Random random = new Random(seed ^ 0x5eed);
            try (var out = Files.newOutputStream(file)) {
                long written = 0;
                long lineNo = 0;
                StringBuilder line = new StringBuilder();
                String[] words = {"needle", "alpha", "beta", "gamma", "delta", "value", "index", "node", "path", "data"};
                while (written < sizeBytes) {
                    line.setLength(0);
                    line.append("line ").append(lineNo++).append(':');
                    int wordCount = 3 + random.nextInt(6);
                    for (int i = 0; i < wordCount && written + line.length() + 1 < sizeBytes; i++) {
                        line.append(' ').append(words[random.nextInt(words.length)]);
                    }
                    if (random.nextInt(6) == 0) {
                        line.append(" needle");
                    }
                    line.append('\n');
                    byte[] bytes = line.toString().getBytes(StandardCharsets.UTF_8);
                    if (written + bytes.length > sizeBytes) {
                        break;
                    }
                    out.write(bytes);
                    written += bytes.length;
                }
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
